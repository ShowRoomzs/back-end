package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.dto.UserOrderStats;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.UserOrderItemStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 소비자 앱 마이 「주문 현황」 집계 — 단계별 주문 수({@link UserOrderStats}).
 * 항목 상태는 C10 과 같은 {@link UserOrderItemAssembler#statusOf}로 정한다 — 숫자와 주문 내역의 라벨이 어긋나지 않게.
 * 기간 제한은 없다. 네 단계에 들 수 있는 항목만 읽으니 끝난 주문이 쌓여도 읽는 양이 늘지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserOrderStatsReader {

    /** 네 단계의 원천 이행 상태 — NEW 는 「결제완료」다. 반송중 · 구매확정 · 취소는 집계 밖이다. */
    static final Set<FulfillmentStatus> COUNTED = EnumSet.of(FulfillmentStatus.NEW, FulfillmentStatus.PREPARING,
            FulfillmentStatus.SHIPPING, FulfillmentStatus.DELIVERED);

    private final OrderProductRepository orderProductRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final UserOrderClaimLoader claimLoader;
    private final UserOrderItemAssembler itemAssembler;

    public UserOrderStats read(Long userId) {
        List<OrderProduct> products = orderProductRepository.findInProgressByUserWithGroup(userId, COUNTED);
        if (products.isEmpty()) {
            return UserOrderStats.EMPTY;
        }
        Set<Long> orderIds = products.stream().map(p -> p.getOrder().getId()).collect(Collectors.toSet());
        // 취소 요청(검토 중) · 반품/교환 진행 중 오버레이 — 이 항목들은 네 단계에서 빠진다.
        UserOrderItemAssembler.Context context = UserOrderItemAssembler.Context.of(
                        cancelRequestRepository.findOpenOrRejectedByOrderIds(orderIds), Set.of(), Set.of())
                .withClaims(claimLoader.load(products));

        Map<UserOrderItemStatus, Set<Long>> ordersByStatus = new EnumMap<>(UserOrderItemStatus.class);
        for (OrderProduct product : products) {
            ordersByStatus.computeIfAbsent(itemAssembler.statusOf(product, context), k -> new HashSet<>())
                    .add(product.getOrder().getId());
        }
        return new UserOrderStats(
                count(ordersByStatus, UserOrderItemStatus.PAID),
                count(ordersByStatus, UserOrderItemStatus.PREPARING),
                count(ordersByStatus, UserOrderItemStatus.SHIPPING),
                count(ordersByStatus, UserOrderItemStatus.DELIVERED));
    }

    private static long count(Map<UserOrderItemStatus, Set<Long>> ordersByStatus, UserOrderItemStatus status) {
        return ordersByStatus.getOrDefault(status, Set.of()).size();
    }
}

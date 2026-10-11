package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 판매 관리 포트 구현(결제 계획서 7-2) — {@code order_product.group_buy_id}로 공구별 판매 실적을 읽는다.
 *
 * <p>{@link #readClosure}는 하위주문 이행 상태(34 설계서)로 판정한다 — 종결 = 구매확정·취소(환불). 종결 경로별 건수도
 * 같은 분포에서 함께 낸다 — 공구 화면이 따로 세면 주문 관리 화면과 숫자가 어긋난다(§29-11).
 * 반송중(RETURNING)은 환불 집행 완료 전까지 미종결이다. 공구 「정산 확인」 게이트가 거짓으로 열리던
 * {@code Optional.empty()} 구간이 닫혔다(34 설계서 5-3).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderGroupBuySalesReader implements GroupBuySalesReader {

    private final OrderProductRepository orderProductRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;

    @Override
    public Optional<GroupBuySales> readSales(Long groupBuyId) {
        List<Object[]> rows = orderProductRepository.sumSalesByGroupBuy(groupBuyId, OrderStatus.PAID_OR_LATER);
        int orderCount = 0;
        long amount = 0L;
        if (!rows.isEmpty() && rows.get(0) != null) {
            Object[] row = rows.get(0);
            orderCount = row[0] instanceof Number n ? n.intValue() : 0;
            amount = row[1] instanceof Number n ? n.longValue() : 0L;
        }
        List<ItemQuantity> quantities = new ArrayList<>();
        for (Object[] row : orderProductRepository.sumQuantityByProductForGroupBuy(groupBuyId, OrderStatus.PAID_OR_LATER)) {
            if (row[0] instanceof Long productId && row[1] instanceof Number quantity) {
                quantities.add(new ItemQuantity(productId, quantity.intValue()));
            }
        }
        return Optional.of(new GroupBuySales(orderCount, amount, quantities));
    }

    @Override
    public Optional<GroupBuyOrderClosure> readClosure(Long groupBuyId) {
        Map<FulfillmentStatus, Integer> byStatus = new EnumMap<>(FulfillmentStatus.class);
        for (Object[] row : deliveryGroupRepository.countByStatusForGroupBuy(groupBuyId)) {
            if (row[0] instanceof FulfillmentStatus status && row[1] instanceof Number count) {
                byStatus.put(status, count.intValue());
            }
        }
        byStatus.remove(FulfillmentStatus.PENDING); // 결제 전은 집계 밖
        int total = byStatus.values().stream().mapToInt(Integer::intValue).sum();
        int confirmed = byStatus.getOrDefault(FulfillmentStatus.CONFIRMED, 0);
        // 반송은 환불로 종결된다 — 이행 상태는 RETURNING 에 머무르므로 반송 환불이 집행된 건을 환불 종결로 옮긴다.
        int returnRefunded = Math.toIntExact(deliveryGroupRepository.countReturnRefundedForGroupBuy(groupBuyId));
        if (returnRefunded > 0) {
            byStatus.computeIfPresent(FulfillmentStatus.RETURNING, (status, count) -> count - returnRefunded);
        }
        // 결제 전(PENDING)은 위에서 뺐다 — 남은 CANCELLED 는 결제 후 취소, 곧 환불 종결이다.
        int refunded = byStatus.getOrDefault(FulfillmentStatus.CANCELLED, 0) + returnRefunded;
        int closed = confirmed + refunded;
        int awaitingShipment = byStatus.getOrDefault(FulfillmentStatus.NEW, 0)
                + byStatus.getOrDefault(FulfillmentStatus.PREPARING, 0);
        // 반품·교환 모듈 전 — 반송중만 안다. 교환은 11-claims 가 생기면 더한다.
        int inReturn = byStatus.getOrDefault(FulfillmentStatus.RETURNING, 0);
        List<UnclosedStage> stages = byStatus.entrySet().stream()
                .filter(e -> e.getKey() != FulfillmentStatus.CONFIRMED && e.getKey() != FulfillmentStatus.CANCELLED)
                .filter(e -> e.getValue() > 0)
                .map(e -> new UnclosedStage(e.getKey().name(), e.getKey().getLabel(), e.getValue()))
                .toList();
        return Optional.of(new GroupBuyOrderClosure(total, closed, total - closed, awaitingShipment, inReturn, stages,
                confirmed, refunded));
    }

    @Override
    public Optional<Long> countOrdersSince(Long groupBuyId, LocalDateTime since) {
        return Optional.of(orderProductRepository.countOrdersSince(groupBuyId, OrderStatus.PAID_OR_LATER, since));
    }

    @Override
    public Optional<Long> countOneToOneInquiriesSince(Long groupBuyId, LocalDateTime since) {
        return Optional.of(orderProductRepository.countOneToOneInquiriesSince(groupBuyId, since));
    }
}

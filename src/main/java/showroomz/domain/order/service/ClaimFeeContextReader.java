package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderClaimCollectionRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.OrderProductStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 일부 반품 재판정의 재료(1021 설계서 3-3) — 하위주문에 남아 있는 상품액과 「이미 최초 배송비를 뺐는가」.
 *
 * <p>남는 상품액 = 취소 · 전량 반품되지 않은 항목의 판매가 × (수량 − 반품된 수량 − 진행 중 클레임 수량). 부분 취소로 기준 아래가 되는 경우의
 * 재부과는 하지 않는다(시안이 반품만 정했다 · Q12).
 */
@Component
@RequiredArgsConstructor
public class ClaimFeeContextReader {

    private final OrderProductRepository orderProductRepository;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimCollectionRepository collectionRepository;

    /** 지금 남아 있는 상품액(이번 요청 전). */
    public long keptAmount(OrderDeliveryGroup group) {
        Map<Long, Long> occupied = new HashMap<>();
        for (Object[] row : claimRepository.sumOccupiedQuantityByDeliveryGroup(group.getId())) {
            occupied.put((Long) row[0], ((Number) row[1]).longValue());
        }
        long kept = 0;
        List<OrderProduct> products = orderProductRepository.findByDeliveryGroupIds(List.of(group.getId()));
        for (OrderProduct product : products) {
            if (product.getStatus() == OrderProductStatus.CANCELLED || product.getStatus() == OrderProductStatus.RETURNED) {
                continue;
            }
            long remaining = product.getQuantity() - product.getReturnedQuantity()
                    - occupied.getOrDefault(product.getId(), 0L);
            kept += (long) product.getPrice() * Math.max(0, remaining);
        }
        return kept;
    }

    public boolean alreadyDeducted(OrderDeliveryGroup group) {
        return collectionRepository.existsInitialFeeDeducted(group.getId());
    }

    public ClaimFeePolicy.ReturnContext contextFor(OrderDeliveryGroup group, long returningAmount) {
        return new ClaimFeePolicy.ReturnContext(keptAmount(group) - returningAmount, alreadyDeducted(group));
    }
}

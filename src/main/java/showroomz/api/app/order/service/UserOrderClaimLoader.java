package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 항목 행 조립용 클레임 읽기(C10 설계서 2-4 #6) — 주문 내역 목록과 주문 상세가 같이 쓴다.
 * 클레임은 배송완료 뒤에만 생기므로, 배송완료·구매확정 그룹이나 반품된 항목이 없는 페이지에서는 읽지 않는다.
 * 재발송비 청구는 거절된 진행 중 클레임이 있을 때만 한 번 더 읽는다.
 */
@Component
@RequiredArgsConstructor
public class UserOrderClaimLoader {

    private static final Set<FulfillmentStatus> CLAIM_POSSIBLE = EnumSet.of(FulfillmentStatus.DELIVERED,
            FulfillmentStatus.CONFIRMED);

    private final OrderClaimRepository claimRepository;
    private final OrderClaimChargeRepository chargeRepository;

    public UserOrderClaimContext load(Collection<OrderProduct> products) {
        Set<Long> orderIds = products.stream()
                .filter(p -> p.getStatus() == OrderProductStatus.RETURNED || (p.getDeliveryGroup() != null
                        && CLAIM_POSSIBLE.contains(p.getDeliveryGroup().getFulfillmentStatus())))
                .map(p -> p.getOrder().getId())
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return UserOrderClaimContext.EMPTY;
        }
        List<OrderClaim> claims = claimRepository.findByOrderIdsWithCollection(orderIds);
        Set<Long> rejectedCollectionIds = claims.stream()
                .filter(claim -> claim.isOpen() && claim.getRejectedAt() != null)
                .map(claim -> claim.getCollection().getId())
                .collect(Collectors.toSet());
        List<OrderClaimCharge> charges = rejectedCollectionIds.isEmpty() ? List.of()
                : chargeRepository.findByCollectionIds(rejectedCollectionIds);
        return UserOrderClaimContext.of(claims, charges);
    }
}

package showroomz.api.app.order.service;

import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimStatus;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 항목 행 조립이 읽는 클레임(C10 설계서 2-4 #6) — 그 페이지 주문들의 클레임 전부와, 반려 재발송비 청구.
 * 진행 중 표시 · 거절 종결 줄 · 환불액 · 그룹의 구매확정 보류를 이것 하나로 푼다.
 *
 * @param byProduct                주문 항목 id → 그 항목의 클레임(신청순)
 * @param rejectChargeByCollection 요청 id → 가장 최근 반려 재발송비 청구
 * @param confirmBlockingGroupIds  보류 클레임(진행 중이고 거절되지 않은 것)이 있는 하위주문 — 구매확정 예정일을 내리지 않는다
 */
public record UserOrderClaimContext(Map<Long, List<OrderClaim>> byProduct,
                                    Map<Long, OrderClaimCharge> rejectChargeByCollection,
                                    Set<Long> confirmBlockingGroupIds) {

    public static final UserOrderClaimContext EMPTY = new UserOrderClaimContext(Map.of(), Map.of(), Set.of());

    /** {@code claims}는 요청(collection)이 함께 읽혀 있어야 한다. */
    public static UserOrderClaimContext of(Collection<OrderClaim> claims, Collection<OrderClaimCharge> charges) {
        if (claims.isEmpty()) {
            return EMPTY;
        }
        Map<Long, List<OrderClaim>> byProduct = claims.stream()
                .sorted(Comparator.comparing(OrderClaim::getId))
                .collect(Collectors.groupingBy(claim -> claim.getOrderProduct().getId()));
        Map<Long, OrderClaimCharge> rejectCharges = new HashMap<>();
        for (OrderClaimCharge charge : charges) {
            if (charge.getType() == ClaimChargeType.REJECT_RESHIP) {
                rejectCharges.merge(charge.getCollection().getId(), charge, (a, b) -> a.getId() > b.getId() ? a : b);
            }
        }
        Set<Long> blocking = new HashSet<>();
        for (OrderClaim claim : claims) {
            if (claim.blocksPurchaseConfirm()) {
                blocking.add(claim.getDeliveryGroup().getId());
            }
        }
        return new UserOrderClaimContext(byProduct, rejectCharges, blocking);
    }

    public List<OrderClaim> claimsOf(Long orderProductId) {
        return byProduct.getOrDefault(orderProductId, List.of());
    }

    /** 화면에 진행으로 보이는 클레임 — 결제 대기는 아직 접수 전이다. 둘이면 가장 최근 신청 건. */
    public OrderClaim displayedOpenClaim(Long orderProductId) {
        OrderClaim latest = null;
        for (OrderClaim claim : claimsOf(orderProductId)) {
            if (claim.isOpen() && claim.getStatus() != ClaimStatus.PAYMENT_PENDING) {
                latest = claim;
            }
        }
        return latest;
    }

    public OrderClaimCharge rejectChargeOf(OrderClaim claim) {
        return rejectChargeByCollection.get(claim.getCollection().getId());
    }
}

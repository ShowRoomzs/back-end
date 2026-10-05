package showroomz.domain.order.service;

import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimType;

/**
 * 클레임 배송비(앱 클레임 설계서 1-4) — 앱이 다루는 돈은 셋이고 전부 <b>주문 시점의 편도 배송비</b>
 * ({@code order_delivery_group.base_delivery_fee})다. 마켓의 현재 설정도, {@code return_fee} · {@code exchange_fee}도
 * 읽지 않는다 — 소비자가 주문 때 본 값 하나로 묶는다. 반송 택배비는 고객이 택배사에 직접 내므로 여기에 없다.
 */
@Component
public class ClaimFeePolicy {

    /**
     * 반품 배송비 차감 — 고객 귀책 반품이고 그 하위주문이 무료배송으로 나갔을 때만 최초 배송비를 뺀다. 요청당 한 번.
     * 배송비를 내고 받은 주문은 그 배송비를 환불하지 않는 것으로 같은 효과가 난다.
     */
    public int returnDeduction(ClaimType type, ClaimFeeBearer feeBearer, OrderDeliveryGroup group) {
        if (type != ClaimType.RETURN || feeBearer != ClaimFeeBearer.CONSUMER || !group.isFreeShippingApplied()) {
            return 0;
        }
        return group.getBaseDeliveryFee();
    }

    /** 교환 재발송 배송비 — 고객 귀책 교환만. 브랜드 귀책이면 0. */
    public int exchangeReshipFee(ClaimFeeBearer feeBearer, OrderDeliveryGroup group) {
        return feeBearer == ClaimFeeBearer.CONSUMER ? group.getBaseDeliveryFee() : 0;
    }

    /** 검수 반려 상품을 다시 받는 배송비. */
    public int rejectReshipFee(OrderDeliveryGroup group) {
        return group.getBaseDeliveryFee();
    }
}

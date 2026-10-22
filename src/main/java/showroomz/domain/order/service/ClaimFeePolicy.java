package showroomz.domain.order.service;

import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimType;

/**
 * 클레임 배송비(앱 클레임 설계서 1-4 · 1021 결정 노트 기-18 변경) — 브랜드가 반품비 · 교환비를 따로 정하지 않는다. 전부 <b>주문 시점의 편도
 * 배송비</b>({@code order_delivery_group.base_delivery_fee})와 결제 시점 권역 추가비({@code region_surcharge})로 계산한다. 마켓의 현재 설정은
 * 읽지 않는다 — 소비자가 주문 때 본 값으로 묶는다(기-20). 반송 택배비는 고객이 택배사에 직접 내므로 여기에 없다(기-32).
 *
 * <ul>
 *   <li>단순 변심 반품 — <b>무료배송 주문만</b> 최초 배송비를 환불액에서 뺀다. 일부 반품이면 반품 뒤 남는 상품액이 결제 시점 무료배송 기준 아래로
 *       내려갈 때만 뺀다. 같은 하위주문에서 한 번만.</li>
 *   <li>단순 변심 교환 — 재발송 배송비(편도 + 제주 · 도서산간 추가비)를 결제한다.</li>
 *   <li>브랜드 귀책 — 착불 · 소비자 부담 없음.</li>
 * </ul>
 */
@Component
public class ClaimFeePolicy {

    /**
     * 최초 배송비 차감(1021 설계서 3-3).
     *
     * @param context 이 요청 뒤 남는 상품액 · 같은 하위주문에서 이미 차감했는가 — {@link ClaimFeeContextReader}
     */
    public int returnDeduction(ClaimType type, ClaimFeeBearer feeBearer, OrderDeliveryGroup group,
                               ReturnContext context) {
        if (type != ClaimType.RETURN || feeBearer != ClaimFeeBearer.CONSUMER || !group.isFreeShippingApplied()) {
            return 0;
        }
        if (context.alreadyDeducted()) {
            return 0;
        }
        Integer threshold = group.getFreeShippingThreshold();
        // 기준을 모르는(옛) 주문은 전처럼 뺀다 — 무료배송으로 나간 사실만으로 판단한다.
        if (threshold != null && context.keptAmountAfter() >= threshold) {
            return 0;
        }
        return group.getBaseDeliveryFee();
    }

    /**
     * 신청 화면 미리보기 — 전량 반품 기준의 최대 차감액(선택 수량에 따라 0이 될 수 있다는 판단은 {@link ReturnContext}로 한다).
     */
    public int returnDeductionForFullReturn(ClaimType type, ClaimFeeBearer feeBearer, OrderDeliveryGroup group,
                                            boolean alreadyDeducted) {
        return returnDeduction(type, feeBearer, group, new ReturnContext(0, alreadyDeducted));
    }

    /** 교환 재발송 배송비 — 고객 귀책 교환만. 편도 + 권역 추가비. 브랜드 귀책이면 0. */
    public int exchangeReshipFee(ClaimFeeBearer feeBearer, OrderDeliveryGroup group) {
        return feeBearer == ClaimFeeBearer.CONSUMER ? reshipFee(group) : 0;
    }

    /** 검수 반려 상품을 다시 받는 배송비 — 편도 + 권역 추가비. */
    public int rejectReshipFee(OrderDeliveryGroup group) {
        return reshipFee(group);
    }

    /** 재발송 배송비 = 편도 기본 배송비 + 결제 시점 권역 추가비(기-18 「편도 · 제주 · 도서산간 추가비 별도」). */
    public int reshipFee(OrderDeliveryGroup group) {
        int surcharge = group.getRegionSurcharge() == null ? 0 : group.getRegionSurcharge();
        return group.getBaseDeliveryFee() + surcharge;
    }

    /** 귀책 변경 운임(기-93 보완) — 그 상품의 기본 배송비(편도) 정액. 소비자가 실제 낸 운임이 아니다. */
    public int freightFlat(OrderDeliveryGroup group) {
        return group.getBaseDeliveryFee();
    }

    /**
     * @param keptAmountAfter 이 요청 뒤에도 남는 상품액(판매가 × 수량 — 취소 · 반품 · 진행 중 클레임 제외)
     * @param alreadyDeducted 같은 하위주문의 다른 반품에서 이미 최초 배송비를 뺐는가
     */
    public record ReturnContext(long keptAmountAfter, boolean alreadyDeducted) {
    }
}

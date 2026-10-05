package showroomz.domain.order.service;

/**
 * 반품 환불액 계산(앱 클레임 설계서 1-4) — 요청(박스)의 판정이 다 끝난 순간 한 번.
 *
 * <pre>
 * 환불 = 통과한 상품 금액 − 반품 배송비 차감
 * 일부 반려이고 환불이 재발송비 이상이면: 환불 −= 재발송비 (반려 상품을 결제 없이 돌려보낸다)
 * </pre>
 *
 * 환불액이 재발송비보다 작으면 빼지 않는다 — 그 재발송비는 소비자가 따로 결제한다(Q15).
 */
public final class ClaimRefundCalculator {

    private ClaimRefundCalculator() {
    }

    /**
     * @param refund          환불 큐에 올릴 금액
     * @param reshipDeducted  반려 상품 재발송비를 환불액에서 뺐는가
     */
    public record Result(int refund, boolean reshipDeducted) {
    }

    /**
     * @param passedGoods     검수를 통과한 반품 상품 금액의 합 — 0이면 환불할 것이 없다
     * @param returnDeduction 반품 배송비 차감 — 요청당 한 번
     * @param rejectReshipFee 미정산 반려 재발송비 — 반려가 없으면 null
     */
    public static Result calculate(long passedGoods, int returnDeduction, Integer rejectReshipFee) {
        if (passedGoods <= 0) {
            return new Result(0, false);
        }
        long refund = Math.max(0, passedGoods - returnDeduction);
        if (rejectReshipFee != null && refund >= rejectReshipFee) {
            return new Result((int) (refund - rejectReshipFee), true);
        }
        return new Result((int) refund, false);
    }
}

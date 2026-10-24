package showroomz.domain.settlement.service;

import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.settlement.type.SettlementItemStatus;

/**
 * 주문 항목 한 줄의 정산 분류(44 어드민 설계서 2-2 ① 표) — 명세 상태 · 반영 수량 · 차감 몫.
 *
 * <p>정산 생성({@link SettlementGenerationService})과 성과 관리(48 파트너 설계서 0-6)가 <b>같은 판정</b>을 쓰도록 한 곳에 둔다 —
 * 집계 중에 「예정」으로 보이던 분해가 정산 뒤 다른 해석으로 바뀌지 않게 하려는 것이다.
 *
 * <ul>
 *     <li>취소 — 항목의 취소 유형(없으면 하위주문의 것)으로 가른다. 분실 처리({@code LOST})만 배송 예외다.</li>
 *     <li>반송 하위주문 — 전 항목이 배송 예외다(반송은 환불로 종결).</li>
 *     <li>항목 반품 — 전량 반품.</li>
 *     <li>나머지 — 확정(반품 수량이 있으면 부분 반품).</li>
 * </ul>
 */
public final class SettlementItemClassifier {

    private SettlementItemClassifier() {
    }

    /**
     * @param settledQuantity 반영 수량 — 확정 · 부분 반품만 0 보다 크다
     * @param settledAmount   반영액 = 결제액 − 취소 − 반품 − 배송 예외
     */
    public record Classification(SettlementItemStatus status, int quantity, int returnedQuantity, long unitPrice,
                                 int settledQuantity, long settledAmount, long cancelPart, long returnPart,
                                 long exceptionPart) {

        public long paidAmount() {
            return unitPrice * quantity;
        }
    }

    public static Classification classify(OrderProductStatus itemStatus, OrderCancelType itemCancelType,
                                          FulfillmentStatus groupStatus, OrderCancelType groupCancelType,
                                          Integer price, Integer quantity, Integer returnedQuantity) {
        long unitPrice = price == null ? 0 : price;
        int qty = quantity == null ? 0 : quantity;
        int returned = Math.min(qty, returnedQuantity == null ? 0 : Math.max(0, returnedQuantity));
        long paid = unitPrice * qty;

        SettlementItemStatus status;
        int settledQuantity = 0;
        long cancelPart = 0, returnPart = 0, exceptionPart = 0;
        boolean itemCancelled = itemStatus == OrderProductStatus.CANCELLED
                || (groupStatus == FulfillmentStatus.CANCELLED && itemStatus != OrderProductStatus.RETURNED);
        if (itemCancelled) {
            OrderCancelType type = itemCancelType != null ? itemCancelType : groupCancelType;
            if (type == OrderCancelType.LOST) {
                status = SettlementItemStatus.DELIVERY_EXCEPTION;
                exceptionPart = paid;
            } else {
                status = SettlementItemStatus.CANCELLED;
                cancelPart = paid;
            }
        } else if (groupStatus == FulfillmentStatus.RETURNING) {
            status = SettlementItemStatus.DELIVERY_EXCEPTION;
            exceptionPart = paid;
        } else if (itemStatus == OrderProductStatus.RETURNED) {
            status = SettlementItemStatus.RETURNED;
            returnPart = paid;
        } else {
            returnPart = unitPrice * returned;
            settledQuantity = qty - returned;
            status = returned > 0 ? SettlementItemStatus.PARTIAL_RETURNED : SettlementItemStatus.CONFIRMED;
        }
        long settledAmount = paid - cancelPart - returnPart - exceptionPart;
        return new Classification(status, qty, returned, unitPrice, settledQuantity, settledAmount, cancelPart,
                returnPart, exceptionPart);
    }
}

package showroomz.domain.payment.type;

/**
 * 자동 취소 사유(결제 계획서 3-2 {@code payment.mismatch_reason}). NULL이면 사용자·운영자 취소다 —
 * 4-5 ⑤가 CANCELLED / CANCELLED_MISMATCH를 가르는 근거.
 */
public enum MismatchReason {
    /** 포트원 결제 금액·통화·상점이 주문과 다르다. */
    AMOUNT,
    /** 주문이 만료·취소된 뒤 결제가 도착했다. */
    ORDER_CLOSED,
    /** 주문은 다른 시도로 이미 완료됐다(이중 결제). */
    NOT_ORDER_PAYMENT
}

package showroomz.domain.order.type;

/**
 * 운임 정산이 소비자와 어떻게 오갔는가(1021 설계서 4-1).
 * REFUND_DEDUCTION(환불액에서 차감) · CHARGE(추가 결제 — 재발송비 결제에 합산) · REFUND_ADDITION(환불 가산 · PG 취소) ·
 * BANK_TRANSFER(원래 결제액 초과분 · 운영자 송금).
 */
public enum FreightSettledVia {
    REFUND_DEDUCTION,
    CHARGE,
    REFUND_ADDITION,
    BANK_TRANSFER
}

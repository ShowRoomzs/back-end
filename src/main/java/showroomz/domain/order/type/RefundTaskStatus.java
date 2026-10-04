package showroomz.domain.order.type;

/** 환불 집행 큐 상태 — 집행(DONE)은 어드민 거래 관리가 채운다. */
public enum RefundTaskStatus {
    PENDING, DONE, VOID
}

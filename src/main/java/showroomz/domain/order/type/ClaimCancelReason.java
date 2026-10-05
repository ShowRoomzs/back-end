package showroomz.domain.order.type;

/** {@link ClaimResult#CANCELLED}의 까닭(앱 클레임 설계서 1-1). */
public enum ClaimCancelReason {
    /** 소비자 철회 — 회수 송장을 넣기 전까지만. */
    WITHDRAWN,
    /** 회수 송장 미등록 자동 취소. */
    INVOICE_EXPIRED,
    /** 운영자 직권 — 검수 거절과 섞이면 거절률 집계가 오염되므로 따로 둔다. */
    ADMIN
}

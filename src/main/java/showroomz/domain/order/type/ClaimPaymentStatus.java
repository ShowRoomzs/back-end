package showroomz.domain.order.type;

/** 클레임 재발송 배송비 결제 시도의 상태(앱 클레임 설계서 1-5) — 주문 결제의 축소판이다. */
public enum ClaimPaymentStatus {
    /** 결제창을 열 수 있는 상태. */
    READY,
    PAID,
    FAILED,
    /** 취소를 선점했다 — PG 취소가 끝나면 CANCELLED. 실패하면 배치가 다시 시도한다. */
    CANCEL_REQUESTED,
    CANCELLED
}

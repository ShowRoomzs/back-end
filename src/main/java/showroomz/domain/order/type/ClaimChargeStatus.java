package showroomz.domain.order.type;

/** 추가 결제의 정산 상태(앱 클레임 설계서 1-5). */
public enum ClaimChargeStatus {
    PENDING,
    /** PG 결제. */
    PAID,
    /** 같은 요청의 환불액에서 차감. */
    DEDUCTED,
    /** 교환 선결제분으로 충당. */
    COVERED,
    /** 폐기·취소로 소멸. */
    VOID,
    /** 결제 취소로 돌려줌. */
    REFUNDED
}

package showroomz.domain.order.type;

/** 소비자 추가 결제의 종류(앱 클레임 설계서 1-5) — 둘 다 편도 재발송 배송비다. */
public enum ClaimChargeType {
    /** 고객 귀책 교환 — 요청할 때 선결제. 결제가 끝나야 요청이 접수된다. */
    EXCHANGE_RESHIP,
    /** 검수 반려 상품을 다시 받는 배송비. */
    REJECT_RESHIP
}

package showroomz.domain.order.type;

/** 환불 큐의 발생 경로(34 설계서 1-9) — 소비자 취소(준비 시작 전)는 결제 전액 취소 경로라 큐에 들어가지 않는다. */
public enum RefundTaskSource {
    CANCEL_REQUEST_APPROVED,
    SELLER_DIRECT_CANCEL,
    /** 택배 반송(배송 실패) — 반품 클레임과 다른 사건이다. */
    RETURN_COMPLETED,
    /** 반품 클레임 검수 통과 — 요청 단위({@code source_id = collection_id} · 35 설계서 1-10). */
    CLAIM_RETURN_PASSED,
    /** 운영자 사유 환불 — 반려 이의 인용 · 구매확정 후 하자 · 위해성 리콜(어드민 06a B5 · 06b B2). {@code source_id}는 근거 클레임(있으면). */
    OPERATOR_REASON
}

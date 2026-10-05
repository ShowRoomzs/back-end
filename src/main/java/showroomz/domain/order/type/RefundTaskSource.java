package showroomz.domain.order.type;

/** 환불 집행 큐의 발생 경로(34 설계서 1-9) — 소비자 취소(준비 시작 전)는 PG 자동이라 큐에 들어가지 않는다. */
public enum RefundTaskSource {
    CANCEL_REQUEST_APPROVED,
    SELLER_DIRECT_CANCEL,
    /** 택배 반송(배송 실패) — 반품 클레임과 다른 사건이다. */
    RETURN_COMPLETED,
    /** 반품 클레임 검수 통과 — 요청 단위({@code source_id = collection_id} · 35 설계서 1-10). */
    CLAIM_RETURN_PASSED
}

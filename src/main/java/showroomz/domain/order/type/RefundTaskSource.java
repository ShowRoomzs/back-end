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
    OPERATOR_REASON,
    /**
     * <b>기록 전용</b> — 결제완료 소비자 취소(준비 시작 전 · 결제 전액 취소). 큐를 거치지 않는 PG 환불을 환불 관리 완료 탭에 보이려고
     * 취소 확인 시점에 DONE 으로 바로 적는다(39 설계서 0-4). 집행기는 DONE 행을 건드리지 않는다.
     */
    USER_CANCEL_BEFORE_PREPARE,
    /** <b>기록 전용</b> — 교환 · 반려 재발송비(추가 결제)의 결제 취소. {@code source_id}는 클레임 요청(collection) id. */
    CLAIM_PAYMENT_CANCELLED
}

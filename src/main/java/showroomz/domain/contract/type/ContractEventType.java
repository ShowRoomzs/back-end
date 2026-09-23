package showroomz.domain.contract.type;

/** 계약 이력 이벤트(설계서 1-6). append-only이며 이 목록 밖의 값을 쌓지 않는다. */
public enum ContractEventType {
    CREATED,
    REVIEW_REQUESTED,
    REVIEW_REQUEST_CANCELED,
    REVIEW_APPROVED,
    REVIEW_REJECTED,
    SIGNATURE_SENT,
    BRAND_SIGNED,
    CREATOR_SIGNED,
    /** 운영자의 서명 현황 저장 1회 — 감사 기록이다. 변경 내역(detail)을 담아 어드민·파트너만 본다. */
    SIGNATURE_UPDATED,
    /** 운영자 확인으로 양측 서명이 모두 차 체결 처리 대기로 넘어간 시점 — 스튜디오 「양측 서명 완료 확인」. */
    BOTH_SIGNED_CONFIRMED,
    RESEND_REQUESTED,
    RESEND_HANDLED,
    CONTRACT_PDF_GENERATED,
    DOCUMENT_UPLOADED,
    DOCUMENT_DELETED,
    CONCLUDED,
    DECLINED,
    EXPIRED,
    CANCELED,
    FIXED_FEE_PAID,
    GROUP_BUY_CREATED
}

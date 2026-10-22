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
    /** 운영자 확인으로 양측 서명이 모두 차 공구 생성 대기로 넘어간 시점 — 스튜디오 「양측 서명 완료 확인」. */
    BOTH_SIGNED_CONFIRMED,
    /** 계약 성립 — 양측 서명 완료(1021 기-05). SYSTEM · 발생 시각 = 나중 서명 시각(established_at). */
    ESTABLISHED,
    RESEND_REQUESTED,
    RESEND_HANDLED,
    /** 계약서 생성본(제출본 PDF) 생성 — 검토 요청 시 SYSTEM이, 그때 실패했으면 어드민 첫 다운로드가 만든다. */
    CONTRACT_PDF_GENERATED,
    DOCUMENT_UPLOADED,
    DOCUMENT_DELETED,
    CONCLUDED,
    DECLINED,
    EXPIRED,
    CANCELED,
    FIXED_FEE_PAID,
    GROUP_BUY_CREATED,
    /** 해지 요청(1021 설계서 1-4) — 요청자 · 사유 라벨. */
    TERMINATION_REQUESTED,
    /** 해지 요청 철회 — 처리 전만. */
    TERMINATION_REQUEST_WITHDRAWN,
    /** 해지 — 운영자 처리 · 종결. 생성된 공구도 함께 종결. */
    TERMINATED,
    /** 초안 승인 기록(1021 기-30) — 브랜드가 콘텐츠 초안을 승인한 사실. */
    DRAFT_APPROVAL_RECORDED,
    /** 브랜드 삭제(작성중·검토 반려) — 행을 남기는 표시라 이력도 남는다. */
    DELETED
}

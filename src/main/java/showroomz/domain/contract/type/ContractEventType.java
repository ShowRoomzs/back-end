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
    SIGNATURE_UPDATED,
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

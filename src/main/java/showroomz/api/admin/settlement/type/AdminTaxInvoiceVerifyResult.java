package showroomz.api.admin.settlement.type;

import showroomz.domain.settlement.type.TaxInvoiceRejectReason;

/** M4 승인번호 대조 결과(44 어드민 설계서 7-6) — 운영자는 번호를 입력하지 않고 결과만 고른다. */
public enum AdminTaxInvoiceVerifyResult {
    MATCH,
    AMOUNT_MISMATCH,
    RECIPIENT_MISMATCH,
    NOT_FOUND;

    /** 반려 사유 — 확인(MATCH)이면 null. */
    public TaxInvoiceRejectReason rejectReason() {
        return switch (this) {
            case MATCH -> null;
            case AMOUNT_MISMATCH -> TaxInvoiceRejectReason.AMOUNT_MISMATCH;
            case RECIPIENT_MISMATCH -> TaxInvoiceRejectReason.RECIPIENT_MISMATCH;
            case NOT_FOUND -> TaxInvoiceRejectReason.NOT_FOUND;
        };
    }
}

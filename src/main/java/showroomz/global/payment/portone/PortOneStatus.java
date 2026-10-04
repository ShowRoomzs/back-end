package showroomz.global.payment.portone;

/** 포트원 V2 결제 상태({@code GET /payments/{id}}의 {@code status}). 모르는 값은 {@link #UNKNOWN}으로 접어 로그만 남긴다. */
public enum PortOneStatus {
    READY,
    PENDING,
    PAY_PENDING,
    VIRTUAL_ACCOUNT_ISSUED,
    PAID,
    FAILED,
    PARTIAL_CANCELLED,
    CANCELLED,
    UNKNOWN;

    public static PortOneStatus of(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /** 승인 진행 중 — 만료를 미루는 근거(4-4). */
    public boolean isInProgress() {
        return this == PENDING || this == PAY_PENDING || this == VIRTUAL_ACCOUNT_ISSUED;
    }
}

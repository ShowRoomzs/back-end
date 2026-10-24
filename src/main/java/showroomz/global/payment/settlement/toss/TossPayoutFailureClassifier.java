package showroomz.global.payment.settlement.toss;

import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerStatus;
import showroomz.domain.settlement.type.PayoutBlockReason;

import java.util.Locale;
import java.util.Set;

/**
 * 토스 실패 → {@code fail_code}(44 토스 설계서 5-5). 보류 사유는 {@link PayoutBlockReason} 이름, 분배 실패는 어댑터 코드다.
 * 원문({@code code: message})은 {@code fail_reason} 에 따로 남는다.
 */
public final class TossPayoutFailureClassifier {

    // ---- 보류 사유(PayoutBlockReason 과 같은 문자열)
    public static final String PARTNER_NOT_READY = PayoutBlockReason.PARTNER_NOT_READY.name();
    public static final String ACCOUNT_HOLDER_MISMATCH = PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name();
    public static final String COMPANY_NOT_IN_BUSINESS = PayoutBlockReason.COMPANY_NOT_IN_BUSINESS.name();
    public static final String ACCOUNT_MISSING = PayoutBlockReason.ACCOUNT_MISSING.name();
    public static final String ACCOUNT_INVALID = PayoutBlockReason.ACCOUNT_INVALID.name();
    public static final String UNSUPPORTED_BANK = PayoutBlockReason.UNSUPPORTED_BANK.name();
    public static final String IDENTITY_VERIFICATION_REQUIRED = PayoutBlockReason.IDENTITY_VERIFICATION_REQUIRED.name();
    public static final String KYC_REQUIRED = PayoutBlockReason.KYC_REQUIRED.name();
    public static final String PAYOUT_CONTRACT_BLOCKED = PayoutBlockReason.PAYOUT_CONTRACT_BLOCKED.name();
    // ---- 분배 실패(어댑터 코드)
    public static final String ACCOUNT_SYNC_FAILED = "ACCOUNT_SYNC_FAILED";
    public static final String REJECTED_BY_PG = "REJECTED_BY_PG";
    public static final String CANCELLED_AT_PG = "CANCELLED_AT_PG";
    public static final String BANK_REJECTED = "BANK_REJECTED";
    public static final String TRANSFER_NOT_FOUND = "TRANSFER_NOT_FOUND";
    /** 행에는 남지 않는다 — {@code DEFERRED} 로 다음 영업일(5-1 ①). */
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";

    // ---- 토스 코드
    public static final String DUPLICATE_SELLER = "DUPLICATE_SELLER";
    public static final String DUPLICATE_PAYOUT_REQUEST = "DUPLICATE_PAYOUT_REQUEST";
    public static final String INVALID_BUSINESS_NUMBER = "INVALID_BUSINESS_NUMBER";
    public static final String EXCEEDED_PAYOUT_BALANCE_AMOUNT = "EXCEEDED_PAYOUT_BALANCE_AMOUNT";
    public static final String FORBIDDEN_SELLER_PAYOUT = "FORBIDDEN_SELLER_PAYOUT";
    public static final String FORBIDDEN_SELLER_MODIFY = "FORBIDDEN_SELLER_MODIFY";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NOT_FOUND_SELLER = "NOT_FOUND_SELLER";
    public static final String INVALID_SELLER_INFO = "INVALID_SELLER_INFO";

    /** 결과를 모르는 것으로 취급하는 토스 코드 — 예외로 던져 REQUESTED 로 둔다(5-2 ④). */
    public static final Set<String> UNKNOWN_OUTCOME_CODES = Set.of("PREVIOUS_REQUEST_IN_PROGRESS", "EXCEED_MAXIMUM_REQUESTS",
            "IDEMPOTENT_REQUEST_PROCESSING", "COMMON_ERROR", "BUSINESS_REGISTRATION_LOOKUP_FAILED",
            "NOT_AVAILABLE_BANK_ACCOUNT_VERIFICATION");

    private static final Set<String> CONTRACT_CODES = Set.of("FORBIDDEN_REQUEST", "FORBIDDEN_OVERDUE_PAYOUT_FEE",
            "FORBIDDEN_ACCOUNT_SUSPENDED");

    // ---- 셀러 상태(1-3)
    public static final String STATUS_APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String STATUS_PARTIALLY_APPROVED = "PARTIALLY_APPROVED";
    public static final String STATUS_KYC_REQUIRED = "KYC_REQUIRED";
    public static final String STATUS_APPROVED = "APPROVED";

    private TossPayoutFailureClassifier() {
    }

    /** 셀러 등록 · 수정 거절 코드 → 보류 사유. */
    public static String sellerFailCode(String tossCode) {
        if (tossCode == null) {
            return PARTNER_NOT_READY;
        }
        return switch (tossCode) {
            case "INVALID_ACCOUNT_INFO", "NOT_SELLER_ACCOUNT" -> ACCOUNT_HOLDER_MISMATCH;
            case "BANK_ACCOUNT_VERIFICATION_FAIL" -> BANK_REJECTED;
            case INVALID_BUSINESS_NUMBER -> COMPANY_NOT_IN_BUSINESS;
            case FORBIDDEN_SELLER_MODIFY -> ACCOUNT_SYNC_FAILED;
            default -> CONTRACT_CODES.contains(tossCode) ? PAYOUT_CONTRACT_BLOCKED : PARTNER_NOT_READY;
        };
    }

    /** 지급 요청 동기 거절 코드 → 분배 실패 코드(결과 모름 · 잔액 · 중복은 호출자가 먼저 가른다). */
    public static String payoutRequestFailCode(String tossCode) {
        if (tossCode == null) {
            return REJECTED_BY_PG;
        }
        return switch (tossCode) {
            case NOT_FOUND_SELLER, INVALID_SELLER_INFO -> PARTNER_NOT_READY;
            case FORBIDDEN_SELLER_PAYOUT -> IDENTITY_VERIFICATION_REQUIRED;   // 호출자가 셀러 상태로 KYC 와 가른다
            case EXCEEDED_PAYOUT_BALANCE_AMOUNT -> INSUFFICIENT_BALANCE;
            default -> CONTRACT_CODES.contains(tossCode) ? PAYOUT_CONTRACT_BLOCKED : REJECTED_BY_PG;
        };
    }

    /** 지급 결과 {@code error.code}(FAILED · REJECTED) — 알려진 것만 가르고 모르면 은행 거절(계좌 변경 뒤 재분배가 기본 대응). */
    public static String payoutResultFailCode(String code, String message) {
        String lowerCode = code == null ? "" : code.toUpperCase(Locale.ROOT);
        String lowerMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lowerCode.contains("ACCOUNT_INFO") || lowerCode.contains("HOLDER") || lowerMessage.contains("예금주")) {
            return ACCOUNT_HOLDER_MISMATCH;
        }
        if (lowerCode.contains("SELLER") || lowerCode.contains("KYC")) {
            return PARTNER_NOT_READY;
        }
        if (CONTRACT_CODES.contains(lowerCode)) {
            return PAYOUT_CONTRACT_BLOCKED;
        }
        return BANK_REJECTED;
    }

    /** 토스 셀러 상태 → 우리 상태(0-8). {@code PARTIALLY_APPROVED} 도 지급 가능이다. */
    public static PartnerStatus partnerStatusOf(String rawStatus) {
        if (rawStatus == null) {
            return PartnerStatus.FAILED;
        }
        return switch (rawStatus) {
            case STATUS_APPROVED, STATUS_PARTIALLY_APPROVED -> PartnerStatus.APPROVED;
            case STATUS_APPROVAL_REQUIRED, STATUS_KYC_REQUIRED -> PartnerStatus.PENDING;
            default -> PartnerStatus.REJECTED;
        };
    }

    /** 미승인 셀러 상태의 보류 사유. */
    public static String blockCodeOf(String rawStatus) {
        return STATUS_KYC_REQUIRED.equals(rawStatus) ? KYC_REQUIRED : IDENTITY_VERIFICATION_REQUIRED;
    }

    public static boolean isUnknownOutcome(int httpStatus, String tossCode) {
        return httpStatus >= 500 || httpStatus == 429 || httpStatus == 409
                || (tossCode != null && UNKNOWN_OUTCOME_CODES.contains(tossCode));
    }
}

package showroomz.global.payment.settlement.portone;

import io.portone.sdk.server.errors.PlatformAccountVerificationFailedException;
import io.portone.sdk.server.errors.PlatformCompanyNotFoundException;
import io.portone.sdk.server.errors.PlatformNotEnabledException;
import io.portone.sdk.server.errors.PlatformNotSupportedBankException;
import io.portone.sdk.server.errors.PlatformPartnerNotFoundException;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;

import java.util.Locale;

/**
 * 포트원 실패 → {@code fail_code}(44_포트원_파트너정산_연동_BE_설계서.md 5-5). 파트너 등록 실패는 {@link PayoutBlockReason} 이름을,
 * 지급 실패는 어댑터 코드를 쓴다. 원문은 {@code fail_reason} 에 따로 남는다.
 */
public final class PortOneFailureClassifier {

    public static final String PARTNER_NOT_READY = PayoutBlockReason.PARTNER_NOT_READY.name();
    public static final String ACCOUNT_HOLDER_MISMATCH = PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name();
    public static final String COMPANY_NOT_IN_BUSINESS = PayoutBlockReason.COMPANY_NOT_IN_BUSINESS.name();
    public static final String UNSUPPORTED_BANK = PayoutBlockReason.UNSUPPORTED_BANK.name();
    public static final String ACCOUNT_SYNC_FAILED = "ACCOUNT_SYNC_FAILED";
    public static final String REJECTED_BY_PG = "REJECTED_BY_PG";
    public static final String CANCELLED_AT_PG = "CANCELLED_AT_PG";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String BANK_REJECTED = "BANK_REJECTED";
    public static final String TRANSFER_NOT_FOUND = "TRANSFER_NOT_FOUND";
    public static final String PLATFORM_NOT_ENABLED = PayoutBlockReason.PLATFORM_NOT_ENABLED.name();

    private PortOneFailureClassifier() {
    }

    /** 파트너 등록 · 갱신 중 포트원이 거절한 것 — 보류 사유. */
    public static String partnerFailCode(PaymentGatewayRejectedException e) {
        Throwable cause = e.getCause();
        if (cause instanceof PlatformAccountVerificationFailedException) {
            return ACCOUNT_HOLDER_MISMATCH;
        }
        if (cause instanceof PlatformNotSupportedBankException) {
            return UNSUPPORTED_BANK;
        }
        if (cause instanceof PlatformCompanyNotFoundException) {
            return COMPANY_NOT_IN_BUSINESS;
        }
        if (cause instanceof PlatformNotEnabledException) {
            return PLATFORM_NOT_ENABLED;
        }
        return PARTNER_NOT_READY;
    }

    /** 정산건 생성 중 포트원이 거절한 것 — 분배 실패 코드. */
    public static String transferFailCode(PaymentGatewayRejectedException e) {
        Throwable cause = e.getCause();
        if (cause instanceof PlatformPartnerNotFoundException) {
            return PARTNER_NOT_READY;
        }
        if (cause instanceof PlatformNotEnabledException) {
            return PLATFORM_NOT_ENABLED;
        }
        return REJECTED_BY_PG;
    }

    /** 지급 결과의 {@code failReason}(문장) — 알려진 낱말로 분류한다. 모르면 BANK_REJECTED(계좌 변경 뒤 재분배가 기본 대응). */
    public static String payoutFailCode(String failReason) {
        if (failReason == null) {
            return BANK_REJECTED;
        }
        String lower = failReason.toLowerCase(Locale.ROOT);
        if (lower.contains("예금주") || lower.contains("holder")) {
            return ACCOUNT_HOLDER_MISMATCH;
        }
        if (lower.contains("취소") || lower.contains("cancel")) {
            return CANCELLED_AT_PG;
        }
        return BANK_REJECTED;
    }
}

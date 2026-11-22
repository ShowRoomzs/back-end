package showroomz.global.payment.settlement.inicis;

import showroomz.domain.settlement.type.PayoutBlockReason;

import java.util.Locale;
import java.util.Set;

/**
 * 이니시스 응답 코드 → {@code fail_code}(60 전환 계획서 5절 · D-3 · D-8). 코드표 전문(Q5)을 아직 받지 못해 성공 코드 외에는 <b>메시지 낱말로
 * 가르는 잠정 규칙</b>이다 — 모르는 코드는 원문을 {@code fail_reason} 에 보존하고 아래 기본값으로 떨어진다. 코드표를 받으면 이 클래스만 바뀐다.
 *
 * <ul>
 *   <li>서브몰 등록 실패의 기본값은 {@code PARTNER_NOT_READY} — 지급 배치 앞단이 다시 시도하는 사유여야 한다({@code PARTNER_CODES}).</li>
 *   <li>지급 요청 거절의 기본값은 {@code REJECTED_BY_PG}, 송금 실패는 {@code INICIS_{clPay}}.</li>
 * </ul>
 */
public final class InicisPayoutFailureClassifier {

    public static final String SUCCESS_UPSERT = "SA01";
    public static final String SUCCESS_PAYREQ = "SB01";
    public static final String SUCCESS_PAYRESULT = "SC01";
    public static final String SUCCESS_PAYCHECK = "SD01";
    public static final String SUCCESS_AMLSTATUS = "SM01";

    // ---- 보류 사유(PayoutBlockReason 과 같은 문자열)
    public static final String PARTNER_NOT_READY = PayoutBlockReason.PARTNER_NOT_READY.name();
    public static final String PARTNER_PROFILE_INCOMPLETE = PayoutBlockReason.PARTNER_PROFILE_INCOMPLETE.name();
    public static final String ACCOUNT_HOLDER_MISMATCH = PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name();
    public static final String ACCOUNT_MISSING = PayoutBlockReason.ACCOUNT_MISSING.name();
    public static final String ACCOUNT_INVALID = PayoutBlockReason.ACCOUNT_INVALID.name();
    public static final String UNSUPPORTED_BANK = PayoutBlockReason.UNSUPPORTED_BANK.name();
    public static final String PAYOUT_CONTRACT_BLOCKED = PayoutBlockReason.PAYOUT_CONTRACT_BLOCKED.name();
    // ---- 분배 실패(어댑터 코드)
    public static final String REJECTED_BY_PG = "REJECTED_BY_PG";
    public static final String BANK_REJECTED = "BANK_REJECTED";
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String PAYOUT_FAIL_PREFIX = "INICIS_";

    /** 「이미 등록된 서브몰」 코드 — 코드표(Q5) 받으면 채운다. 그 전에는 메시지 낱말({@link #isAlreadyRegistered})로 본다. */
    static final Set<String> ALREADY_REGISTERED_CODES = Set.of();
    /** 잔액 부족 코드 — Q5. */
    static final Set<String> INSUFFICIENT_BALANCE_CODES = Set.of();

    private InicisPayoutFailureClassifier() {
    }

    /** 서브몰 {@code INS} 가 「이미 있음」으로 거절됐는가 — 그렇다면 {@code UPD} 로 1회 재시도한다(D-3). */
    public static boolean isAlreadyRegistered(String code, String message) {
        if (code != null && ALREADY_REGISTERED_CODES.contains(code)) {
            return true;
        }
        String m = lower(message);
        return m.contains("이미") || m.contains("중복") || m.contains("already") || m.contains("duplicate");
    }

    /** 서브몰에 미송금 지급 데이터가 남아 있어 수정이 거절됐는가 — 계좌 변경을 미룬다(D-1 ⑤). */
    public static boolean isModifyBlocked(String message) {
        String m = lower(message);
        return m.contains("지급") && (m.contains("수정 불가") || m.contains("변경 불가"));
    }

    /** {@code payreq} 가 잔액 부족으로 거절됐는가 — 묶음을 지우고 행을 다음 지급일로(D-9 · 5절). */
    public static boolean isInsufficientBalance(String code, String message) {
        if (code != null && INSUFFICIENT_BALANCE_CODES.contains(code)) {
            return true;
        }
        String m = lower(message);
        return m.contains("잔액") || m.contains("자금 부족") || m.contains("insufficient");
    }

    /** 서브몰 등록 · 수정 거절 → 보류 사유. 기본은 배치 앞단이 다시 시도하는 {@code PARTNER_NOT_READY}. */
    public static String subMallFailCode(String code, String message) {
        String m = lower(message);
        if (m.contains("예금주")) {
            return ACCOUNT_HOLDER_MISMATCH;
        }
        if (m.contains("은행")) {
            return UNSUPPORTED_BANK;
        }
        if (m.contains("계좌")) {
            return BANK_REJECTED;
        }
        if (isContractProblem(code, m)) {
            return PAYOUT_CONTRACT_BLOCKED;
        }
        return PARTNER_NOT_READY;
    }

    /** 지급 요청 동기 거절 → 분배 실패 코드(잔액 · 결과 모름은 호출자가 먼저 가른다). */
    public static String payRequestFailCode(String code, String message) {
        String m = lower(message);
        if (m.contains("서브몰") || m.contains("판매자") || m.contains("idmall")) {
            return PARTNER_NOT_READY;
        }
        if (isContractProblem(code, m)) {
            return PAYOUT_CONTRACT_BLOCKED;
        }
        return REJECTED_BY_PG;
    }

    /** 송금 결과 {@code clPay} ≠ 01 → {@code INICIS_{clPay}}(D-8 · 원문 코드 그대로). */
    public static String payResultFailCode(String clPay) {
        return PAYOUT_FAIL_PREFIX + (clPay == null || clPay.isBlank() ? "UNKNOWN" : clPay.trim());
    }

    private static boolean isContractProblem(String code, String lowerMessage) {
        return lowerMessage.contains("api key") || lowerMessage.contains("api-key") || lowerMessage.contains("인증")
                || lowerMessage.contains("계약") || lowerMessage.contains("권한");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

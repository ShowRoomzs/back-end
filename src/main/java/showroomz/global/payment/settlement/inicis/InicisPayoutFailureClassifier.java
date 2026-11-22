package showroomz.global.payment.settlement.inicis;

import showroomz.domain.settlement.type.PayoutBlockReason;

import java.util.Set;

/**
 * 이니시스 응답 코드 → {@code fail_code}(60 전환 계획서 5절 · D-3 · D-8). 코드표는 매뉴얼 팝업 「지급대행 AML 코드 상세」(2026-11-22 확보 ·
 * {@code manual.inicis.com/assets/add-option.html#popup_103})를 따른다. 표에 없는 코드는 원문을 {@code fail_reason} 에 보존하고 아래 기본값으로
 * 떨어진다.
 *
 * <ul>
 *   <li>성공 코드가 API 마다 여럿이다 — 서브몰 등록 SA01 / 수정 SA02 · 지급 등록 SB01 / 삭제 SB03 / 고객확인 대상이지만 등록 성공 SB05 · 송금 결과
 *   SC01 / 데이터 없음 SC02 · 잔액 SD01 · SD02 · AML SM01 / 미대상 SM02.</li>
 *   <li>서브몰 등록 실패의 기본값은 {@code PARTNER_NOT_READY} — 지급 배치 앞단이 다시 시도하는 사유여야 한다({@code PARTNER_CODES}).</li>
 *   <li>지급 요청 거절의 기본값은 {@code REJECTED_BY_PG}, 송금 실패는 {@code INICIS_{clPay}}(clPay 는 「01 성공 · 이외 실패」뿐이다).</li>
 *   <li>잔액 부족 코드는 표에 없다 — 지급 등록은 잔액과 무관하게 받고 송금 때 갈린다(잔액은 {@code paycheck} 로 우리가 먼저 본다 · D-9).</li>
 * </ul>
 */
public final class InicisPayoutFailureClassifier {

    public static final String SUCCESS_UPSERT = "SA01";
    public static final String SUCCESS_UPSERT_UPDATE = "SA02";
    public static final String SUCCESS_PAYREQ = "SB01";
    public static final String SUCCESS_PAYREQ_DELETE = "SB03";
    /** 「고객확인이행 대상입니다. 지급요청 데이터 등록은 성공하였습니다.」 — 접수는 됐다. 수취자 · 운영자에게 고객확인 안내(D-6). */
    public static final String PAYREQ_AML_REQUIRED = "SB05";
    public static final String SUCCESS_PAYRESULT = "SC01";
    /** 「송금결과 내역 조회 데이터가 없습니다.」 — 빈 결과(오류 아님). */
    public static final String PAYRESULT_EMPTY = "SC02";
    public static final String SUCCESS_PAYCHECK = "SD01";
    public static final String SUCCESS_AMLSTATUS = "SM01";
    /** 「AML 미대상 판매자ID 입니다.」 — 고객확인 의무가 없다(막지 않는다). */
    public static final String AML_NOT_TARGET = "SM02";

    static final Set<String> SUCCESS_UPSERT_CODES = Set.of(SUCCESS_UPSERT, SUCCESS_UPSERT_UPDATE);
    static final Set<String> SUCCESS_PAYREQ_CODES = Set.of(SUCCESS_PAYREQ, SUCCESS_PAYREQ_DELETE, PAYREQ_AML_REQUIRED);
    static final Set<String> SUCCESS_PAYRESULT_CODES = Set.of(SUCCESS_PAYRESULT, PAYRESULT_EMPTY);
    static final Set<String> SUCCESS_PAYCHECK_CODES = Set.of(SUCCESS_PAYCHECK, "SD02");
    static final Set<String> SUCCESS_AMLSTATUS_CODES = Set.of(SUCCESS_AMLSTATUS, AML_NOT_TARGET);

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

    // ---- 실패 코드(popup_103)
    /** 「이미 사용중인 ID이므로 등록 불가능합니다.」 — INS 를 UPD 로 1회 재시도(D-3). */
    public static final String ALREADY_REGISTERED = "EM04";
    /** 「수정 가능한 ID가 없습니다.」 — UPD 를 INS 로 1회 재시도(이니시스에서 지워졌거나 환경이 바뀌었다). */
    public static final String NOT_REGISTERED = "EM05";
    /** 「등록 불가능한 은행코드 입니다.」 */
    static final String UNSUPPORTED_BANK_CODE = "EM06";
    /** 헤더 · API 키 · 가맹점 · 위변조값 — 설정 · 계약 문제(운영자). EH01~05 · EM01 · EM02 · EM03. */
    static final Set<String> CONTRACT_CODES = Set.of("EH01", "EH02", "EH03", "EH04", "EH05", "EM01", "EM02", "EM03");
    /**
     * 일시 오류 — 이니시스가 이번 요청을 처리하지 못했다(접수 안 됨이 확실 · 다시 보내면 된다). EH06 요청 id 중복(새 id 로 다시) · EM07 서브몰 등록/수정 실패 ·
     * EM08 지급 삭제 실패 · EM18 지급 등록 실패 · EM50 · EM51 예금주 조회 · 복호화 서버 예외. 행을 실패로 돌리지 않는다(운영자 재분배 · attempt 소모 없이).
     */
    static final Set<String> TRANSIENT_CODES = Set.of("EH06", "EM07", "EM08", "EM18", "EM50", "EM51");
    /** 필수 항목 누락 — 서브몰 정보가 비었다(EB01 · EM16). */
    static final Set<String> MISSING_FIELD_CODES = Set.of("EB01", "EM16");
    /** 지급 요청이 가리키는 서브몰이 없다 · 정보가 틀렸다(EM16 · EM17 · EM20). */
    static final Set<String> SUB_MALL_MISSING_CODES = Set.of("EM16", "EM17", "EM20");
    /** 잔액 부족 — 코드표에 없다(지급 등록은 잔액과 무관하게 받는다). 생기면 여기 넣는다. */
    static final Set<String> INSUFFICIENT_BALANCE_CODES = Set.of();

    private InicisPayoutFailureClassifier() {
    }

    /** 서브몰 {@code INS} 가 「이미 있음」(EM04)으로 거절됐는가 — 그렇다면 {@code UPD} 로 1회 재시도한다(D-3). */
    public static boolean isAlreadyRegistered(String code) {
        return ALREADY_REGISTERED.equals(code);
    }

    /** 서브몰 {@code UPD} 가 「수정할 ID 없음」(EM05)으로 거절됐는가 — {@code INS} 로 1회 재시도한다. */
    public static boolean isNotRegistered(String code) {
        return NOT_REGISTERED.equals(code);
    }

    /** 일시 오류인가 — 그렇다면 같은 요청을 다음 틱에 다시 보낸다({@link #TRANSIENT_CODES}). */
    public static boolean isTransient(String code) {
        return code != null && TRANSIENT_CODES.contains(code);
    }

    /** {@code payreq} 가 잔액 부족으로 거절됐는가 — 코드표에 잔액 코드가 없어 지금은 언제나 false. */
    public static boolean isInsufficientBalance(String code) {
        return code != null && INSUFFICIENT_BALANCE_CODES.contains(code);
    }

    /** 서브몰 등록 · 수정 거절 → 보류 사유. 기본은 배치 앞단이 다시 시도하는 {@code PARTNER_NOT_READY}. */
    public static String subMallFailCode(String code) {
        if (code == null) {
            return PARTNER_NOT_READY;
        }
        if (UNSUPPORTED_BANK_CODE.equals(code)) {
            return UNSUPPORTED_BANK;
        }
        if (CONTRACT_CODES.contains(code)) {
            return PAYOUT_CONTRACT_BLOCKED;
        }
        if (MISSING_FIELD_CODES.contains(code)) {
            return PARTNER_PROFILE_INCOMPLETE;
        }
        // EB02 · EB03 · EM17(유효성) · 일시 오류(EH06 · EM07 · EM50 · EM51)는 배치 앞단이 다시 시도한다.
        return PARTNER_NOT_READY;
    }

    /**
     * 지급 요청 동기 거절 → 분배 실패 코드. EM13 · EM14 · EM15(지급일 오류 — 우리 영업일 달력 · 마감이 이니시스와 어긋났다 · 운영자 확인) 등은
     * {@code REJECTED_BY_PG}. 일시 오류({@link #isTransient})는 어댑터가 실패로 돌리지 않고 {@code RETRY} 로 돌려준다.
     */
    public static String payRequestFailCode(String code) {
        if (code == null) {
            return REJECTED_BY_PG;
        }
        if (SUB_MALL_MISSING_CODES.contains(code)) {
            return PARTNER_NOT_READY;
        }
        if (CONTRACT_CODES.contains(code)) {
            return PAYOUT_CONTRACT_BLOCKED;
        }
        return REJECTED_BY_PG;
    }

    /** 송금 결과 {@code clPay} ≠ 01 → {@code INICIS_{clPay}}(D-8 · 원문 코드 그대로). */
    public static String payResultFailCode(String clPay) {
        return PAYOUT_FAIL_PREFIX + (clPay == null || clPay.isBlank() ? "UNKNOWN" : clPay.trim());
    }
}

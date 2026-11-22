package showroomz.global.payment.settlement.inicis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.settlement.type.PayoutBlockReason;

import static org.assertj.core.api.Assertions.assertThat;

/** 이니시스 실패 분류(60 전환 계획서 9-1) — 매뉴얼 「지급대행 AML 코드 상세」(popup_103) 기준. 표에 없는 코드는 기본값으로 떨어진다. */
@DisplayName("이니시스 실패 분류 — 코드표 기준 · 모르는 코드 → 기본값 · 송금 실패 INICIS_{clPay} · 재시도 코드 EM04 · EM05")
class InicisPayoutFailureClassifierTest {

    @Test
    @DisplayName("성공 코드는 API 마다 여럿 — 등록 SA01/수정 SA02 · 지급 SB01/삭제 SB03/고객확인 대상 SB05 · 결과 SC01/없음 SC02 · AML SM01/미대상 SM02")
    void successCodes() {
        assertThat(InicisPayoutFailureClassifier.SUCCESS_UPSERT_CODES).containsExactlyInAnyOrder("SA01", "SA02");
        assertThat(InicisPayoutFailureClassifier.SUCCESS_PAYREQ_CODES).containsExactlyInAnyOrder("SB01", "SB03", "SB05");
        assertThat(InicisPayoutFailureClassifier.SUCCESS_PAYRESULT_CODES).containsExactlyInAnyOrder("SC01", "SC02");
        assertThat(InicisPayoutFailureClassifier.SUCCESS_AMLSTATUS_CODES).containsExactlyInAnyOrder("SM01", "SM02");
    }

    @Test
    @DisplayName("지급 요청 거절 — 서브몰 없음 · 정보 오류(EM16 · EM17 · EM20) → PARTNER_NOT_READY · 헤더 · 키 · 가맹점 · 위변조(EH · EM01 · EM02) → 계약 · 그 밖(지급일 오류 EM14 등) → REJECTED_BY_PG")
    void payRequestFailCode() {
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("EM17")).isEqualTo(PayoutBlockReason.PARTNER_NOT_READY.name());
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("EH05")).isEqualTo(PayoutBlockReason.PAYOUT_CONTRACT_BLOCKED.name());
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("EM01")).isEqualTo(PayoutBlockReason.PAYOUT_CONTRACT_BLOCKED.name());
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("EM14")).isEqualTo("REJECTED_BY_PG");
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("ZZ99")).isEqualTo("REJECTED_BY_PG");
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode(null)).isEqualTo("REJECTED_BY_PG");
    }

    @Test
    @DisplayName("송금 결과 clPay ≠ 01 → INICIS_{clPay} — 원문 코드 그대로 · 비면 INICIS_UNKNOWN")
    void payResultFailCode() {
        assertThat(InicisPayoutFailureClassifier.payResultFailCode("02")).isEqualTo("INICIS_02");
        assertThat(InicisPayoutFailureClassifier.payResultFailCode(" 99 ")).isEqualTo("INICIS_99");
        assertThat(InicisPayoutFailureClassifier.payResultFailCode(null)).isEqualTo("INICIS_UNKNOWN");
    }

    @Test
    @DisplayName("서브몰 등록 거절 — 은행 코드(EM06) → UNSUPPORTED_BANK · 필수 누락(EB01 · EM16) → PARTNER_PROFILE_INCOMPLETE · 계약 · 위변조 → PAYOUT_CONTRACT_BLOCKED · 그 밖(EM07 · EM50 예금주 조회 실패) → PARTNER_NOT_READY(재시도)")
    void subMallFailCode() {
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EM06")).isEqualTo(PayoutBlockReason.UNSUPPORTED_BANK.name());
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EB01")).isEqualTo(PayoutBlockReason.PARTNER_PROFILE_INCOMPLETE.name());
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EM02")).isEqualTo(PayoutBlockReason.PAYOUT_CONTRACT_BLOCKED.name());
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EM07")).isEqualTo(PayoutBlockReason.PARTNER_NOT_READY.name())
                .isIn(PayoutBlockReason.PARTNER_CODES);
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EM50")).isEqualTo(PayoutBlockReason.PARTNER_NOT_READY.name());
        assertThat(InicisPayoutFailureClassifier.subMallFailCode(null)).isEqualTo(PayoutBlockReason.PARTNER_NOT_READY.name());
    }

    @Test
    @DisplayName("재시도 코드 — EM04(이미 사용중인 ID) → UPD · EM05(수정 가능한 ID 없음) → INS · 잔액 부족 코드는 코드표에 없다")
    void retryCodes() {
        assertThat(InicisPayoutFailureClassifier.isAlreadyRegistered("EM04")).isTrue();
        assertThat(InicisPayoutFailureClassifier.isAlreadyRegistered("EM07")).isFalse();
        assertThat(InicisPayoutFailureClassifier.isNotRegistered("EM05")).isTrue();
        assertThat(InicisPayoutFailureClassifier.isInsufficientBalance("EM18")).isFalse();
    }

    @Test
    @DisplayName("일시 오류(접수 안 됨 · 다시 보내면 된다) — EH06 요청 id 중복 · EM07 · EM08 · EM18 · EM50 · EM51 / 확정 거절(EM17 · EM14 · EH05)은 아니다 · EH06 은 계약 문제가 아니다")
    void transientCodes() {
        for (String code : new String[]{"EH06", "EM07", "EM08", "EM18", "EM50", "EM51"}) {
            assertThat(InicisPayoutFailureClassifier.isTransient(code)).as(code).isTrue();
        }
        for (String code : new String[]{"EM17", "EM14", "EH05", "EM06", "SB01", null}) {
            assertThat(InicisPayoutFailureClassifier.isTransient(code)).as(String.valueOf(code)).isFalse();
        }
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EH06")).isEqualTo(PayoutBlockReason.PARTNER_NOT_READY.name());
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("EH06")).isNotEqualTo(PayoutBlockReason.PAYOUT_CONTRACT_BLOCKED.name());
    }
}

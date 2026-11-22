package showroomz.global.payment.settlement.inicis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.settlement.type.PayoutBlockReason;

import static org.assertj.core.api.Assertions.assertThat;

/** 이니시스 실패 분류(60 전환 계획서 9-1) — 코드표(Q5) 전 잠정 규칙. 모르는 코드는 원문을 보존하고 기본값으로 떨어진다. */
@DisplayName("이니시스 실패 분류 — 모르는 코드 → REJECTED_BY_PG · 송금 실패 INICIS_{clPay} · 서브몰 기본 PARTNER_NOT_READY · 낱말 판정")
class InicisPayoutFailureClassifierTest {

    @Test
    @DisplayName("지급 요청 거절 — 모르는 코드 → REJECTED_BY_PG · 서브몰 낱말 → PARTNER_NOT_READY · 인증 · 계약 → PAYOUT_CONTRACT_BLOCKED")
    void payRequestFailCode() {
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("ZZ99", "알 수 없는 오류")).isEqualTo("REJECTED_BY_PG");
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode(null, null)).isEqualTo("REJECTED_BY_PG");
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("EB10", "등록되지 않은 서브몰"))
                .isEqualTo(PayoutBlockReason.PARTNER_NOT_READY.name());
        assertThat(InicisPayoutFailureClassifier.payRequestFailCode("EA01", "API KEY 인증 실패"))
                .isEqualTo(PayoutBlockReason.PAYOUT_CONTRACT_BLOCKED.name());
    }

    @Test
    @DisplayName("송금 결과 clPay ≠ 01 → INICIS_{clPay} — 원문 코드 그대로 · 비면 INICIS_UNKNOWN")
    void payResultFailCode() {
        assertThat(InicisPayoutFailureClassifier.payResultFailCode("12")).isEqualTo("INICIS_12");
        assertThat(InicisPayoutFailureClassifier.payResultFailCode(" 99 ")).isEqualTo("INICIS_99");
        assertThat(InicisPayoutFailureClassifier.payResultFailCode(null)).isEqualTo("INICIS_UNKNOWN");
    }

    @Test
    @DisplayName("서브몰 등록 거절 — 기본 PARTNER_NOT_READY(배치 앞단 재시도 대상) · 예금주 · 계좌 · 은행 낱말은 계좌 사유")
    void subMallFailCode() {
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("ZZ01", "처리 실패"))
                .isEqualTo(PayoutBlockReason.PARTNER_NOT_READY.name())
                .isIn(PayoutBlockReason.PARTNER_CODES);
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EA20", "예금주가 일치하지 않습니다"))
                .isEqualTo(PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name());
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EA21", "유효하지 않은 계좌")).isEqualTo("BANK_REJECTED");
        assertThat(InicisPayoutFailureClassifier.subMallFailCode("EA22", "지원하지 않는 은행"))
                .isEqualTo(PayoutBlockReason.UNSUPPORTED_BANK.name());
    }

    @Test
    @DisplayName("「이미 등록」 · 잔액 부족 낱말 판정 — 코드표 전까지 메시지로")
    void heuristics() {
        assertThat(InicisPayoutFailureClassifier.isAlreadyRegistered("EA30", "이미 등록된 서브몰입니다")).isTrue();
        assertThat(InicisPayoutFailureClassifier.isAlreadyRegistered("EA31", "필수값 누락")).isFalse();
        assertThat(InicisPayoutFailureClassifier.isInsufficientBalance("EB20", "지급 가능 잔액 부족")).isTrue();
        assertThat(InicisPayoutFailureClassifier.isInsufficientBalance("EB21", "지급일 형식 오류")).isFalse();
    }
}

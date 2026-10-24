package showroomz.global.payment.settlement.toss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerStatus;
import showroomz.domain.settlement.type.PayoutBlockReason;

import static org.assertj.core.api.Assertions.assertThat;

/** 토스 실패 코드 → fail_code(토스 설계서 5-5) · 셀러 상태 → 우리 상태(0-8). */
@DisplayName("토스 지급대행 실패 분류")
class TossPayoutFailureClassifierTest {

    @Test
    @DisplayName("셀러 등록 · 수정 — 예금주 · 사업자번호 · 은행 검증 · 수정 불가 · 계약 · 모르는 코드")
    void sellerCodes() {
        assertThat(TossPayoutFailureClassifier.sellerFailCode("INVALID_ACCOUNT_INFO")).isEqualTo("ACCOUNT_HOLDER_MISMATCH");
        assertThat(TossPayoutFailureClassifier.sellerFailCode("NOT_SELLER_ACCOUNT")).isEqualTo("ACCOUNT_HOLDER_MISMATCH");
        assertThat(TossPayoutFailureClassifier.sellerFailCode("BANK_ACCOUNT_VERIFICATION_FAIL")).isEqualTo("BANK_REJECTED");
        assertThat(TossPayoutFailureClassifier.sellerFailCode("INVALID_BUSINESS_NUMBER")).isEqualTo("COMPANY_NOT_IN_BUSINESS");
        assertThat(TossPayoutFailureClassifier.sellerFailCode("FORBIDDEN_SELLER_MODIFY")).isEqualTo("ACCOUNT_SYNC_FAILED");
        assertThat(TossPayoutFailureClassifier.sellerFailCode("FORBIDDEN_REQUEST")).isEqualTo("PAYOUT_CONTRACT_BLOCKED");
        assertThat(TossPayoutFailureClassifier.sellerFailCode("WHATEVER")).isEqualTo("PARTNER_NOT_READY");
        assertThat(TossPayoutFailureClassifier.sellerFailCode(null)).isEqualTo("PARTNER_NOT_READY");
    }

    @Test
    @DisplayName("지급 요청 동기 거절 — 셀러 없음 · 본인인증 · 잔액 · 계약 · 날짜(우리 버그) · 모르는 코드")
    void payoutRequestCodes() {
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("NOT_FOUND_SELLER")).isEqualTo("PARTNER_NOT_READY");
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("INVALID_SELLER_INFO")).isEqualTo("PARTNER_NOT_READY");
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("FORBIDDEN_SELLER_PAYOUT")).isEqualTo("IDENTITY_VERIFICATION_REQUIRED");
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("EXCEEDED_PAYOUT_BALANCE_AMOUNT")).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("FORBIDDEN_OVERDUE_PAYOUT_FEE")).isEqualTo("PAYOUT_CONTRACT_BLOCKED");
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("FORBIDDEN_ACCOUNT_SUSPENDED")).isEqualTo("PAYOUT_CONTRACT_BLOCKED");
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("INVALID_PAYOUT_DATE")).isEqualTo("REJECTED_BY_PG");
        assertThat(TossPayoutFailureClassifier.payoutRequestFailCode("INVALID_ENCRYPTION")).isEqualTo("REJECTED_BY_PG");
    }

    @Test
    @DisplayName("지급 결과 error — 예금주 · 셀러/KYC · 계약 · 그 밖은 은행 거절(기본 대응 = 계좌 변경 뒤 재분배)")
    void payoutResultCodes() {
        assertThat(TossPayoutFailureClassifier.payoutResultFailCode("INVALID_ACCOUNT_INFO", null)).isEqualTo("ACCOUNT_HOLDER_MISMATCH");
        assertThat(TossPayoutFailureClassifier.payoutResultFailCode("X", "예금주가 다릅니다")).isEqualTo("ACCOUNT_HOLDER_MISMATCH");
        assertThat(TossPayoutFailureClassifier.payoutResultFailCode("FORBIDDEN_SELLER_PAYOUT", null)).isEqualTo("PARTNER_NOT_READY");
        assertThat(TossPayoutFailureClassifier.payoutResultFailCode("FORBIDDEN_REQUEST", null)).isEqualTo("PAYOUT_CONTRACT_BLOCKED");
        assertThat(TossPayoutFailureClassifier.payoutResultFailCode("BANK_MAINTENANCE", "원천사 점검")).isEqualTo("BANK_REJECTED");
        assertThat(TossPayoutFailureClassifier.payoutResultFailCode(null, null)).isEqualTo("BANK_REJECTED");
    }

    @Test
    @DisplayName("셀러 상태 — PARTIALLY_APPROVED · APPROVED 는 지급 가능 · APPROVAL_REQUIRED · KYC_REQUIRED 는 대기(보류 사유 각각) · 모르면 거절")
    void sellerStatus() {
        assertThat(TossPayoutFailureClassifier.partnerStatusOf("APPROVED")).isEqualTo(PartnerStatus.APPROVED);
        assertThat(TossPayoutFailureClassifier.partnerStatusOf("PARTIALLY_APPROVED")).isEqualTo(PartnerStatus.APPROVED);
        assertThat(TossPayoutFailureClassifier.partnerStatusOf("APPROVAL_REQUIRED")).isEqualTo(PartnerStatus.PENDING);
        assertThat(TossPayoutFailureClassifier.partnerStatusOf("KYC_REQUIRED")).isEqualTo(PartnerStatus.PENDING);
        assertThat(TossPayoutFailureClassifier.partnerStatusOf("SOMETHING")).isEqualTo(PartnerStatus.REJECTED);
        assertThat(TossPayoutFailureClassifier.partnerStatusOf(null)).isEqualTo(PartnerStatus.FAILED);
        assertThat(TossPayoutFailureClassifier.blockCodeOf("KYC_REQUIRED")).isEqualTo(PayoutBlockReason.KYC_REQUIRED.name());
        assertThat(TossPayoutFailureClassifier.blockCodeOf("APPROVAL_REQUIRED")).isEqualTo(PayoutBlockReason.IDENTITY_VERIFICATION_REQUIRED.name());
    }

    @Test
    @DisplayName("결과 모름 판정 — 5xx · 429 · 409 · 처리 중 코드")
    void unknownOutcome() {
        assertThat(TossPayoutFailureClassifier.isUnknownOutcome(500, "COMMON_ERROR")).isTrue();
        assertThat(TossPayoutFailureClassifier.isUnknownOutcome(429, null)).isTrue();
        assertThat(TossPayoutFailureClassifier.isUnknownOutcome(409, "IDEMPOTENT_REQUEST_PROCESSING")).isTrue();
        assertThat(TossPayoutFailureClassifier.isUnknownOutcome(400, "PREVIOUS_REQUEST_IN_PROGRESS")).isTrue();
        assertThat(TossPayoutFailureClassifier.isUnknownOutcome(400, "EXCEED_MAXIMUM_REQUESTS")).isTrue();
        assertThat(TossPayoutFailureClassifier.isUnknownOutcome(400, "INVALID_REQUEST")).isFalse();
        assertThat(TossPayoutFailureClassifier.isUnknownOutcome(403, "FORBIDDEN_REQUEST")).isFalse();
    }
}

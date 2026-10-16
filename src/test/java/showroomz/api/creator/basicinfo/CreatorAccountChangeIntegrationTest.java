package showroomz.api.creator.basicinfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorSettlementAccountHistoryRepository;
import showroomz.domain.member.creator.type.CreatorAccountVerificationStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 정산 계좌 변경 · 실명 확인 · 지급 보류(ui-studio-06 T2 · T3 · T6 · 50 설계서 6절 · 14절 AV). 실명 확인은 PG 셀러 계좌 검증이다 —
 * {@code FakeSettlementPartnerGateway} 로 승인 · 실패 · 장애를 심는다. 확인은 커밋 직후 같은 스레드에서 돈다(테스트에서 바로 보인다).
 */
@DisplayName("스튜디오 정산 계좌 변경 — 실명 확인 · 확인 중 지급 보류 · 해제 · 실패 · 재시도")
class CreatorAccountChangeIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";
    private static final String NEW_ACCOUNT = "1101234567";

    @Autowired private CreatorSettlementAccountHistoryRepository accountHistories;

    @BeforeEach
    void setUpBanks() {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        registerSellerAccount();
        if (!bankRepository.existsById("088")) {
            fixture.createBank("088", "신한은행");
        }
        if (!bankRepository.existsById("004")) {
            fixture.createBank("004", "국민은행");
        }
    }

    @Test
    @DisplayName("[AV-01] 계좌 변경 — 응답은 「확인 중」 · 커밋 뒤 PG 검증 통과로 「실명 확인 완료」 · 계좌 이력 · 셀러 호출은 새 계좌 · 실명 예금주")
    void changeAndVerify() throws Exception {
        change("088", NEW_ACCOUNT).andExpect(status().isOk())
                .andExpect(jsonPath("$.account.bankName").value("신한은행"))
                .andExpect(jsonPath("$.account.accountNumber").value("****234567"))
                .andExpect(jsonPath("$.account.verification.status").value("VERIFYING"));

        creatorGet(BASE + "/settlement").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.verification.status").value("VERIFIED"))
                .andExpect(jsonPath("$.account.verification.label").value("실명 확인 완료"))
                .andExpect(jsonPath("$.account.verification.verifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.account.bankbook.fileName").value("통장사본_신한은행.jpg"))
                .andExpect(jsonPath("$.account.bankbook.submittedAt").isNotEmpty());
        assertThat(accountHistories.findFirstByCreatorIdOrderByChangedAtDescIdDesc(creator.getId())).isPresent();
        assertThat(partnerGateway.calls(SettlementPayee.CREATOR)).last()
                .satisfies(profile -> {
                    assertThat(profile.account().accountNumber()).isEqualTo(NEW_ACCOUNT);
                    assertThat(profile.account().holder()).isEqualTo("김지민");
                });
    }

    @Test
    @DisplayName("[AV-02 · AV-04] 지급 예정 행 — 예금주 불일치면 보류(정산 계좌 확인 중) 유지 · 배치도 풀지 않음 → 다른 계좌로 확인되면 오늘 이후 예정으로 풀린다")
    void scheduledPayoutHeldThenReleased() throws Exception {
        Settlement s = scheduledSettlement("가을 공구");
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        SettlementPayout held = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(held.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(held.getFailCode()).isEqualTo(PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED.name());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        creatorGet(BASE + "/settlement")
                .andExpect(jsonPath("$.account.verification.status").value("FAILED"))
                .andExpect(jsonPath("$.account.verification.failReason").value("예금주 불일치"));

        payoutService.releaseBlocked(LocalDate.now());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        change("088", "1101234568").andExpect(status().isOk());

        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getFailCode()).isNull();
        assertThat(released.getDueDate()).isAfterOrEqualTo(LocalDate.now());
        assertThat(settlementEvents(s.getId()))
                .contains(SettlementEventType.PAYOUT_BLOCKED, SettlementEventType.PAYOUT_HOLD_RELEASED);
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("[AV-05] PG 장애 — 「확인 중」으로 남고 보류 유지 → 지급 배치 앞단 재시도로 확인 · 보류 해제")
    void retryOnPartnerOutage() throws Exception {
        Settlement s = scheduledSettlement("가을 공구");
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 호출 실패");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFYING);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        payoutService.releaseBlocked(LocalDate.now());

        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[AV-03 · AV-09] 영향 — 예정 · 보류 건수와 합계 · 지시 나간 건은 따로 · 확정 전 제외 · 지시 나간 행은 변경해도 그대로")
    void changeImpact() throws Exception {
        Settlement scheduled = scheduledSettlement("가을 공구");
        Settlement requested = scheduledSettlement("겨울 공구");
        jdbc.update("UPDATE settlement_payout SET status = 'REQUESTED' WHERE settlement_id = ? AND payee = 'CREATOR'",
                requested.getId());
        seedSettlement("봄 공구", 500_000, SettlementStatus.REVIEWING);
        long amount = payout(scheduled.getId(), SettlementPayee.CREATOR).getAmount();

        creatorGet(BASE + "/settlement-account/change-impact").andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingPayoutCount").value(1))
                .andExpect(jsonPath("$.pendingPayoutAmount").value(amount))
                .andExpect(jsonPath("$.inFlightPayoutCount").value(1));

        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치");
        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        assertThat(payout(requested.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
    }

    @Test
    @DisplayName("[AV-07] 사업자 · 세금계산서 확인 전 — 계좌가 확인돼도 그 사유로 보류가 남는다(계좌 사유만 지운다)")
    void otherHoldReasonRemains() throws Exception {
        makeCreatorBusiness();
        Settlement s = scheduledSettlement("가을 공구");
        jdbc.update("UPDATE settlement SET creator_business_type = 'BUSINESS' WHERE settlement_id = ?", s.getId());

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        SettlementPayout row = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(row.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(row.getFailCode()).isNull();
    }

    @Test
    @DisplayName("[AV-08] 같은 계좌 400 · 하이픈 400 · 통장 사본 누락 400 · 없는 은행 404")
    void validation() throws Exception {
        change("004", "123456789012").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_ACCOUNT_UNCHANGED"));
        change("088", "110-123-4567").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("계좌번호는 10~16자리 숫자로 입력해 주세요."));
        Map<String, Object> noBankbook = body("088", NEW_ACCOUNT);
        noBankbook.remove("bankbookImageUrl");
        mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(noBankbook)))
                .andExpect(status().isBadRequest());
        change("999", NEW_ACCOUNT).andExpect(status().isNotFound());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.UNVERIFIED);
    }

    // ------------------------------------------------------------------ helpers

    /** 인플루언서 몫이 지시 전 예정(SCHEDULED)인 확정 정산. */
    private Settlement scheduledSettlement(String title) {
        Settlement s = seedSettlement(title, 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), s.getId());
        return s;
    }

    private ResultActions change(String bankCode, String accountNumber) throws Exception {
        return mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body(bankCode, accountNumber))));
    }

    private Map<String, Object> body(String bankCode, String accountNumber) {
        Map<String, Object> body = new HashMap<>();
        body.put("bankCode", bankCode);
        body.put("accountNumber", accountNumber);
        body.put("bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg");
        body.put("bankbookFileName", "통장사본_신한은행.jpg");
        return body;
    }

    private Creator creator() {
        return creatorRepository.findById(creator.getId()).orElseThrow();
    }
}

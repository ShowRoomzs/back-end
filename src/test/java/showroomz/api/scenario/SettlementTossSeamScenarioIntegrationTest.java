package showroomz.api.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.common.settlement.FakeSettlementPayoutGateway;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerProfile;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLine;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.payment.settlement.portone.PortOneFailureClassifier;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포트원 파트너 정산 이음새(44_포트원_파트너정산_연동_BE_설계서.md 8-1) — 포트원은 가짜({@code FakeSettlementPartnerGateway} ·
 * {@code FakeSettlementPayoutGateway} 의 REQUESTED 모드)다. 확인하는 것은 우리 쪽 상태 전이 · 식별자 · 재시도 · 사람을 부르는 자리다.
 *
 * <p>① 확정 → 파트너 보장 · 예금주 불일치 → 브랜드 행 보류 · 레일 · 재판정으로 해제 ② 지시 REQUESTED(정산건 id) → 결과 조회로 PAID →
 * 공구 SETTLED ③ 결과 FAILED → M3 → 새 정산건 id(회차) ④ 결과 조회 통신 실패 → REQUESTED 유지 → 다음 틱에 닫힘 ⑤ 지급액 불일치 →
 * PAID 아님 · 사람 호출 1회 ⑥ 정산계좌 변경 승인 → 파트너 계좌 즉시 갱신 ⑦ 지급 미실행 → 지급 시각 틱에 알림.
 */
@DisplayName("[시나리오 PO] 포트원 파트너 정산 이음새 — 파트너 보장 · 정산건 · 결과 조회 · 재분배")
class SettlementPortOneSeamScenarioIntegrationTest extends SettlementTestSupport {

    private static final String ADMIN = "/v1/admin/settlements";

    // ================================================================== ①

    @Test
    @DisplayName("PO-1 확정 → 두 수취자 파트너 보장 · 브랜드 예금주 불일치 → 브랜드 행만 BLOCKED(사유) · 레일 · 이력 · 계좌 바로잡은 뒤 배치 앞단 재판정 → SCHEDULED")
    void partnerBlockedThenReleased() throws Exception {
        readyToPayWithBanks();
        partnerGateway.failFor(SettlementPayee.BRAND, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치 — 은행 「글로우」");
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = confirm(generate(LocalDateTime.now().withNano(0)));

        // 확정은 유지 · 두 수취자 모두 호출 · 프로필은 회원 정보 그대로(은행 코드 · 계좌 · 파트너 id 규칙).
        assertThat(s.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(partnerGateway.calls()).extracting(PartnerProfile::payee)
                .containsExactly(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        PartnerProfile brandProfile = partnerGateway.calls(SettlementPayee.BRAND).get(0);
        assertThat(brandProfile.partnerId()).isEqualTo("brand-" + brand.marketId());
        assertThat(brandProfile.existing()).isFalse();
        assertThat(brandProfile.account().bankCode()).isEqualTo("088");
        assertThat(brandProfile.account().accountNumber()).isEqualTo("110123456789");
        assertThat(brandProfile.business()).isNotNull();
        PartnerProfile creatorProfile = partnerGateway.calls(SettlementPayee.CREATOR).get(0);
        assertThat(creatorProfile.partnerId()).isEqualTo("creator-" + creator.getId());
        assertThat(creatorProfile.business()).isNull();   // 비사업자 — 원천징수 대상자

        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(brandRow.getFailCode()).isEqualTo(PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name());
        assertThat(brandRow.getDueDate()).isNull();
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_BLOCKED);
        // 승인된 인플루언서는 연결이 남고, 실패한 브랜드는 연결이 없다.
        assertThat(jdbc.queryForObject("SELECT portone_partner_status FROM creator WHERE creator_id = ?", String.class,
                creator.getId())).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT portone_partner_id FROM seller WHERE seller_id = ?", String.class,
                brand.seller().getId())).isNull();
        adminGet(ADMIN + "/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.blockReasons[?(@.code == 'ACCOUNT_HOLDER_MISMATCH')]").isNotEmpty());

        // 지급일 배치 앞단 — 파트너 사유 행을 다시 등록해 본다. 계좌가 맞으면 지급 예정으로.
        LocalDate today = LocalDate.now();
        assertThat(payoutService.releaseBlocked(today)).isZero();   // 아직 예금주 불일치
        partnerGateway.approveFor(SettlementPayee.BRAND);
        assertThat(payoutService.releaseBlocked(today)).isEqualTo(1);
        SettlementPayout released = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getFailCode()).isNull();
        assertThat(released.getDueDate()).isEqualTo(s.getPayoutDueDate().isBefore(today) ? today : s.getPayoutDueDate());
        assertThat(jdbc.queryForObject("SELECT portone_partner_id FROM seller WHERE seller_id = ?", String.class,
                brand.seller().getId())).isEqualTo("brand-" + brand.marketId());
    }

    // ================================================================== ②

    @Test
    @DisplayName("PO-2 지시 → 브랜드 · 인플루언서 REQUESTED + 정산건 id · 플랫폼 PAID → 결과 조회(지급 전) 유지 → 지급 완료 조회 → PAID · 지급 id · 공구 SETTLED")
    void requestedThenPaidByLookup() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();

        Settlement afterPay = pay(s, s.getPayoutDueDate());
        assertThat(afterPay.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(brandRow.getPgTransferId())
                .isEqualTo(FakeSettlementPayoutGateway.transferId(s.getSettlementNumber(), SettlementPayee.BRAND, 0));
        assertThat(brandRow.getPgPayoutId()).isNull();
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.PAID);
        // 지시 명령에는 수취자 식별자(파트너 id 산출용)와 회차가 실린다.
        assertThat(payoutGateway.calls()).singleElement().satisfies(call -> assertThat(call.lines())
                .extracting(PayoutLine::payee, PayoutLine::payeeRefId, PayoutLine::attempt)
                .contains(tuple(SettlementPayee.BRAND, brand.marketId(), 0), tuple(SettlementPayee.CREATOR, creator.getId(), 0)));
        payoutService.notifyReadyForExecution(LocalDate.now());   // 대조 알림 — 예외 없이 끝난다

        // 결과 조회 — 아직 콘솔 실행 전: REQUESTED 유지.
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payoutGateway.lookups()).hasSize(1);

        // 지급 완료 — 수취자별 지급 id 가 참조번호로 남고 정산 PAID · 공구 SETTLED.
        payoutGateway.completeFor(SettlementPayee.BRAND, "PO-BRAND-1");
        payoutGateway.completeFor(SettlementPayee.CREATOR, "PO-CREATOR-1");
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        SettlementPayout paid = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(paid.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(paid.getPgPayoutId()).isEqualTo("PO-BRAND-1");
        assertThat(paid.getPgReference()).isEqualTo("PO-BRAND-1");
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_REQUESTED, SettlementEventType.PAYOUT_PAID);
        sellerGet("/v1/seller/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.pgReference").value("PO-BRAND-1"));
    }

    // ================================================================== ③

    @Test
    @DisplayName("PO-3 결과 조회 FAILED(은행 거절) → PAYOUT_FAILED → M3 회원 정보 계좌 → 새 정산건 id(회차 1) · 수취자 식별자 그대로")
    void lookupFailedThenRedistributed() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());

        payoutGateway.failLookupFor(SettlementPayee.CREATOR, PortOneFailureClassifier.BANK_REJECTED, "계좌 해지");
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        SettlementPayout failed = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(failed.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo(PortOneFailureClassifier.BANK_REJECTED);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);

        adminPost(ADMIN + "/" + s.getId() + "/payouts/" + failed.getId() + "/redistribute",
                Map.of("accountSource", "CURRENT_PROFILE")).andExpect(status().isOk());
        SettlementPayout retried = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(retried.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(retried.getAttempt()).isEqualTo(1);
        assertThat(retried.getPgTransferId())
                .isEqualTo(FakeSettlementPayoutGateway.transferId(s.getSettlementNumber(), SettlementPayee.CREATOR, 1));
        assertThat(payoutGateway.calls()).hasSize(2);
        assertThat(payoutGateway.calls().get(1).lines()).singleElement()
                .extracting(PayoutLine::payee, PayoutLine::payeeRefId, PayoutLine::attempt)
                .containsExactly(SettlementPayee.CREATOR, creator.getId(), 1);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
    }

    // ================================================================== ④

    @Test
    @DisplayName("PO-4 결과 조회 통신 실패 → 예외 없이 REQUESTED 유지 → 다음 틱에 닫힘")
    void lookupUnresponsiveThenClosed() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND);
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());

        payoutGateway.lookupUnresponsive();
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);

        payoutGateway.reset();
        payoutGateway.completeFor(SettlementPayee.BRAND, "PO-BRAND-2");
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    // ================================================================== ⑤

    @Test
    @DisplayName("PO-5 지급액 불일치 → PAID 로 닫지 않고 REQUESTED + 사유 · 「지급 확인 필요」 이력은 한 번만")
    void amountMismatchKeepsRequested() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND);
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());

        payoutGateway.flagLookupFor(SettlementPayee.BRAND, PortOneFailureClassifier.AMOUNT_MISMATCH, "지급액 35,000 ≠ 행 35,180");
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        SettlementPayout row = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(row.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(row.getFailCode()).isEqualTo(PortOneFailureClassifier.AMOUNT_MISMATCH);
        assertThat(settlementEvents(s.getId()).stream().filter(e -> e == SettlementEventType.PAYOUT_CHECK_REQUIRED)).hasSize(1);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
    }

    // ================================================================== ⑥

    @Test
    @DisplayName("PO-6 정산계좌 변경 요청 승인 → 연결된 브랜드 파트너의 계좌를 즉시 갱신(existing · 새 계좌) · 연결 전이면 호출 없음")
    void brandAccountChangeSyncsPartner() throws Exception {
        readyToPayWithBanks();
        // 연결 전 — 변경 승인은 파트너를 부르지 않는다.
        approveAccountChange("004", "9876543210", "글로우랩 주식회사");
        assertThat(partnerGateway.calls()).isEmpty();
        // 확정이 연결을 만든다.
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        confirm(generate(LocalDateTime.now().withNano(0)));
        assertThat(partnerGateway.calls(SettlementPayee.BRAND)).hasSize(1);
        assertThat(partnerGateway.calls(SettlementPayee.BRAND).get(0).account().accountNumber()).isEqualTo("9876543210");

        // 연결 뒤 — 승인 즉시 갱신(existing=true · 새 계좌).
        approveAccountChange("088", "110555666777", "글로우랩");
        assertThat(partnerGateway.calls(SettlementPayee.BRAND)).hasSize(2);
        PartnerProfile updated = partnerGateway.calls(SettlementPayee.BRAND).get(1);
        assertThat(updated.existing()).isTrue();
        assertThat(updated.account().bankCode()).isEqualTo("088");
        assertThat(updated.account().accountNumber()).isEqualTo("110555666777");
        assertThat(updated.account().holder()).isEqualTo("글로우랩");
    }

    // ================================================================== ⑦

    @Test
    @DisplayName("PO-7 지시 뒤 1영업일이 지나도 PG 지급이 없으면 지급 시각 틱에 「지급 미실행」 이력 · 알림(다른 틱에는 없음)")
    void executionOverdue() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND);
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());
        jdbc.update("UPDATE settlement_payout SET requested_at = ? WHERE settlement_id = ? AND payee = 'BRAND'",
                Timestamp.valueOf(LocalDateTime.now().minusDays(5)), s.getId());

        payoutService.pollResults(LocalDate.now().atTime(14, 0));
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_CHECK_REQUIRED);
        payoutService.pollResults(LocalDate.now().atTime(10, 5));
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_CHECK_REQUIRED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
    }

    // ------------------------------------------------------------------ 보조

    /** 구매확정 1건 · 공구 종료 · 생성 · 확정 — 지급 예정. */
    private Settlement scheduledSettlement() {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        return confirm(generate(LocalDateTime.now().withNano(0)));
    }

    /** 세 수취자 모두 지시 가능 + 은행 코드 역조회가 되게 은행 둘. */
    private void readyToPayWithBanks() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        fixture.createBank("004", "KB국민은행");
        fixture.createBank("088", "신한은행");
    }

    private void approveAccountChange(String bankCode, String accountNumber, String holder) throws Exception {
        long requestId = changeRequests.createSettlement(brandToken, bankCode, accountNumber, holder);
        mockMvc.perform(post("/v1/admin/change-requests/" + requestId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}

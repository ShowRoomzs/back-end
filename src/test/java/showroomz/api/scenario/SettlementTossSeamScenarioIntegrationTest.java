package showroomz.api.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.common.settlement.FakeSettlementPartnerGateway;
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
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.payment.settlement.toss.TossPayoutFailureClassifier;
import showroomz.global.payment.settlement.toss.TossPayoutsWebhookVerifier;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토스 지급대행 이음새(44_토스페이먼츠_지급대행_연동_BE_설계서.md 8-1) — 토스는 가짜({@code FakeSettlementPartnerGateway} ·
 * {@code FakeSettlementPayoutGateway} 의 REQUESTED 모드)다. 확인하는 것은 우리 쪽 상태 전이 · 식별자 · 웹훅 · 재시도 · 사람을 부르는 자리다.
 *
 * <p>TP-1 확정 → 개인 수취자 본인인증 대기 → BLOCKED · 수취자 알림 · TP-2 {@code seller.changed} 웹훅으로 즉시 해제 · TP-3 지시 REQUESTED(참조 id ·
 * PG 지급 id · EXPRESS) · TP-4 {@code payout.changed(COMPLETED)} 웹훅 → PAID · 공구 SETTLED · 멱등 · TP-5 웹훅 유실 → 폴링 · TP-6 FAILED → M3 →
 * 새 참조 id · TP-7 잔액 부족 → 연기 · TP-8 주당 한도 → KYC 보류 · TP-9 서명 틀린 웹훅 401 · TP-10 결과 모름 → 폴링 복구 · 지연 알림.
 */
@DisplayName("[시나리오 TP] 토스 지급대행 이음새 — 셀러 보장 · 지급 요청 · 웹훅 · 폴링 · 재분배")
class SettlementTossSeamScenarioIntegrationTest extends SettlementTestSupport {

    private static final String ADMIN = "/v1/admin/settlements";
    private static final String WEBHOOK = "/v1/webhooks/toss/payouts";

    @Autowired TossPayoutsProperties tossProperties;

    // ================================================================== TP-1 · TP-2

    @Test
    @DisplayName("TP-1 확정 → 두 수취자 셀러 보장 · 개인 인플루언서 APPROVAL_REQUIRED → 인플루언서 행만 BLOCKED(본인인증 대기) · 연결(id · ref · env · 원시 상태)은 남는다 · 레일")
    void identityVerificationBlocks() throws Exception {
        readyToPayWithBanks();
        partnerGateway.pendingFor(SettlementPayee.CREATOR, FakeSettlementPartnerGateway.APPROVAL_REQUIRED);
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = confirm(generate(LocalDateTime.now().withNano(0)));

        assertThat(s.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(partnerGateway.calls()).extracting(PartnerProfile::payee)
                .containsExactly(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        PartnerProfile brandProfile = partnerGateway.calls(SettlementPayee.BRAND).get(0);
        assertThat(brandProfile.partnerRef()).isEqualTo("brand-" + brand.marketId());
        assertThat(brandProfile.existing()).isFalse();
        assertThat(brandProfile.externalId()).isNull();
        assertThat(brandProfile.account().bankCode()).isEqualTo("088");
        assertThat(brandProfile.business()).isNotNull();
        PartnerProfile creatorProfile = partnerGateway.calls(SettlementPayee.CREATOR).get(0);
        assertThat(creatorProfile.partnerRef()).isEqualTo("creator-" + creator.getId());
        assertThat(creatorProfile.business()).isNull();   // 비사업자 → INDIVIDUAL

        SettlementPayout creatorRow = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(creatorRow.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(creatorRow.getFailCode()).isEqualTo(PayoutBlockReason.IDENTITY_VERIFICATION_REQUIRED.name());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_BLOCKED);
        // 등록은 됐다 — 발급 id · 참조 · 환경 · 원시 상태가 남는다(승인 때만 계좌 지문).
        Map<String, Object> creatorLink = jdbc.queryForMap("SELECT pg_partner_id, pg_partner_ref, pg_partner_env, pg_partner_status, "
                + "pg_partner_account_hash FROM creator WHERE creator_id = ?", creator.getId());
        assertThat(creatorLink.get("pg_partner_id")).isEqualTo("FAKE-SELLER-creator-" + creator.getId());
        assertThat(creatorLink.get("pg_partner_ref")).isEqualTo("creator-" + creator.getId());
        assertThat(creatorLink.get("pg_partner_env")).isEqualTo("TEST");
        assertThat(creatorLink.get("pg_partner_status")).isEqualTo(FakeSettlementPartnerGateway.APPROVAL_REQUIRED);
        assertThat(creatorLink.get("pg_partner_account_hash")).isNull();
        Map<String, Object> brandLink = jdbc.queryForMap("SELECT pg_partner_id, pg_partner_status, pg_partner_account_hash "
                + "FROM seller WHERE seller_id = ?", brand.seller().getId());
        assertThat(brandLink.get("pg_partner_id")).isEqualTo("FAKE-SELLER-brand-" + brand.marketId());
        assertThat(brandLink.get("pg_partner_status")).isEqualTo(FakeSettlementPartnerGateway.PARTIALLY_APPROVED);
        assertThat(brandLink.get("pg_partner_account_hash")).isNotNull();
        adminGet(ADMIN + "/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.blockReasons[?(@.code == 'IDENTITY_VERIFICATION_REQUIRED')]").isNotEmpty());

        // 배치 앞단 재판정 — 아직 대기면 그대로(사유 유지). 승인되면 지급 예정으로.
        LocalDate today = LocalDate.now();
        assertThat(payoutService.releaseBlocked(today)).isZero();
        partnerGateway.approveFor(SettlementPayee.CREATOR);
        assertThat(payoutService.releaseBlocked(today)).isEqualTo(1);
        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getFailCode()).isNull();
        assertThat(released.getDueDate()).isEqualTo(s.getPayoutDueDate().isBefore(today) ? today : s.getPayoutDueDate());
    }

    @Test
    @DisplayName("TP-2 seller.changed(PARTIALLY_APPROVED) 웹훅 → 배치를 기다리지 않고 즉시 SCHEDULED · 원시 상태 갱신 · 같은 eventId 재수신은 무시 · KYC_REQUIRED 로 바뀌면 예정 행이 다시 보류")
    void sellerChangedWebhookReleasesAndBlocks() throws Exception {
        readyToPayWithBanks();
        partnerGateway.pendingFor(SettlementPayee.CREATOR, FakeSettlementPartnerGateway.APPROVAL_REQUIRED);
        Settlement s = scheduledSettlement();
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        String sellerId = "FAKE-SELLER-creator-" + creator.getId();

        String eventId = UUID.randomUUID().toString();
        webhook(sellerChanged(eventId, sellerId, FakeSettlementPartnerGateway.PARTIALLY_APPROVED)).andExpect(status().isOk());
        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getDueDate()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT pg_partner_status FROM creator WHERE creator_id = ?", String.class, creator.getId()))
                .isEqualTo(FakeSettlementPartnerGateway.PARTIALLY_APPROVED);
        assertThat(jdbc.queryForObject("SELECT result FROM settlement_payout_webhook_event WHERE event_id = ?", String.class, eventId))
                .isEqualTo("APPLIED");
        // 재수신 — 200 · 처리 1회.
        webhook(sellerChanged(eventId, sellerId, FakeSettlementPartnerGateway.PARTIALLY_APPROVED)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_payout_webhook_event WHERE event_id = ?", Integer.class, eventId))
                .isEqualTo(1);

        // KYC 로 넘어가면 — 예정 행이 보류로 · 수취자 알림.
        webhook(sellerChanged(UUID.randomUUID().toString(), sellerId, FakeSettlementPartnerGateway.KYC_REQUIRED)).andExpect(status().isOk());
        SettlementPayout blocked = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(blocked.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(blocked.getFailCode()).isEqualTo(PayoutBlockReason.KYC_REQUIRED.name());
        // 모르는 셀러 — 200(IGNORED) · DB 변화 없음.
        String unknown = UUID.randomUUID().toString();
        webhook(sellerChanged(unknown, "NOBODY", FakeSettlementPartnerGateway.APPROVED)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT result FROM settlement_payout_webhook_event WHERE event_id = ?", String.class, unknown))
                .isEqualTo("IGNORED");
    }

    // ================================================================== TP-3 · TP-4 · TP-5

    @Test
    @DisplayName("TP-3 지시 → 브랜드 · 인플루언서 REQUESTED + 참조 id + PG 지급 id(참조번호) + EXPRESS · 플랫폼 PAID · 지시 명령에 셀러 id · 회차 · 대사 요약")
    void requestedWithPayoutId() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();

        Settlement afterPay = pay(s, s.getPayoutDueDate());
        assertThat(afterPay.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        String ref = FakeSettlementPayoutGateway.payoutRef(s.getSettlementNumber(), SettlementPayee.BRAND, 0);
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(brandRow.getPgTransferId()).isEqualTo(ref);
        assertThat(brandRow.getPgPayoutId()).isEqualTo(FakeSettlementPayoutGateway.payoutId(ref));
        assertThat(brandRow.getPgReference()).isEqualTo(FakeSettlementPayoutGateway.payoutId(ref));
        assertThat(brandRow.getPgScheduleType()).isEqualTo("EXPRESS");
        assertThat(brandRow.getPgPayoutDate()).isEqualTo(s.getPayoutDueDate());
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(payoutGateway.calls()).singleElement().satisfies(call -> {
            assertThat(call.requestedAt()).isNotNull();
            assertThat(call.lines()).extracting(PayoutLine::payee, PayoutLine::payeeRefId, PayoutLine::attempt, PayoutLine::partnerExternalId)
                    .contains(tuple(SettlementPayee.BRAND, brand.marketId(), 0, "FAKE-SELLER-brand-" + brand.marketId()),
                            tuple(SettlementPayee.CREATOR, creator.getId(), 0, "FAKE-SELLER-creator-" + creator.getId()));
        });
        payoutGateway.reconcileWith(2, brandRow.getAmount() + payout(s.getId(), SettlementPayee.CREATOR).getAmount());
        payoutService.reconcileRequested(LocalDate.now());   // 대사 요약 — 예외 없이 끝난다

        // 폴링 — 아직 처리 중: REQUESTED 유지.
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payoutGateway.lookups()).hasSize(1);
    }

    @Test
    @DisplayName("TP-4 payout.changed(COMPLETED) 웹훅 → PAID · 공구 SETTLED · 영수증 · 재수신 · 폴링이 다시 봐도 전이 없음 · 셀러 화면 참조번호")
    void completedByWebhook() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());
        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        SettlementPayout creatorRow = payout(s.getId(), SettlementPayee.CREATOR);

        String eventId = UUID.randomUUID().toString();
        webhook(payoutChanged(eventId, brandRow.getPgPayoutId(), brandRow.getPgTransferId(), "COMPLETED", brandRow.getAmount(), null))
                .andExpect(status().isOk());
        SettlementPayout paid = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(paid.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(paid.getPgReference()).isEqualTo(brandRow.getPgPayoutId());
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);   // 인플루언서 아직
        // 참조 id 로도 찾는다(지시 중 통신 실패로 id 가 비어 있던 행) — id 를 모르는 척 보낸다.
        webhook(payoutChanged(UUID.randomUUID().toString(), "UNKNOWN-ID", creatorRow.getPgTransferId(), "COMPLETED",
                creatorRow.getAmount(), null)).andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_REQUESTED, SettlementEventType.PAYOUT_PAID);

        // 재수신 · 폴링 — 전이 없음(이력 PAYOUT_PAID 는 브랜드 · 인플루언서 · 플랫폼 3회에서 늘지 않는다).
        long paidEvents = settlementEvents(s.getId()).stream().filter(e -> e == SettlementEventType.PAYOUT_PAID).count();
        assertThat(paidEvents).isEqualTo(3);
        webhook(payoutChanged(eventId, brandRow.getPgPayoutId(), brandRow.getPgTransferId(), "COMPLETED", brandRow.getAmount(), null))
                .andExpect(status().isOk());
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        assertThat(settlementEvents(s.getId()).stream().filter(e -> e == SettlementEventType.PAYOUT_PAID)).hasSize((int) paidEvents);
        sellerGet("/v1/seller/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.pgReference").value(brandRow.getPgPayoutId()));
    }

    @Test
    @DisplayName("TP-5 웹훅 유실 → 30분 폴링(백스톱)이 COMPLETED 로 닫는다 · 통신 실패 틱은 REQUESTED 유지")
    void completedByPollingBackstop() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());

        payoutGateway.lookupUnresponsive();
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);

        payoutGateway.reset();
        payoutGateway.completeFor(SettlementPayee.BRAND);
        payoutGateway.completeFor(SettlementPayee.CREATOR);
        payoutService.pollResults(LocalDateTime.now().withHour(14));
        SettlementPayout paid = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(paid.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(paid.getPgReference()).isEqualTo(paid.getPgPayoutId()).isNotNull();
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
    }

    // ================================================================== TP-6

    @Test
    @DisplayName("TP-6 payout.changed(FAILED) → PAYOUT_FAILED → M3 회원 정보 계좌 → 새 참조 id(회차 1) · 셀러 id 그대로")
    void failedThenRedistributed() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());
        SettlementPayout row = payout(s.getId(), SettlementPayee.CREATOR);

        webhook(payoutChanged(UUID.randomUUID().toString(), row.getPgPayoutId(), row.getPgTransferId(), "FAILED", row.getAmount(),
                "INVALID_ACCOUNT_INFO")).andExpect(status().isOk());
        SettlementPayout failed = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(failed.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo(TossPayoutFailureClassifier.BANK_REJECTED);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);

        adminPost(ADMIN + "/" + s.getId() + "/payouts/" + failed.getId() + "/redistribute",
                Map.of("accountSource", "CURRENT_PROFILE")).andExpect(status().isOk());
        SettlementPayout retried = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(retried.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(retried.getAttempt()).isEqualTo(1);
        assertThat(retried.getPgTransferId())
                .isEqualTo(FakeSettlementPayoutGateway.payoutRef(s.getSettlementNumber(), SettlementPayee.CREATOR, 1));
        assertThat(retried.getPgPayoutId()).isNotEqualTo(row.getPgPayoutId());
        assertThat(payoutGateway.calls()).hasSize(2);
        assertThat(payoutGateway.calls().get(1).lines()).singleElement()
                .extracting(PayoutLine::payee, PayoutLine::payeeRefId, PayoutLine::attempt, PayoutLine::partnerExternalId)
                .containsExactly(SettlementPayee.CREATOR, creator.getId(), 1, "FAKE-SELLER-creator-" + creator.getId());
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
    }

    // ================================================================== TP-7

    @Test
    @DisplayName("TP-7 잔액 선검사 — 잔액 < 합계면 지시하지 않고 다음 영업일로 연기(attempt 불변 · 이력) · 잔액이 차면 지시 · 동기 잔액 거절(DEFERRED)도 같은 결과")
    void balanceInsufficientDefers() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();
        LocalDate due = s.getPayoutDueDate();
        LocalDate next = businessCalendar.addBusinessDays(due, 1);

        payoutGateway.balance(1L);
        assertThat(payoutService.distributeDue(due, due.atTime(10, 0), 200)).isZero();
        SettlementPayout deferred = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(deferred.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(deferred.getDueDate()).isEqualTo(next);
        assertThat(deferred.getAttempt()).isZero();
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);   // 정산 단위로 미룬다
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_DEFERRED);
        assertThat(payoutGateway.calls()).isEmpty();

        payoutGateway.balance(1_000_000_000L);
        assertThat(payoutService.distributeDue(next, next.atTime(10, 0), 200)).isEqualTo(1);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);

        // 동기 거절 — 지시가 DEFERRED 로 돌아오면 되돌려 다음 영업일로.
        payoutGateway.reset();
        payoutGateway.deferFor(SettlementPayee.CREATOR);
        payoutGateway.requestedFor(SettlementPayee.BRAND);
        Settlement s2 = scheduledSettlementOnOtherGroupBuy();
        payoutService.distribute(s2.getId(), s2.getPayoutDueDate(), s2.getPayoutDueDate().atTime(10, 0));
        SettlementPayout creatorRow = payout(s2.getId(), SettlementPayee.CREATOR);
        assertThat(creatorRow.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(creatorRow.getDueDate()).isEqualTo(businessCalendar.addBusinessDays(s2.getPayoutDueDate(), 1));
        assertThat(creatorRow.getRequestedAt()).isNull();
        assertThat(creatorRow.getAttempt()).isZero();
        assertThat(payout(s2.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(settlementEvents(s2.getId())).contains(SettlementEventType.PAYOUT_DEFERRED);
    }

    // ================================================================== TP-8

    @Test
    @DisplayName("TP-8 PARTIALLY_APPROVED 수취자의 주간 누적 + 이 행 ≥ 한도 → 지시하지 않고 BLOCKED(KYC_REQUIRED) · 알림 · APPROVED 가 되면 해제")
    void weeklyLimitBlocks() throws Exception {
        readyToPayWithBanks();
        payoutGateway.requestedFor(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        Settlement s = scheduledSettlement();
        jdbc.update("UPDATE settlement_payout SET amount = ? WHERE settlement_id = ? AND payee = 'BRAND'", 10_000_000L, s.getId());

        pay(s, s.getPayoutDueDate());
        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(brandRow.getFailCode()).isEqualTo(PayoutBlockReason.KYC_REQUIRED.name());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payoutGateway.calls()).singleElement().satisfies(call -> assertThat(call.lines())
                .extracting(PayoutLine::payee).contains(SettlementPayee.CREATOR).doesNotContain(SettlementPayee.BRAND));

        // KYC 통과(APPROVED · 한도 없음) → 즉시 예정으로 → 지시된다.
        webhook(sellerChanged(UUID.randomUUID().toString(), "FAKE-SELLER-brand-" + brand.marketId(),
                FakeSettlementPartnerGateway.APPROVED)).andExpect(status().isOk());
        SettlementPayout releasedRow = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(releasedRow.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        pay(s, releasedRow.getDueDate());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
    }

    // ================================================================== TP-9

    @Test
    @DisplayName("TP-9 서명이 틀리거나 시간 헤더가 없는 웹훅 → 401 · 수신 기록 없음")
    void badSignatureRejected() throws Exception {
        String body = sellerChanged(UUID.randomUUID().toString(), "X", FakeSettlementPartnerGateway.APPROVED);
        String time = "2026-10-15T10:00:00+09:00";
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("tosspayments-webhook-transmission-time", time)
                        .header("tosspayments-webhook-signature", "v1:AAAA"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("tosspayments-webhook-signature", verifier().sign(body.getBytes(StandardCharsets.UTF_8), time)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_payout_webhook_event", Integer.class)).isZero();
    }

    // ================================================================== TP-10

    @Test
    @DisplayName("TP-10 지시 중 통신 실패(결과 모름) → REQUESTED(id 없음) → 폴링이 id 를 채워 PAID · 지급 시각 틱에 N영업일 지난 REQUESTED 는 「PG 처리 지연」 1회")
    void unknownOutcomeRecoveredByPolling() throws Exception {
        readyToPayWithBanks();
        payoutGateway.unresponsive();
        Settlement s = scheduledSettlement();
        pay(s, s.getPayoutDueDate());
        SettlementPayout row = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(row.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(row.getPgPayoutId()).isNull();
        assertThat(row.getPgTransferId()).isNull();
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.PAID);   // 내부 계정은 결과 모름이 없다

        // 지연 알림 — 지급 시각 틱에만 · 그 밖 틱에는 없다.
        jdbc.update("UPDATE settlement_payout SET requested_at = ? WHERE settlement_id = ? AND payee IN ('BRAND', 'CREATOR')",
                Timestamp.valueOf(LocalDateTime.now().minusDays(5)), s.getId());
        payoutGateway.reset();
        payoutService.pollResults(LocalDate.now().atTime(14, 0));
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_CHECK_REQUIRED);
        payoutService.pollResults(LocalDate.now().atTime(10, 5));
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_CHECK_REQUIRED);
        SettlementPayout recovered = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(recovered.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(recovered.getPgTransferId())
                .isEqualTo(FakeSettlementPayoutGateway.payoutRef(s.getSettlementNumber(), SettlementPayee.BRAND, 0));
        assertThat(recovered.getPgPayoutId()).isNotNull();   // 폴링이 목록에서 찾아 채웠다

        payoutGateway.completeFor(SettlementPayee.BRAND);
        payoutGateway.completeFor(SettlementPayee.CREATOR);
        payoutService.pollResults(LocalDate.now().atTime(14, 0));
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    // ================================================================== 계좌 변경

    @Test
    @DisplayName("정산계좌 변경 요청 승인 → 연결된 브랜드 셀러의 계좌를 즉시 갱신(existing · externalId · 새 계좌) · 연결 전이면 호출 없음")
    void brandAccountChangeSyncsSeller() throws Exception {
        readyToPayWithBanks();
        approveAccountChange("004", "9876543210", "글로우랩 주식회사");
        assertThat(partnerGateway.calls()).isEmpty();
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        confirm(generate(LocalDateTime.now().withNano(0)));
        assertThat(partnerGateway.calls(SettlementPayee.BRAND)).hasSize(1);

        approveAccountChange("088", "110555666777", "글로우랩");
        assertThat(partnerGateway.calls(SettlementPayee.BRAND)).hasSize(2);
        PartnerProfile updated = partnerGateway.calls(SettlementPayee.BRAND).get(1);
        assertThat(updated.existing()).isTrue();
        assertThat(updated.externalId()).isEqualTo("FAKE-SELLER-brand-" + brand.marketId());
        assertThat(updated.account().bankCode()).isEqualTo("088");
        assertThat(updated.account().accountNumber()).isEqualTo("110555666777");
    }

    // ------------------------------------------------------------------ 보조

    private Settlement scheduledSettlement() {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        return confirm(generate(LocalDateTime.now().withNano(0)));
    }

    /** 두 번째 정산 — 같은 브랜드 · 인플루언서의 다른 종료 공구(지급 예정까지). */
    private Settlement scheduledSettlementOnOtherGroupBuy() {
        Settlement other = seedSettlement("두 번째 공구", 50_000, SettlementStatus.PAYOUT_SCHEDULED);
        LocalDate due = LocalDate.now();
        jdbc.update("UPDATE settlement SET payout_due_date = ? WHERE settlement_id = ?", due, other.getId());
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?", due, other.getId());
        return settlement(other.getId());
    }

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

    private TossPayoutsWebhookVerifier verifier() {
        return new TossPayoutsWebhookVerifier(tossProperties.getSecurityKey());
    }

    private org.springframework.test.web.servlet.ResultActions webhook(String body) throws Exception {
        String time = "2026-10-15T10:00:00+09:00";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(bytes)
                .header("tosspayments-webhook-transmission-time", time)
                .header("tosspayments-webhook-signature", verifier().sign(bytes, time)));
    }

    private static String sellerChanged(String eventId, String sellerId, String status) {
        return """
                {"eventType":"seller.changed","createdAt":"2026-10-15T10:00:00+09:00","version":"2022-11-16","eventId":"%s",
                 "entityType":"seller","entityBody":{"id":"%s","refSellerId":"x","businessType":"INDIVIDUAL","status":"%s"}}
                """.formatted(eventId, sellerId, status);
    }

    private static String payoutChanged(String eventId, String payoutId, String refPayoutId, String status, long amount, String errorCode) {
        String error = errorCode == null ? "null" : "{\"code\":\"%s\",\"message\":\"실패\"}".formatted(errorCode);
        return """
                {"eventType":"payout.changed","createdAt":"2026-10-15T11:00:00+09:00","version":"2022-11-16","eventId":"%s",
                 "entityType":"payout","entityBody":{"id":"%s","refPayoutId":"%s","destination":"S","scheduleType":"EXPRESS",
                 "payoutDate":"2026-10-15","amount":{"currency":"KRW","value":%d},"transactionDescription":"쇼룸즈정산",
                 "requestedAt":"2026-10-15T10:00:00+09:00","status":"%s","error":%s,"metadata":null}}
                """.formatted(eventId, payoutId, refPayoutId, amount, status, error);
    }
}

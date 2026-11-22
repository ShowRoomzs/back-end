package showroomz.api.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.FakeSettlementPartnerGateway;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerProfile;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupCommand;
import showroomz.domain.settlement.service.PayoutHoldTransitions;
import showroomz.domain.settlement.service.SettlementPayoutGroupService;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.SettlementProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이니시스 지급대행 이음새(60_KG이니시스_지급대행_전환_계획서.md 9-2) — 이니시스는 가짜({@code FakeSettlementPayoutGateway} ·
 * {@code FakeSettlementPartnerGateway} 의 이니시스 모드)다. 확인하는 것은 우리 쪽 지급 묶음 · 날짜 · 합류 · 수렴 · 빼기 · 잔액 · AML · 결과 전이다.
 *
 * <p>예정일 D 의 정산을 그 전 영업일 X 에 지시한다(리드 1 · 마감 15:00). 시각은 테스트가 넘긴다.
 */
@DisplayName("[시나리오 IN] 이니시스 지급대행 이음새 — 지급 묶음 · 합류 · 수렴 · 마감 전 빼기 · 잔액 · AML · 송금 결과")
class SettlementInicisSeamScenarioIntegrationTest extends SettlementTestSupport {

    @Autowired PayoutHoldTransitions holdTransitions;
    @Autowired SettlementPayoutGroupService groupService;
    @Autowired SettlementProperties settlementProperties;

    @BeforeEach
    void inicisMode() {
        payoutGateway.inicisMode();
        partnerGateway.inicisMode();
    }

    // ================================================================== IN-1 · IN-3

    @Test
    @DisplayName("IN-1 · IN-3 예정일 D 정산 — 전전 영업일 배치는 지시 0 · X 10:00 배치 → 수취자 행 REQUESTED(지급일 D · INICIS · 묶음 키) · 묶음 OPEN(확정 합계) · 플랫폼 즉시 PAID → 송금 결과 01 → PAID(지급일) · 정산 PAID · 공구 SETTLED")
    void dayBeforeInstructionThenRemitted() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);

        assertThat(payoutService.distributeDue(businessCalendar.previousBusinessDay(x),
                businessCalendar.previousBusinessDay(x).atTime(10, 0), 200)).isZero();
        assertThat(payoutService.distributeDue(x, x.atTime(10, 0), 200)).isEqualTo(1);

        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        String idMall = "tb" + brand.marketId();
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(brandRow.getPgPayoutDate()).isEqualTo(d);
        assertThat(brandRow.getPgProvider()).isEqualTo("INICIS");
        assertThat(brandRow.getPgScheduleType()).isEqualTo("NEXT_DAY");
        assertThat(brandRow.getPgTransferId()).isEqualTo(idMall + ":" + d + ":NEXT_DAY");
        assertThat(brandRow.getPgPayoutId()).isNull();
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.PAID);
        Map<String, Object> group = group(brandRow.getPayoutGroupId());
        assertThat(group.get("status")).isEqualTo("OPEN");
        assertThat(((Number) group.get("confirmed_amount")).longValue()).isEqualTo(brandRow.getAmount());
        assertThat(group.get("pg_partner_id")).isEqualTo(idMall);
        assertThat(group.get("last_request_id")).isNotNull();
        assertThat(payoutGateway.groupCalls()).extracting(GroupCommand::payee)
                .containsExactlyInAnyOrder(SettlementPayee.BRAND, SettlementPayee.CREATOR);
        assertThat(payoutGateway.uploaded()).containsEntry(idMall + "|" + d, brandRow.getAmount());
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);

        payoutGateway.completeFor(SettlementPayee.BRAND);
        payoutGateway.completeFor(SettlementPayee.CREATOR);
        payoutService.pollResults(d.atTime(17, 0));

        SettlementPayout paid = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(paid.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(paid.getPaidAt().toLocalDate()).isEqualTo(d);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        Map<String, Object> closed = group(brandRow.getPayoutGroupId());
        assertThat(closed.get("status")).isEqualTo("PAID");
        assertThat(closed.get("remitted_on").toString()).isEqualTo(d.toString());
    }

    // ================================================================== IN-2 · IN-4

    @Test
    @DisplayName("IN-2 · IN-4 같은 브랜드 정산 2건이 같은 D — 브랜드 묶음 1개 · 전송 1회 · 합계 → 송금 실패(clPay≠01) → 두 정산의 브랜드 행 모두 FAILED · 재분배(M3)는 행마다 새 묶음")
    void twoSettlementsOneGroupThenFailed() {
        readyForInicis();
        Settlement a = scheduledSettlement();
        LocalDate d = a.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        Settlement b = secondSettlementDue(d);

        payoutService.distributeDue(x, x.atTime(10, 0), 200);

        long brandA = payout(a.getId(), SettlementPayee.BRAND).getAmount();
        long brandB = payout(b.getId(), SettlementPayee.BRAND).getAmount();
        assertThat(payout(a.getId(), SettlementPayee.BRAND).getPayoutGroupId())
                .isEqualTo(payout(b.getId(), SettlementPayee.BRAND).getPayoutGroupId());
        assertThat(payoutGateway.groupCalls(SettlementPayee.BRAND)).singleElement()
                .satisfies(c -> assertThat(c.total()).isEqualTo(brandA + brandB));

        Long oldGroup = payout(a.getId(), SettlementPayee.BRAND).getPayoutGroupId();
        payoutGateway.failLookupFor(SettlementPayee.BRAND, "INICIS_12", "이니시스 송금 실패 clPay=12");
        payoutService.pollResults(d.atTime(17, 0));

        assertThat(payout(a.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout(a.getId(), SettlementPayee.BRAND).getFailCode()).isEqualTo("INICIS_12");
        assertThat(payout(b.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(settlement(a.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        assertThat(group(oldGroup).get("status")).isEqualTo("FAILED");

        // M3 재분배(지난 계좌) — 영업일 마감 전이면 바로 새 묶음(다음 지급일)에 넣고 올린다.
        SettlementPayout failed = payout(a.getId(), SettlementPayee.BRAND);
        inTransaction(() -> payoutRepository.reschedule(failed.getId(), d, failed.getBankName(), failed.getAccountNumberEnc(),
                failed.getAccountHolder(), 3));
        payoutService.distribute(a.getId(), d, d.atTime(11, 0));

        SettlementPayout retried = payout(a.getId(), SettlementPayee.BRAND);
        assertThat(retried.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(retried.getAttempt()).isEqualTo(1);
        assertThat(retried.getPayoutGroupId()).isNotEqualTo(oldGroup);
        assertThat(retried.getPgPayoutDate()).isEqualTo(businessCalendar.addBusinessDays(d, 1));
        assertThat(payout(b.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.FAILED);   // 행마다
    }

    // ================================================================== IN-5 · IN-12 · 계좌 제약

    @Test
    @DisplayName("IN-5 · IN-12 10:00 뒤 풀린 행 → 14:00 틱이 같은 키 OPEN 묶음에 합류 · 「기존 + 이번」 합계로 덮는다 · 계좌가 다른 행은 다음 지급일 묶음 · 그 묶음은 앞 묶음 송금 뒤에 서브몰 계좌를 바꾸고 올린다")
    void lateRowJoinsAndOtherAccountWaits() {
        readyForInicis();
        Settlement a = scheduledSettlement();
        LocalDate d = a.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        LocalDate nextPayDate = businessCalendar.addBusinessDays(d, 1);
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        long brandA = payout(a.getId(), SettlementPayee.BRAND).getAmount();
        Long g = payout(a.getId(), SettlementPayee.BRAND).getPayoutGroupId();

        Settlement b = secondSettlementDue(d);
        Settlement c = secondSettlementDue(d);
        jdbc.update("UPDATE settlement_payout SET bank_name = ?, account_number_enc = ?, account_holder = ? "
                        + "WHERE settlement_id = ? AND payee = 'BRAND'", "국민은행", personalDataCipher.encrypt("9876543210"),
                "글로우랩 주식회사", c.getId());
        payoutService.cutoffTick(x.atTime(14, 0), 200);

        long brandB = payout(b.getId(), SettlementPayee.BRAND).getAmount();
        assertThat(payout(b.getId(), SettlementPayee.BRAND).getPayoutGroupId()).isEqualTo(g);
        assertThat(payout(b.getId(), SettlementPayee.BRAND).getPgPayoutDate()).isEqualTo(d);   // 「해제 다음 영업일 지급」
        assertThat(((Number) group(g).get("confirmed_amount")).longValue()).isEqualTo(brandA + brandB);
        assertThat(payoutGateway.uploaded()).containsEntry("tb" + brand.marketId() + "|" + d, brandA + brandB);
        SettlementPayout other = payout(c.getId(), SettlementPayee.BRAND);
        assertThat(other.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(other.getPgPayoutDate()).isEqualTo(nextPayDate);
        assertThat(other.getPayoutGroupId()).isNotEqualTo(g);
        assertThat(group(other.getPayoutGroupId()).get("confirmed_amount")).isNull();   // 다른 계좌의 미송금 묶음 — 아직 올리지 않는다
        assertThat(payoutGateway.uploaded()).doesNotContainKey("tb" + brand.marketId() + "|" + nextPayDate);

        // D 송금 완료 → 다음 마감 전 틱이 서브몰 계좌를 바꾸고(UPD) 다음 지급일 묶음을 올린다.
        payoutGateway.completeFor(SettlementPayee.BRAND);
        payoutService.pollResults(d.atTime(11, 0));
        assertThat(group(g).get("status")).isEqualTo("PAID");
        int ensureCalls = partnerGateway.calls(SettlementPayee.BRAND).size();
        payoutService.cutoffTick(d.atTime(14, 0), 200);

        assertThat(partnerGateway.calls(SettlementPayee.BRAND)).hasSize(ensureCalls + 1);
        PartnerProfile update = partnerGateway.calls(SettlementPayee.BRAND).get(ensureCalls);
        assertThat(update.existing()).isTrue();
        assertThat(update.account().accountNumber()).isEqualTo("9876543210");
        assertThat(payoutGateway.uploaded()).containsEntry("tb" + brand.marketId() + "|" + nextPayDate, other.getAmount());
    }

    @Test
    @DisplayName("합류가 확정 거절 — 새로 붙은 행만 묶음에서 빠져 FAILED · 앞서 올라간 행은 REQUESTED · 묶음 OPEN · 확정 합계 그대로")
    void rejectedJoinFailsOnlyNewRows() {
        readyForInicis();
        Settlement a = scheduledSettlement();
        LocalDate d = a.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        SettlementPayout first = payout(a.getId(), SettlementPayee.BRAND);

        Settlement b = secondSettlementDue(d);
        payoutGateway.failFor(SettlementPayee.BRAND, "REJECTED_BY_PG", "EB99 · 처리 불가");
        payoutService.cutoffTick(x.atTime(14, 0), 200);

        SettlementPayout rejected = payout(b.getId(), SettlementPayee.BRAND);
        assertThat(rejected.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(rejected.getPayoutGroupId()).isNull();
        assertThat(settlement(b.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        assertThat(payout(a.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        Map<String, Object> group = group(first.getPayoutGroupId());
        assertThat(group.get("status")).isEqualTo("OPEN");
        assertThat(((Number) group.get("confirmed_amount")).longValue()).isEqualTo(first.getAmount());
        assertThat(payoutGateway.uploaded()).containsEntry("tb" + brand.marketId() + "|" + d, first.getAmount());
    }

    // ================================================================== 결과 모름 · 수렴(D-1 ④)

    @Test
    @DisplayName("전송 결과 모름 — 행 REQUESTED · 확정 합계 그대로(null) · 14:00 수렴이 같은 합계로 다시 올린다 · 맞으면 다시 부르지 않는다")
    void unknownOutcomeConverges() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.unresponsive();
        payoutService.distributeDue(x, x.atTime(10, 0), 200);

        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(group(brandRow.getPayoutGroupId()).get("confirmed_amount")).isNull();
        assertThat(brandRow.getPgTransferId()).isNull();

        payoutGateway.responsive();
        payoutService.cutoffTick(x.atTime(14, 0), 200);
        assertThat(((Number) group(brandRow.getPayoutGroupId()).get("confirmed_amount")).longValue()).isEqualTo(brandRow.getAmount());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getPgTransferId()).isNotNull();
        int calls = payoutGateway.groupCalls().size();
        payoutService.cutoffTick(x.atTime(14, 30), 200);
        assertThat(payoutGateway.groupCalls()).hasSize(calls);
    }

    @Test
    @DisplayName("결과 모름 뒤 재전송이 확정 거절 — 앞선 전송이 PG 에 있을 수 있어 행을 실패로 돌리지 않는다(이중 지급 방지) · 묶음 OPEN · OUTCOME_UNKNOWN 유지 · 마감 뒤에도 그대로 · 송금 결과가 닫는다")
    void rejectionAfterUnknownKeepsRows() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.unresponsive();
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        payoutGateway.responsive();

        payoutGateway.failFor(SettlementPayee.BRAND, "REJECTED_BY_PG", "EM13 · 익일 지급 오류");
        payoutService.cutoffTick(x.atTime(14, 0), 200);

        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(brandRow.getPayoutGroupId()).isNotNull();
        Map<String, Object> group = group(brandRow.getPayoutGroupId());
        assertThat(group.get("status")).isEqualTo("OPEN");
        assertThat(group.get("result_code")).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(settlement(s.getId()).getStatus()).isNotEqualTo(SettlementStatus.PAYOUT_FAILED);

        payoutService.distributeDue(d, d.atTime(10, 0), 200);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(group(brandRow.getPayoutGroupId()).get("status")).isEqualTo("OPEN");

        // 앞선 전송이 실제로 올라가 있었다 — 송금 결과가 닫는다(행을 실패로 돌렸다면 재분배가 한 번 더 보냈을 것이다).
        payoutGateway.completeFor(SettlementPayee.BRAND);
        payoutGateway.completeFor(SettlementPayee.CREATOR);
        payoutService.pollResults(d.atTime(17, 0));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(group(brandRow.getPayoutGroupId()).get("status")).isEqualTo("PAID");
    }

    // ================================================================== PG 일시 오류(RETRY)

    @Test
    @DisplayName("PG 일시 오류(EM18) — 행 REQUESTED · 실패로 돌리지 않음 · 묶음 RETRY_EM18 · 확정 합계 없음 → 14:00 수렴이 다시 올려 접수 · 표시 지움")
    void transientErrorRetriedAtCutoffTick() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.retryFor(SettlementPayee.BRAND, "EM18");
        payoutService.distributeDue(x, x.atTime(10, 0), 200);

        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(brandRow.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(brandRow.getPgTransferId()).isNull();
        Map<String, Object> retried = group(brandRow.getPayoutGroupId());
        assertThat(retried.get("status")).isEqualTo("OPEN");
        assertThat(retried.get("result_code")).isEqualTo("RETRY_EM18");
        assertThat(retried.get("confirmed_amount")).isNull();
        assertThat(settlement(s.getId()).getStatus()).isNotEqualTo(SettlementStatus.PAYOUT_FAILED);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_FAILED);

        payoutGateway.recoverFor(SettlementPayee.BRAND);
        payoutService.cutoffTick(x.atTime(14, 0), 200);

        Map<String, Object> accepted = group(brandRow.getPayoutGroupId());
        assertThat(((Number) accepted.get("confirmed_amount")).longValue()).isEqualTo(brandRow.getAmount());
        assertThat(accepted.get("result_code")).isNull();
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getPgTransferId()).isNotNull();
        assertThat(payoutGateway.uploaded()).containsEntry("tb" + brand.marketId() + "|" + d, brandRow.getAmount());
    }

    @Test
    @DisplayName("일시 오류가 마감까지 이어짐 — 다음 배치의 마감 경과 정리가 행을 다음 지급일로 넘기고 같은 배치가 새 묶음으로 다시 지시(attempt 불변 · 재분배 불필요) · 앞 묶음 DELETED")
    void transientErrorPastCutoffMovesToNextPayDate() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.retryFor(SettlementPayee.BRAND, "EM18");
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        payoutService.cutoffTick(x.atTime(14, 0), 200);
        SettlementPayout stuck = payout(s.getId(), SettlementPayee.BRAND);
        Long firstGroupId = stuck.getPayoutGroupId();
        assertThat(stuck.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(group(firstGroupId).get("result_code")).isEqualTo("RETRY_EM18");

        payoutGateway.recoverFor(SettlementPayee.BRAND);
        payoutService.distributeDue(d, d.atTime(10, 0), 200);

        LocalDate nextPayDate = businessCalendar.addBusinessDays(d, 1);
        SettlementPayout moved = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(moved.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(moved.getPgPayoutDate()).isEqualTo(nextPayDate);
        assertThat(moved.getPayoutGroupId()).isNotEqualTo(firstGroupId);
        assertThat(moved.getAttempt()).isEqualTo(stuck.getAttempt());
        assertThat(group(firstGroupId).get("status")).isEqualTo("DELETED");
        assertThat(payoutGateway.uploaded()).containsEntry("tb" + brand.marketId() + "|" + nextPayDate, moved.getAmount());
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_DEFERRED)
                .doesNotContain(SettlementEventType.PAYOUT_FAILED);
    }

    // ================================================================== 송금 계좌 대조

    @Test
    @DisplayName("송금 결과의 계좌가 지급 당시 계좌와 다름 — 지급은 닫는다(PAID) · 묶음에 ACCOUNT_MISMATCH · 정산 이력에 확인 요청 / 같은 계좌면 표시 없음")
    void remittedAccountMismatchFlagged() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        SettlementPayout creatorRow = payout(s.getId(), SettlementPayee.CREATOR);

        payoutGateway.completeFor(SettlementPayee.BRAND);
        payoutGateway.completeFor(SettlementPayee.CREATOR);
        payoutGateway.remitAccountFor(SettlementPayee.BRAND, "088", "110-999-990000");   // 등록 계좌 110123456789 와 다르다
        payoutGateway.remitAccountFor(SettlementPayee.CREATOR, "004", "123456789012");   // 같다
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_CHECK_REQUIRED);
        payoutService.pollResults(d.atTime(17, 0));

        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);
        Map<String, Object> brandGroup = group(brandRow.getPayoutGroupId());
        assertThat(brandGroup.get("status")).isEqualTo("PAID");
        assertThat(brandGroup.get("result_code")).isEqualTo("ACCOUNT_MISMATCH");
        assertThat(group(creatorRow.getPayoutGroupId()).get("result_code")).isNull();
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_CHECK_REQUIRED);
    }

    // ================================================================== 정합(직렬화 · 마감 뒤 전송 · 10분 수렴)

    @Test
    @DisplayName("같은 수취자 전송은 한 번에 하나 — 첫 전송이 PG 호출 중이면 둘째 전송은 기다렸다가 준비한다(이미 맞으면 부르지 않는다) · PG 호출 1회 · 확정 합계 = 행 합계")
    void concurrentSubmitsAreSerialized() throws Exception {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.unresponsive();
        payoutService.distributeDue(x, x.atTime(10, 0), 200);   // 결과 모름 — 묶음은 다시 올려야 한다
        payoutGateway.responsive();
        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);
        Long groupId = brandRow.getPayoutGroupId();
        int before = payoutGateway.groupCalls().size();

        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        payoutGateway.holdNextSubmit(entered, release);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> groupService.submit(groupId, x.atTime(11, 0)));
            assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> groupService.submit(groupId, x.atTime(11, 0)));
            Thread.sleep(500);
            // 첫 전송이 PG 호출 안에 붙들려 있는 동안 둘째 전송은 PG 에 닿지 않는다.
            assertThat(payoutGateway.groupCalls()).hasSize(before);
            release.countDown();
            assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(SettlementPayoutGroupService.SubmitOutcome.SENT);
            assertThat(second.get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(SettlementPayoutGroupService.SubmitOutcome.IN_SYNC);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(payoutGateway.groupCalls()).hasSize(before + 1);
        Map<String, Object> group = group(groupId);
        assertThat(((Number) group.get("confirmed_amount")).longValue()).isEqualTo(brandRow.getAmount());
        assertThat(group.get("result_code")).isNull();
    }

    @Test
    @DisplayName("수정 마감이 지난 묶음은 보내지 않는다 — 결과 모름 묶음도 15:01 전송은 SKIPPED · PG 호출 없음 · 결과 모름 유지(송금 결과가 가른다)")
    void noSubmitAfterCutoff() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.unresponsive();
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        payoutGateway.responsive();
        Long groupId = payout(s.getId(), SettlementPayee.BRAND).getPayoutGroupId();
        int before = payoutGateway.groupCalls().size();

        assertThat(groupService.submit(groupId, x.atTime(15, 1))).isEqualTo(SettlementPayoutGroupService.SubmitOutcome.SKIPPED);
        assertThat(payoutGateway.groupCalls()).hasSize(before);
        assertThat(group(groupId).get("status")).isEqualTo("OPEN");
        assertThat(group(groupId).get("result_code")).isEqualTo("OUTCOME_UNKNOWN");
    }

    @Test
    @DisplayName("마감 전 수렴(10분마다) — 어긋난 묶음(결과 모름)만 11:05 에 다시 올려 확정 · 맞는 묶음은 부르지 않는다 · 마감 뒤(15:05) · 비영업일은 아무것도 하지 않는다")
    void convergeEveryTenMinutesBeforeCutoff() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.unresponsive();
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        payoutGateway.responsive();
        SettlementPayout brandRow = payout(s.getId(), SettlementPayee.BRAND);

        assertThat(payoutService.convergeBeforeCutoff(x.atTime(15, 5))).isZero();
        LocalDate holiday = x.plusDays(1);
        while (businessCalendar.isBusinessDay(holiday)) {
            holiday = holiday.plusDays(1);
        }
        assertThat(payoutService.convergeBeforeCutoff(holiday.atTime(11, 5))).isZero();

        assertThat(payoutService.convergeBeforeCutoff(x.atTime(11, 5))).isEqualTo(2);   // 브랜드 · 인플루언서 묶음
        assertThat(((Number) group(brandRow.getPayoutGroupId()).get("confirmed_amount")).longValue()).isEqualTo(brandRow.getAmount());
        int calls = payoutGateway.groupCalls().size();
        assertThat(payoutService.convergeBeforeCutoff(x.atTime(11, 15))).isZero();
        assertThat(payoutGateway.groupCalls()).hasSize(calls);
    }

    // ================================================================== IN-6 · IN-7

    @Test
    @DisplayName("IN-6 지시 뒤 마감 전 제재 — 그 행 BLOCKED · 묶음에서 빠지고 남은 행이 없으면 DEL(묶음 DELETED) / IN-7 마감 뒤 제재 — 묶음 불변(지급 뒤 차감)")
    void sanctionBeforeAndAfterCutoff() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        Long g = payout(s.getId(), SettlementPayee.BRAND).getPayoutGroupId();
        Long creatorGroup = payout(s.getId(), SettlementPayee.CREATOR).getPayoutGroupId();

        // 마감 뒤(16:00) — 인플루언서 몫: 묶음은 고정이다.
        inTransaction(() -> holdTransitions.hold(SettlementPayee.CREATOR, creator.getId(), PayoutBlockReason.CREATOR_SUSPENDED,
                x.atTime(16, 0)));
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(group(creatorGroup).get("status")).isEqualTo("OPEN");

        // 마감 전(11:00) — 브랜드 몫: 빼고 남은 합계 0 → DEL.
        inTransaction(() -> holdTransitions.hold(SettlementPayee.BRAND, brand.marketId(), PayoutBlockReason.BRAND_SUSPENDED,
                x.atTime(11, 0)));
        SettlementPayout held = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(held.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(held.getFailCode()).isEqualTo(PayoutBlockReason.BRAND_SUSPENDED.name());
        assertThat(held.getPayoutGroupId()).isNull();
        assertThat(group(g).get("status")).isEqualTo("DELETED");
        assertThat(payoutGateway.uploaded()).doesNotContainKey("tb" + brand.marketId() + "|" + d);
        assertThat(payoutGateway.groupCalls(SettlementPayee.BRAND)).last().satisfies(c -> assertThat(c.total()).isZero());
    }

    // ================================================================== IN-8 · IN-8′

    @Test
    @DisplayName("IN-8 10:00 잔액 부족 → 전부 지시 · 14:00 여전히 부족 → 큰 묶음만 남기고 나머지 DEL · 행은 다음 지급일 예정 · attempt 불변 / IN-8′ 14:00 전 입금이면 정리 없음")
    void balanceShortfall() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        long brandAmount = payout(s.getId(), SettlementPayee.BRAND).getAmount();
        long creatorAmount = payout(s.getId(), SettlementPayee.CREATOR).getAmount();
        assertThat(brandAmount).isGreaterThan(creatorAmount);

        payoutGateway.balance(brandAmount);
        payoutService.distributeDue(x, x.atTime(10, 0), 200);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);

        payoutService.cutoffTick(x.atTime(14, 0), 200);

        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        SettlementPayout deferred = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(deferred.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(deferred.getDueDate()).isEqualTo(businessCalendar.addBusinessDays(d, 1));
        assertThat(deferred.getAttempt()).isZero();
        assertThat(payoutGateway.uploaded()).doesNotContainKey("tc" + creator.getId() + "|" + d);
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_DEFERRED);
    }

    @Test
    @DisplayName("IN-8′ 10:00 부족 → 14:00 전 입금(잔액 증가) → 정리 없음 · 전부 D 지급")
    void balanceRecoveredBeforeRecheck() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutGateway.balance(1L);
        payoutService.distributeDue(x, x.atTime(10, 0), 200);

        payoutGateway.balance(1_000_000_000L);
        payoutService.cutoffTick(x.atTime(14, 0), 200);

        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getPgPayoutDate()).isEqualTo(d);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_DEFERRED);
    }

    // ================================================================== IN-9 · IN-9′

    @Test
    @DisplayName("IN-9 7일 누적이 고객확인 기준 이상 · AML 정보 없음 → 지시는 한다(안내만 · 보류 없음 · 해제 반복 없음)")
    void amlThresholdOnlyAlerts() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        long original = settlementProperties.getPayout().getWeeklyLimitAmount();
        settlementProperties.getPayout().setWeeklyLimitAmount(1L);
        try {
            payoutService.distributeDue(x, x.atTime(10, 0), 200);
            assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
            assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
            assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED);
            assertThat(payoutService.releaseBlocked(d)).isZero();
        } finally {
            settlementProperties.getPayout().setWeeklyLimitAmount(original);
        }
    }

    @Test
    @DisplayName("IN-9′ amlstatus = 만료 → 지시 앞단이 저장 · 그 수취자 행 BLOCKED(KYC_REQUIRED) · 등록 성공으로는 안 풀림 · amlstatus = 유효면 배치 앞단이 해제")
    void amlBlockingReleasedOnlyByAmlStatus() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        partnerGateway.amlFor(SettlementPayee.BRAND, FakeSettlementPartnerGateway.AML_EXPIRED);

        payoutService.distributeDue(x, x.atTime(10, 0), 200);

        SettlementPayout blocked = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(blocked.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(blocked.getFailCode()).isEqualTo(PayoutBlockReason.KYC_REQUIRED.name());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(jdbc.queryForObject("SELECT pg_aml_status FROM seller WHERE seller_id = ?", String.class,
                brand.seller().getId())).isEqualTo(FakeSettlementPartnerGateway.AML_EXPIRED);

        int ensureCalls = partnerGateway.calls().size();
        assertThat(payoutService.releaseBlocked(x)).isZero();
        assertThat(partnerGateway.calls()).hasSize(ensureCalls);   // 등록(upsert)으로 풀지 않는다
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        partnerGateway.amlFor(SettlementPayee.BRAND, FakeSettlementPartnerGateway.AML_VALID);
        assertThat(payoutService.releaseBlocked(x)).isEqualTo(1);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    // ================================================================== IN-10 · IN-11

    @Test
    @DisplayName("IN-10 대표자 생년월일 없는 브랜드 확정 → BLOCKED(PARTNER_PROFILE_INCOMPLETE) · 파트너센터에서 입력(값은 내리지 않음) → 배치 앞단이 서브몰 등록 · 해제")
    void missingBirthdateBlocksUntilEntered() throws Exception {
        readyForInicis();
        jdbc.update("UPDATE seller SET representative_birthdate_enc = NULL WHERE seller_id = ?", brand.seller().getId());
        Settlement s = scheduledSettlement();

        SettlementPayout blocked = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(blocked.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(blocked.getFailCode()).isEqualTo(PayoutBlockReason.PARTNER_PROFILE_INCOMPLETE.name());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        sellerGet("/v1/seller/basic-info/business").andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.representativeBirthdateRegistered").value(false));

        sellerPatch("/v1/seller/basic-info/business", Map.of("taxEmail", "tax@glowlab.example",
                "representativeBirthdate", "1985-03-14")).andExpect(status().isNoContent());
        sellerGet("/v1/seller/basic-info/business").andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.representativeBirthdateRegistered").value(true));
        sellerPatch("/v1/seller/basic-info/business", Map.of("taxEmail", "tax@glowlab.example",
                "representativeBirthdate", "1985-02-30")).andExpect(status().isBadRequest());

        assertThat(payoutService.releaseBlocked(LocalDate.now())).isEqualTo(1);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(partnerGateway.calls(SettlementPayee.BRAND)).last()
                .satisfies(p -> assertThat(p.birthdate()).isEqualTo("19850314"));
        assertThat(jdbc.queryForObject("SELECT pg_partner_provider FROM seller WHERE seller_id = ?", String.class,
                brand.seller().getId())).isEqualTo("INICIS");
    }

    @Test
    @DisplayName("IN-11 토스 연결 이력(pg_partner_provider=TOSS) — 연결 없음으로 보고 이니시스 INS(id 없음 · existing=false) · 연결은 INICIS · idMall 로 바뀐다")
    void tossLinkIsNotInicisLink() {
        readyForInicis();
        jdbc.update("UPDATE seller SET pg_partner_id = ?, pg_partner_ref = ?, pg_partner_env = 'TEST', pg_partner_provider = 'TOSS', "
                + "pg_partner_status = 'APPROVED' WHERE seller_id = ?", "TOSS-SELLER-1", "tb" + brand.marketId(), brand.seller().getId());

        scheduledSettlement();

        PartnerProfile profile = partnerGateway.calls(SettlementPayee.BRAND).get(0);
        assertThat(profile.existing()).isFalse();
        assertThat(profile.externalId()).isNull();
        assertThat(profile.partnerRef()).isEqualTo("tb" + brand.marketId());
        Map<String, Object> link = jdbc.queryForMap("SELECT pg_partner_id, pg_partner_provider FROM seller WHERE seller_id = ?",
                brand.seller().getId());
        assertThat(link.get("pg_partner_id")).isEqualTo("tb" + brand.marketId());
        assertThat(link.get("pg_partner_provider")).isEqualTo("INICIS");
    }

    // ================================================================== IN-13

    @Test
    @DisplayName("IN-13 처리 지연 판정은 지급일 기준 — D 10:00(결과 전)은 알림 없음 · D+1 10:00 에도 결과가 없으면 알림")
    void staleJudgedByPayDate() {
        readyForInicis();
        Settlement s = scheduledSettlement();
        LocalDate d = s.getPayoutDueDate();
        LocalDate x = businessCalendar.previousBusinessDay(d);
        payoutService.distributeDue(x, x.atTime(10, 0), 200);

        payoutService.pollResults(d.atTime(10, 0));
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_CHECK_REQUIRED);

        payoutService.pollResults(businessCalendar.addBusinessDays(d, 1).atTime(10, 0));
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_CHECK_REQUIRED);
        assertThat(payoutGateway.groupLookups()).hasSize(2);
    }

    // ------------------------------------------------------------------ 보조

    /** 이니시스 서브몰 필수값 — 계좌 · 은행 코드 · 대표자 생년월일 · 인플루언서 휴대폰 · 이메일 · 생년월일. */
    private void readyForInicis() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        fixture.createBank("004", "국민은행");
        fixture.createBank("088", "신한은행");
        jdbc.update("UPDATE seller SET representative_birthdate_enc = ? WHERE seller_id = ?",
                personalDataCipher.encrypt("19850314"), brand.seller().getId());
        jdbc.update("UPDATE creator SET phone_number = ?, business_email = ?, birthday = ? WHERE creator_id = ?",
                "010-9876-5432", "jimin@example.com", "1997-05-05", creator.getId());
    }

    private Settlement scheduledSettlement() {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        return confirm(generate(LocalDateTime.now().withNano(0)));
    }

    /** 같은 브랜드 · 인플루언서의 다른 종료 공구 정산 — 지급 예정(예정일 D)까지. */
    private Settlement secondSettlementDue(LocalDate due) {
        Settlement other = seedSettlement("다른 공구 " + System.nanoTime(), 50_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement SET payout_due_date = ? WHERE settlement_id = ?", due, other.getId());
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ? AND payee <> 'PLATFORM'",
                due, other.getId());
        jdbc.update("UPDATE settlement_payout SET status = 'NOT_APPLICABLE' WHERE settlement_id = ? AND payee = 'PLATFORM'",
                other.getId());
        return settlement(other.getId());
    }

    private Map<String, Object> group(Long groupId) {
        return jdbc.queryForMap("SELECT status, confirmed_amount, intended_amount, pg_partner_id, pay_date, remitted_on, "
                + "last_request_id, result_code FROM settlement_payout_group WHERE id = ?", groupId);
    }

    @SuppressWarnings("unused")
    private List<SettlementPayout> rows(Long settlementId) {
        return payoutsOf(settlementId);
    }
}

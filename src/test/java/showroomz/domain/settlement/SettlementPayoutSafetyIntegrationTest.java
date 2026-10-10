package showroomz.domain.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.scheduler.SettlementAutoConfirmScheduler;
import showroomz.global.scheduler.SettlementGenerationScheduler;
import showroomz.global.scheduler.SettlementPayoutScheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 확정 · 지급 보완 — {@link SettlementLifecycleIntegrationTest}가 정상 전이를 본다. 여기서는 돈이 두 번 나가거나 확정이 두 번
 * 일어나지 않는지, 그리고 배치 진입점(tick · run)이 서비스와 같은 결과를 내는지를 본다.
 *
 * <ul>
 *   <li>PS-01 PG 응답 없음(결과 모름) — REQUESTED 로 남고 재지시하지 않는다</li>
 *   <li>PS-02 지급 완료 뒤 배치 재실행 — 지시 · 이력 · 지급 시각 불변</li>
 *   <li>PS-03 예정일 전 · 공휴일 · 조정 보류 중 — 지시하지 않는다</li>
 *   <li>PS-04 중복 확정 — 409 · 수취자 행 · 증빙 행 · 이력 불변</li>
 *   <li>PS-05 자동 확정 배치 1회분 — 마감 지난 확인 중만 · 두 번 돌아도 1회</li>
 *   <li>PS-06 생성 배치 1회분 — 미종결이면 건너뛰고 종결 뒤 1건 · 두 번 돌아도 1건</li>
 * </ul>
 */
@DisplayName("정산 확정 · 지급 보완 — 결과 미확인 · 재실행 · 예정일 전 · 중복 확정 · 배치 1회분")
class SettlementPayoutSafetyIntegrationTest extends SettlementTestSupport {

    /** 2026-09-30(수) 10:00 생성 → 확인 마감 10.05 23:59:59 → 자동 확정 10.06 00:00 → 지급 예정 10.12(10.09 한글날 제외). */
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final LocalDate PAYOUT_DUE = LocalDate.of(2026, 10, 12);

    @Autowired private SettlementAdjustmentPort adjustmentPort;

    @BeforeEach
    void hangulDayOnly() {
        jdbc.update("INSERT INTO business_holiday (holiday_date, name) VALUES (?, ?)", LocalDate.of(2026, 10, 9), "한글날");
        businessCalendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 10, 9)));
    }

    // ------------------------------------------------------------------ PS-01 결과 미확인

    @Test
    @DisplayName("PS-01 PG 응답 없음 — 지시한 3행 REQUESTED(지급 처리 중) · 계좌 스냅샷 · 정산 지급 예정 · 공구 ENDED · 같은 날 · 다음 날 배치가 다시 지시하지 않는다")
    void unresponsiveGatewayNeverResends() throws Exception {
        readyToPay();
        Settlement s = confirm(generated());
        payoutGateway.unresponsive();

        payoutService.distribute(s.getId(), PAYOUT_DUE, PAYOUT_DUE.atTime(10, 0));

        assertThat(payoutGateway.calls()).singleElement()
                .satisfies(call -> assertThat(call.lines()).hasSize(3));
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.REQUESTED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getBankName()).isEqualTo("신한은행");
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getAccountHolder()).isEqualTo("김지민");
        Settlement after = settlement(s.getId());
        assertThat(after.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(after.getPaidAt()).isNull();
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.ENDED);
        assertThat(settlementEvents(s.getId())).filteredOn(SettlementEventType.PAYOUT_REQUESTED::equals).hasSize(3);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_PAID,
                SettlementEventType.PAYOUT_FAILED);
        assertThat(payoutService.findSettlementIdsDue(PAYOUT_DUE, 200)).doesNotContain(s.getId());
        // 운영자 화면 — 분배 실패가 아니므로 재분배 대상도 아니다(결과는 PG 결과 조회가 닫는다).
        adminGet("/v1/admin/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYOUT_SCHEDULED"))
                .andExpect(jsonPath("$.payouts.rows[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.actions.canRedistribute").value(false));

        payoutGateway.reset();
        SettlementPayoutScheduler scheduler = new SettlementPayoutScheduler(payoutService, businessCalendar);
        scheduler.run(PAYOUT_DUE.atTime(15, 0));
        scheduler.run(PAYOUT_DUE.plusDays(1).atTime(10, 0));

        assertThat(payoutGateway.calls()).isEmpty();
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.REQUESTED);
        assertThat(settlementEvents(s.getId())).filteredOn(SettlementEventType.PAYOUT_REQUESTED::equals).hasSize(3);
    }

    // ------------------------------------------------------------------ PS-02 재실행

    @Test
    @DisplayName("PS-02 지급 완료 뒤 같은 날 배치가 다시 돌아도 PG 지시 1회 · 지급 이력 3건 · 지급 시각 그대로")
    void rerunAfterPaidIsNoop() {
        readyToPay();
        Settlement s = confirm(generated());
        SettlementPayoutScheduler scheduler = new SettlementPayoutScheduler(payoutService, businessCalendar);

        scheduler.run(PAYOUT_DUE.atTime(10, 0));
        Settlement paid = settlement(s.getId());
        assertThat(paid.getStatus()).isEqualTo(SettlementStatus.PAID);

        scheduler.run(PAYOUT_DUE.atTime(10, 30));
        payoutService.distribute(s.getId(), PAYOUT_DUE, PAYOUT_DUE.atTime(11, 0));

        assertThat(payoutGateway.calls()).hasSize(1);
        assertThat(settlementEvents(s.getId())).filteredOn(SettlementEventType.PAYOUT_PAID::equals).hasSize(3);
        assertThat(settlement(s.getId()).getPaidAt()).isEqualTo(paid.getPaidAt());
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getPaidAt).containsOnly(PAYOUT_DUE.atTime(10, 0));
    }

    // ------------------------------------------------------------------ PS-03 지시하지 않는 날 · 상태

    @Test
    @DisplayName("PS-03 예정일 전(10.08) · 공휴일(10.09)에는 지시 0회 · 예정일(10.12)에 3자 지급")
    void notBeforeDueDate() {
        readyToPay();
        Settlement s = confirm(generated());
        SettlementPayoutScheduler scheduler = new SettlementPayoutScheduler(payoutService, businessCalendar);

        assertThat(payoutService.findSettlementIdsDue(LocalDate.of(2026, 10, 8), 200)).doesNotContain(s.getId());
        scheduler.run(LocalDate.of(2026, 10, 8).atTime(10, 0));
        scheduler.run(LocalDate.of(2026, 10, 9).atTime(10, 0));
        // 배치가 아닌 직접 호출도 예정일 전 행은 건너뛴다(선점 가드).
        payoutService.distribute(s.getId(), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8).atTime(11, 0));

        assertThat(payoutGateway.calls()).isEmpty();
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.SCHEDULED);

        assertThat(payoutService.findSettlementIdsDue(PAYOUT_DUE, 200)).contains(s.getId());
        scheduler.run(PAYOUT_DUE.atTime(10, 0));
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    @DisplayName("PS-03 조정 협의로 보류(HELD)된 정산은 예정일이 지나도 지시하지 않는다")
    void heldSettlementIsNotPaid() {
        readyToPay();
        Settlement s = generated();
        inTransaction(() -> {
            adjustmentPort.holdForAdjustment(s.getId(), 0L, LocalDateTime.of(2026, 10, 2, 10, 0));
            return null;
        });

        new SettlementPayoutScheduler(payoutService, businessCalendar).run(PAYOUT_DUE.plusDays(7).atTime(10, 0));
        payoutService.distribute(s.getId(), PAYOUT_DUE.plusDays(7), PAYOUT_DUE.plusDays(7).atTime(11, 0));

        assertThat(payoutGateway.calls()).isEmpty();
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.ADJUSTING);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.HELD);
    }

    // ------------------------------------------------------------------ PS-04 중복 확정

    @Test
    @DisplayName("PS-04 이미 확정된 정산을 다시 확정 — 409 STATE_CHANGED · 수취자 행 · 예정일 · 세금계산서 행 · 확정 이력 그대로")
    void confirmTwiceIsRejected() {
        Settlement s = confirm(generated());
        List<SettlementPayout> before = payoutsOf(s.getId());

        assertThatThrownBy(() -> confirmService.confirm(s.getId(), SettlementConfirmReason.AUTO,
                s.autoConfirmAt().plusHours(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_STATE_CHANGED);
        assertThatThrownBy(() -> confirmService.confirm(s.getId(), SettlementConfirmReason.AGREED,
                s.autoConfirmAt().plusHours(2)))
                .isInstanceOf(BusinessException.class);

        Settlement after = settlement(s.getId());
        assertThat(after.getConfirmReason()).isEqualTo(SettlementConfirmReason.AUTO);
        assertThat(after.getConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 0, 0));
        assertThat(after.getPayoutDueDate()).isEqualTo(PAYOUT_DUE);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getPayee, SettlementPayout::getStatus,
                        SettlementPayout::getDueDate)
                .containsExactlyElementsOf(before.stream()
                        .map(p -> tuple(p.getPayee(), p.getStatus(), p.getDueDate())).toList());
        assertThat(settlementEvents(s.getId())).filteredOn(SettlementEventType.AUTO_CONFIRMED::equals).hasSize(1);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.CONFIRMED_BY_AGREEMENT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_tax_document WHERE settlement_id = ? "
                + "AND type = 'BRAND_TAX_INVOICE'", Long.class, s.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ PS-05 · PS-06 배치 1회분

    @Test
    @DisplayName("PS-05 자동 확정 배치 tick — 마감 지난 확인 중 2건만 확정(마감 다음 날 00:00) · 마감 전 · 조정 협의는 그대로 · 두 번 돌아도 확정 1회")
    void autoConfirmTick() {
        Settlement due1 = seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.REVIEWING);
        Settlement due2 = seedSettlement("겨울 핸드크림 공구", 2_000_000, SettlementStatus.REVIEWING);
        Settlement notYet = seedSettlement("봄 선크림 공구", 3_000_000, SettlementStatus.REVIEWING);
        Settlement adjusting = seedSettlement("여름 미스트 공구", 4_000_000, SettlementStatus.ADJUSTING);
        LocalDateTime passedDue = LocalDate.now().minusDays(1).atTime(23, 59, 59);
        for (Settlement s : List.of(due1, due2, adjusting)) {
            jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?", passedDue, s.getId());
        }
        SettlementAutoConfirmScheduler scheduler = new SettlementAutoConfirmScheduler(confirmService);

        scheduler.tick();
        scheduler.tick();

        for (Settlement s : List.of(due1, due2)) {
            Settlement confirmed = settlement(s.getId());
            assertThat(confirmed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
            assertThat(confirmed.getConfirmReason()).isEqualTo(SettlementConfirmReason.AUTO);
            assertThat(confirmed.getConfirmedAt()).isEqualTo(LocalDate.now().atStartOfDay());
            assertThat(settlementEvents(s.getId())).filteredOn(SettlementEventType.AUTO_CONFIRMED::equals).hasSize(1);
            assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        }
        assertThat(settlement(notYet.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(settlement(adjusting.getId()).getStatus()).isEqualTo(SettlementStatus.ADJUSTING);
        assertThat(settlementEvents(adjusting.getId())).doesNotContain(SettlementEventType.AUTO_CONFIRMED);
    }

    @Test
    @DisplayName("PS-06 생성 배치 tick — 구매확정 전 주문이 남으면 건너뛰고 · 종결 뒤 1건 생성 · 두 번 돌아도 정산 1건 · 생성 이력 1건")
    void generationTick() {
        OrderDeliveryGroup open = deliveredFrom(purchase(creamVariant, 1).group());
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        SettlementGenerationScheduler scheduler = new SettlementGenerationScheduler(generationService);

        scheduler.tick();
        assertThat(settlementRepository.findByGroupBuyId(groupBuy.getId())).isEmpty();

        LocalDateTime now = LocalDateTime.now();
        assertThat(fulfillmentService.confirmPurchase(open.getId(), now, now.minusDays(7))).isTrue();
        scheduler.tick();
        scheduler.tick();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement WHERE group_buy_id = ?", Long.class,
                groupBuy.getId())).isEqualTo(1);
        Settlement s = settlementRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(itemsOf(s)).hasSize(2);
        assertThat(settlementEvents(s.getId())).containsExactly(SettlementEventType.CREATED);
        assertThat(generationService.findGroupBuyIdsToGenerate(200)).doesNotContain(groupBuy.getId());
    }

    // ------------------------------------------------------------------ 보조

    private Settlement generated() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(GENERATED_AT);
    }

    /** 세 수취자 모두 지시 가능 — 계좌 · 주민등록번호(비사업자 보류 해제). */
    private void readyToPay() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
    }
}

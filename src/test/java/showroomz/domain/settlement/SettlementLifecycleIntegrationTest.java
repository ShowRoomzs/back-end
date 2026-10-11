package showroomz.domain.settlement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.FakeSettlementPayoutGateway;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.service.SettlementConfirmService;
import showroomz.domain.settlement.service.SettlementPayoutService;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.scheduler.SettlementPayoutScheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 정산 확정 · 지급(44 어드민 설계서 3절 · 구현 계획서 5-2) — ST-05 · ST-08(1/2) · 비영업일 · 0원 행. 배치는 꺼져 있고 서비스를
 * 직접 부른다. 지급은 {@link FakeSettlementPayoutGateway}(기본 즉시 지급 완료 · 수취자별 실패를 심는다).
 */
@DisplayName("정산 확정 · 지급 — 자동 확정 · 보류 · 3자 분배 · 공구 SETTLED")
class SettlementLifecycleIntegrationTest extends SettlementTestSupport {

    /** 2026-09-30(수) 10:00 생성 → 10.01 · 10.02 · 10.05 → 확인 마감 10.05 23:59:59 → 자동 확정 10.06 00:00. */
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final LocalDateTime REVIEW_DUE = LocalDateTime.of(2026, 10, 5, 23, 59, 59);
    /** 10.06 확정 → 10.07 · 10.08 · (10.09 한글날) · 10.12. */
    private static final LocalDate PAYOUT_DUE = LocalDate.of(2026, 10, 12);

    @Autowired private SettlementConfirmService confirmService;
    @Autowired private SettlementPayoutService payoutService;
    @Autowired private FakeSettlementPayoutGateway payoutGateway;
    @Autowired private SettlementAdjustmentPort adjustmentPort;

    @BeforeEach
    void hangulDayOnly() {
        payoutGateway.reset();
        jdbc.update("INSERT INTO business_holiday (holiday_date, name) VALUES (?, ?)", LocalDate.of(2026, 10, 9), "한글날");
        businessCalendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 10, 9)));
    }

    @AfterEach
    void resetGateway() {
        payoutGateway.reset();
    }

    private Settlement generated() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement settlement = generate(GENERATED_AT);
        assertThat(settlement.getReviewDueAt()).isEqualTo(REVIEW_DUE);
        return settlement;
    }

    @Test
    @DisplayName("ST-05 마감 전 배치는 변화 없음 · 마감 후 PAYOUT_SCHEDULED · 확정 시각 = 마감 다음 날 00:00 · 지급 예정일 한글날 건너뜀")
    void autoConfirm() {
        registerCreatorResidentNumber();
        Settlement s = generated();

        assertThat(confirmService.findIdsToAutoConfirm(REVIEW_DUE, 200)).doesNotContain(s.getId());
        assertThat(confirmService.autoConfirm(s.getId(), REVIEW_DUE)).isFalse();
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);

        LocalDateTime batchAt = LocalDateTime.of(2026, 10, 6, 0, 10);
        assertThat(confirmService.findIdsToAutoConfirm(batchAt, 200)).contains(s.getId());
        assertThat(confirmService.autoConfirm(s.getId(), batchAt)).isTrue();

        Settlement confirmed = settlement(s.getId());
        assertThat(confirmed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(confirmed.getConfirmReason()).isEqualTo(SettlementConfirmReason.AUTO);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 0, 0));
        assertThat(confirmed.getPayoutDueDate()).isEqualTo(PAYOUT_DUE);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getPayee, SettlementPayout::getStatus,
                        SettlementPayout::getDueDate)
                .containsExactly(tuple(SettlementPayee.BRAND, PayoutStatus.SCHEDULED, PAYOUT_DUE),
                        tuple(SettlementPayee.CREATOR, PayoutStatus.SCHEDULED, PAYOUT_DUE),
                        tuple(SettlementPayee.PLATFORM, PayoutStatus.SCHEDULED, PAYOUT_DUE));
        assertThat(settlementHistoryRepository.findLatestFirst(s.getId()).get(0).getDetail())
                .isEqualTo("자동 확정 10.06 + 3영업일 · 10.09 한글날 제외");
        assertThat(confirmService.autoConfirm(s.getId(), batchAt)).isFalse();
    }

    @Test
    @DisplayName("ST-05 보류 — 사업자 인플루언서 · 주민번호 없는 비사업자는 인플루언서 행만 BLOCKED · 브랜드 · 플랫폼은 예정")
    void blockedCreatorPayouts() {
        Settlement individual = generated();
        confirmService.confirm(individual.getId(), SettlementConfirmReason.AUTO, individual.autoConfirmAt());
        assertThat(payout(individual.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(individual.getId(), SettlementPayee.CREATOR).getDueDate()).isNull();
        assertThat(payout(individual.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

        // 주민번호를 등록하면 지급 배치 앞단의 재판정이 푼다 — 예정일은 max(지급 예정일, 오늘).
        registerCreatorResidentNumber();
        assertThat(payoutService.releaseBlocked(LocalDate.of(2026, 10, 14))).isEqualTo(1);
        assertThat(payout(individual.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(payout(individual.getId(), SettlementPayee.CREATOR).getDueDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("ST-05 사업자 — 인플루언서 행 BLOCKED · 브랜드 먼저 지급 완료 · 정산은 PAYOUT_SCHEDULED 에 머문다")
    void businessCreatorWaitsForInvoice() {
        makeCreatorBusiness();
        registerSellerAccount();
        Settlement s = generated();
        assertThat(s.isBusinessCreator()).isTrue();
        assertThat(s.getPlatformShareAmount()).isZero();
        confirmService.confirm(s.getId(), SettlementConfirmReason.AUTO, s.autoConfirmAt());

        payoutService.distribute(s.getId(), PAYOUT_DUE, PAYOUT_DUE.atTime(10, 0));

        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getPayee, SettlementPayout::getStatus)
                .containsExactly(tuple(SettlementPayee.BRAND, PayoutStatus.PAID),
                        tuple(SettlementPayee.CREATOR, PayoutStatus.BLOCKED),
                        tuple(SettlementPayee.PLATFORM, PayoutStatus.NOT_APPLICABLE));
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
    }

    @Test
    @DisplayName("ST-08(1/2) 인플루언서만 FAILED → 정산 PAYOUT_FAILED · 브랜드 PAID / 전부 PAID → 정산 PAID · paid_at · 공구 SETTLED")
    void payoutResults() {
        registerCreatorResidentNumber();
        registerCreatorAccount();
        registerSellerAccount();
        Settlement s = generated();
        confirmService.confirm(s.getId(), SettlementConfirmReason.AUTO, s.autoConfirmAt());
        payoutGateway.failFor(SettlementPayee.CREATOR, "BANK_REJECTED", "예금주 불일치");

        LocalDateTime payAt = PAYOUT_DUE.atTime(10, 0);
        payoutService.distribute(s.getId(), PAYOUT_DUE, payAt);

        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        SettlementPayout brand = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(brand.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(brand.getPgReference()).isEqualTo("FAKE-%s-BRAND".formatted(s.getSettlementNumber()));
        assertThat(brand.getBankName()).isEqualTo("신한은행");
        assertThat(brand.getAccountNumberEnc()).isNotNull().isNotEqualTo("110123456789");
        SettlementPayout creatorPayout = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(creatorPayout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(creatorPayout.getFailCode()).isEqualTo("BANK_REJECTED");
        assertThat(creatorPayout.getAccountHolder()).isEqualTo("김지민");
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.ENDED);
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.PAYOUT_FAILED, SettlementEventType.PAYOUT_PAID);
    }

    @Test
    @DisplayName("ST-08(1/2) 전부 지급 완료 → 정산 PAID · paid_at · 공구 SETTLED · 계좌 없는 수취자는 FAILED(ACCOUNT_MISSING)")
    void allPaidSettlesGroupBuy() {
        registerCreatorResidentNumber();
        registerSellerAccount();
        Settlement s = generated();
        confirmService.confirm(s.getId(), SettlementConfirmReason.AUTO, s.autoConfirmAt());

        LocalDateTime payAt = PAYOUT_DUE.atTime(10, 0);
        payoutService.distribute(s.getId(), PAYOUT_DUE, payAt);
        // 인플루언서 계좌가 없다 — 게이트웨이를 부르지 않고 분배 실패로.
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("ACCOUNT_MISSING");
        assertThat(payoutGateway.calls()).singleElement()
                .satisfies(call -> assertThat(call.lines()).extracting(line -> line.payee())
                        .containsExactly(SettlementPayee.BRAND, SettlementPayee.PLATFORM));
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);

        // 계좌를 등록하고 실패 행을 다시 예정으로 돌리면(재분배는 단계 6 의 어드민 API) 다음 지급에서 끝난다.
        registerCreatorAccount();
        Long creatorPayoutId = payout(s.getId(), SettlementPayee.CREATOR).getId();
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE payout_id = ?",
                PAYOUT_DUE, creatorPayoutId);
        payoutService.distribute(s.getId(), PAYOUT_DUE, payAt.plusHours(1));

        Settlement paid = settlement(s.getId());
        assertThat(paid.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(paid.getPaidAt()).isEqualTo(payAt.plusHours(1));
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        assertThat(settlementEvents(s.getId())).endsWith(SettlementEventType.PAYOUT_REQUESTED,
                SettlementEventType.PAYOUT_PAID);
    }

    @Test
    @DisplayName("비영업일 — 토요일 배치는 건너뛰고 월요일에 나간다")
    void skipsNonBusinessDay() {
        registerCreatorResidentNumber();
        registerCreatorAccount();
        registerSellerAccount();
        Settlement s = generated();
        confirmService.confirm(s.getId(), SettlementConfirmReason.AUTO, s.autoConfirmAt());
        SettlementPayoutScheduler scheduler = new SettlementPayoutScheduler(payoutService, businessCalendar);

        scheduler.run(LocalDate.of(2026, 10, 17).atTime(10, 0)); // 토요일
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

        scheduler.run(LocalDate.of(2026, 10, 19).atTime(10, 0)); // 월요일
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    @DisplayName("0원 행 — 리워드 0 이면 인플루언서 · 플랫폼 행 NOT_APPLICABLE · 브랜드만 지급되어도 정산 PAID")
    void zeroAmountRows() {
        jdbc.update("UPDATE contract_item SET reward_rate = 0");
        registerSellerAccount();
        Settlement s = generated();
        assertThat(s.getRewardAmount()).isZero();
        confirmService.confirm(s.getId(), SettlementConfirmReason.AUTO, s.autoConfirmAt());
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus)
                .containsExactly(PayoutStatus.SCHEDULED, PayoutStatus.NOT_APPLICABLE, PayoutStatus.NOT_APPLICABLE);

        payoutService.distribute(s.getId(), PAYOUT_DUE, PAYOUT_DUE.atTime(10, 0));

        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
    }

    // ------------------------------------------------------------------ 조정 포트(단계 5-2)

    @Test
    @DisplayName("ST-06 포트 — 보류 → 전 수취자 행 HELD · 합의 확정 → 리워드 이하만 다시 계산 · 원래 리워드 · 확정 거래액 · 명세 불변 · HELD → SCHEDULED")
    void adjustmentPortHoldAndAgreement() {
        registerCreatorResidentNumber();
        Settlement s = generated();
        long originalReward = s.getRewardAmount();
        long agreed = originalReward + 10_000;
        List<Long> itemRewards = itemsOf(s).stream().map(i -> i.getRewardAmount()).toList();
        LocalDateTime heldAt = LocalDateTime.of(2026, 10, 2, 10, 0);

        inTransaction(() -> {
            adjustmentPort.holdForAdjustment(s.getId(), 0L, heldAt);
            return null;
        });
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.ADJUSTING);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.HELD);
        assertThat(confirmService.findIdsToAutoConfirm(LocalDateTime.of(2026, 10, 6, 0, 10), 200))
                .doesNotContain(s.getId());

        LocalDateTime agreedAt = LocalDateTime.of(2026, 10, 2, 15, 0);
        inTransaction(() -> {
            adjustmentPort.confirmByAgreement(s.getId(), 0L, agreed, agreedAt);
            return null;
        });

        Settlement after = settlement(s.getId());
        assertThat(after.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(after.getConfirmReason()).isEqualTo(SettlementConfirmReason.AGREED);
        assertThat(after.getConfirmedAt()).isEqualTo(agreedAt);
        assertThat(after.getOriginalRewardAmount()).isEqualTo(originalReward);
        assertThat(after.getRewardAmount()).isEqualTo(agreed);
        assertThat(after.getConfirmedSalesAmount()).isEqualTo(s.getConfirmedSalesAmount());
        assertThat(after.getBrandPayoutAmount()).isEqualTo(s.getBrandPayoutAmount() - 11_000);
        assertThat(itemsOf(s)).extracting(i -> i.getRewardAmount()).containsExactlyElementsOf(itemRewards);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getPayee, SettlementPayout::getStatus,
                        SettlementPayout::getAmount)
                .containsExactly(
                        tuple(SettlementPayee.BRAND, PayoutStatus.SCHEDULED, after.getBrandPayoutAmount()),
                        tuple(SettlementPayee.CREATOR, PayoutStatus.SCHEDULED, after.getCreatorPayoutAmount()),
                        tuple(SettlementPayee.PLATFORM, PayoutStatus.SCHEDULED, after.getPlatformShareAmount()));
        // 확정일(금) + 3영업일 = 수요일
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getDueDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(settlementEvents(s.getId())).containsSubsequence(SettlementEventType.ADJUSTMENT_REQUESTED,
                SettlementEventType.ADJUSTMENT_ACCEPTED, SettlementEventType.CONFIRMED_BY_AGREEMENT);
    }

    @Test
    @DisplayName("ST-07 포트 — 자동 확정이 먼저 지나간 정산의 보류는 409 WINDOW_CLOSED · 정산 그대로(만료 확정은 SA-21)")
    void adjustmentHoldAfterAutoConfirm() {
        registerCreatorResidentNumber();
        Settlement s = generated();
        assertThat(confirmService.autoConfirm(s.getId(), LocalDateTime.of(2026, 10, 6, 0, 10))).isTrue();

        assertThatThrownBy(() -> inTransaction(() -> {
            adjustmentPort.holdForAdjustment(s.getId(), 0L, LocalDateTime.of(2026, 10, 6, 0, 20));
            return null;
        })).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).doesNotContain(PayoutStatus.HELD);
    }
}

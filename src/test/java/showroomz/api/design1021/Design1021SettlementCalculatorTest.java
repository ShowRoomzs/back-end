package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.service.SettlementAmounts;
import showroomz.domain.settlement.service.SettlementCalculator;
import showroomz.domain.settlement.service.SettlementInput;
import showroomz.domain.settlement.service.SettlementRates;
import showroomz.global.error.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1021 설계서 6-1 — 분해 줄 「+ 반품 시 차감한 최초 배송비」 · 「± 운임 정산」이 산식 · 검산에 들어간다.
 */
@DisplayName("1021 6-1 — 정산 산식 · 검산(최초 배송비 · 운임 정산)")
class Design1021SettlementCalculatorTest {

    private static final SettlementRates RATES = new SettlementRates(new BigDecimal("0.03"), new BigDecimal("0.00"),
            new BigDecimal("0.10"), new BigDecimal("0.03"), new BigDecimal("0.003"));

    private final SettlementCalculator calculator = new SettlementCalculator();

    private SettlementAmounts calculate(long confirmedSales, long reward, long reship, long consumerDelivery,
                                        long brandClawback, long returnDeduction, long freight) {
        return calculator.calculate(new SettlementInput(confirmedSales, reward, reship, consumerDelivery, brandClawback,
                0, CreatorBusinessType.INDIVIDUAL, RATES, returnDeduction, freight));
    }

    @Test
    @DisplayName("브랜드 수취액 = 확정 거래액 − PG − 플랫폼 − 리워드 − 부가세 + 배송비 + 재발송비 + 최초 배송비 ± 운임 정산")
    void brandLineOrder() {
        SettlementAmounts a = calculate(1_000_000, 100_000, 6_000, 3_000, 0, 3_000, -3_000);

        long expected = 1_000_000 - 30_000 - 0 - 100_000 - 10_000 + 6_000 + 3_000 + 3_000 - 3_000;
        assertThat(a.brandPayoutAmount()).isEqualTo(expected);
        assertThat(a.returnDeductionFeeAmount()).isEqualTo(3_000);
        assertThat(a.freightAdjustmentAmount()).isEqualTo(-3_000);
        assertThatCode(() -> calculator.verify(a)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운임 정산은 부호대로 — 소비자 부담(+)은 브랜드에 더하고 환급(−)은 뺀다 · 리워드 기준(확정 거래액)은 그대로")
    void freightSignAndRewardBase() {
        SettlementAmounts plus = calculate(500_000, 50_000, 0, 0, 0, 0, 6_000);
        SettlementAmounts minus = calculate(500_000, 50_000, 0, 0, 0, 0, -6_000);
        SettlementAmounts none = calculate(500_000, 50_000, 0, 0, 0, 0, 0);

        assertThat(plus.brandPayoutAmount() - none.brandPayoutAmount()).isEqualTo(6_000);
        assertThat(none.brandPayoutAmount() - minus.brandPayoutAmount()).isEqualTo(6_000);
        assertThat(plus.creatorPayoutAmount()).isEqualTo(none.creatorPayoutAmount()).isEqualTo(minus.creatorPayoutAmount());
        assertThat(plus.pgFeeAmount()).isEqualTo(none.pgFeeAmount());
        calculator.verify(plus);
        calculator.verify(minus);
    }

    @Test
    @DisplayName("최초 배송비는 리워드 · PG 수수료 기준에 들어가지 않는다(브랜드 몫에만)")
    void returnDeductionOnlyBrand() {
        SettlementAmounts with = calculate(300_000, 30_000, 0, 0, 0, 9_000, 0);
        SettlementAmounts without = calculate(300_000, 30_000, 0, 0, 0, 0, 0);

        assertThat(with.brandPayoutAmount() - without.brandPayoutAmount()).isEqualTo(9_000);
        assertThat(with.pgFeeAmount()).isEqualTo(without.pgFeeAmount());
        assertThat(with.platformShareAmount()).isEqualTo(without.platformShareAmount());
        calculator.verify(with);
    }

    @Test
    @DisplayName("다른 회차 차감은 운임 정산이 깎은 수취액 안에서만 — 넘는 차감은 이월(0 바닥)")
    void clawbackCappedAfterFreight() {
        SettlementAmounts a = calculate(100_000, 10_000, 0, 0, 200_000, 0, -5_000);

        assertThat(a.brandPayoutAmount()).isZero();
        assertThat(a.brandClawbackAmount()).isEqualTo(100_000 - 3_000 - 10_000 - 1_000 - 5_000);
        assertThat(a.brandClawbackCarryover()).isEqualTo(200_000 - a.brandClawbackAmount());
        calculator.verify(a);
    }

    @Test
    @DisplayName("검산은 최초 배송비 · 운임 정산을 재원 쪽에 넣어야 맞는다 — 빠지면 생성을 막는다")
    void verifyFailsWhenFeeLinesMissing() {
        SettlementAmounts a = calculate(1_000_000, 100_000, 0, 0, 0, 3_000, 2_000);
        SettlementAmounts broken = new SettlementAmounts(a.rates(), a.businessType(), a.confirmedSalesAmount(),
                a.pgFeeAmount(), a.platformFeeAmount(), a.rewardAmount(), a.rewardVatAmount(), a.reshipFeeAmount(),
                a.consumerDeliveryFeeAmount(), a.brandClawbackAmount(), a.brandPayoutAmount(), a.rewardClawbackAmount(),
                a.creatorBaseAmount(), a.withholdingIncomeAmount(), a.withholdingLocalAmount(), a.withholdingAmount(),
                a.creatorVatAmount(), a.creatorPayoutAmount(), a.platformShareAmount(), a.brandClawbackCarryover(),
                a.rewardClawbackCarryover(), 0, 0);

        assertThatThrownBy(() -> calculator.verify(broken)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("조정 상한도 최초 배송비 · 운임 정산을 넣는다 — 운임 환급(−)이 있어도 상한 금액 합의가 검산을 통과한다")
    void maxRewardIncludesFeeLines() {
        // 운임 환급 −50,000 · 리워드 0 이면 브랜드 수취액 = 1,000,000 − 30,000 − 50,000 = 920,000 → 상한 = floor10(920,000 ÷ 1.1)
        Settlement minus = settlement(calculate(1_000_000, 100_000, 0, 0, 0, 0, -50_000));
        long upper = calculator.maxRewardAmount(minus);

        assertThat(upper).isEqualTo(836_360);
        SettlementAmounts atUpper = calculator.recalculateForReward(minus, upper);
        assertThat(atUpper.brandPayoutAmount()).isBetween(0L, 20L);
        // 빠뜨렸던 식(1021 이전)으로 구한 상한이면 브랜드 수취액이 음수라 합의 확정이 막힌다.
        assertThatThrownBy(() -> calculator.recalculateForReward(minus, 881_810)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("최초 배송비(+) · 소비자 부담 운임(+)은 상한을 그만큼 올린다")
    void maxRewardRaisedByPlusLines() {
        Settlement none = settlement(calculate(1_000_000, 100_000, 0, 0, 0, 0, 0));
        Settlement plus = settlement(calculate(1_000_000, 100_000, 0, 0, 0, 3_000, 8_000));

        // 11,000 ÷ 1.1 = 10,000 — 10원 절사 전후 같은 값
        assertThat(calculator.maxRewardAmount(plus) - calculator.maxRewardAmount(none)).isEqualTo(10_000);
        assertThat(calculator.recalculateForReward(plus, calculator.maxRewardAmount(plus)).brandPayoutAmount())
                .isBetween(0L, 20L);
    }

    private static Settlement settlement(SettlementAmounts amounts) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 10, 0);
        return Settlement.builder()
                .settlementNumber("STL-2610-021")
                .creatorBusinessType(amounts.businessType())
                .periodStartAt(now.minusDays(20))
                .periodEndAt(now.minusDays(10))
                .ordersClosedAt(now)
                .createdAt(now)
                .reviewDueAt(now.plusDays(3))
                .breakdown(new Settlement.Breakdown(amounts.confirmedSalesAmount(), 0, 0, 0, 0, 0, 0, 0))
                .amounts(amounts)
                .build();
    }

    @Test
    @DisplayName("1021 이전 입력(최초 배송비 · 운임 0)은 결과가 그대로다")
    void legacyInputUnchanged() {
        SettlementAmounts legacy = calculator.calculate(new SettlementInput(1_000_000, 100_000, 6_000, 3_000, 0, 0,
                CreatorBusinessType.INDIVIDUAL, RATES));
        SettlementAmounts zeroLines = calculate(1_000_000, 100_000, 6_000, 3_000, 0, 0, 0);

        assertThat(legacy.brandPayoutAmount()).isEqualTo(zeroLines.brandPayoutAmount());
        assertThat(legacy.returnDeductionFeeAmount()).isZero();
        assertThat(legacy.freightAdjustmentAmount()).isZero();
    }
}

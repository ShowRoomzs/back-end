package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.service.SettlementAmounts;
import showroomz.domain.settlement.service.SettlementCalculator;
import showroomz.domain.settlement.service.SettlementInput;
import showroomz.domain.settlement.service.SettlementRates;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정산 산식 — 44 어드민 설계서 2-4 · 2-6 · 4-1 · 4-3 의 더미 수치를 고정한다(ST-01 · ST-02). 세 화면이 같은 숫자를 보는 근거가
 * {@link SettlementCalculator} 하나라 여기서 깨지면 전부 깨진다.
 */
@DisplayName("정산 산식 — SettlementCalculator")
class SettlementCalculatorTest {

    private static final SettlementRates RATES = new SettlementRates(new BigDecimal("0.03"), new BigDecimal("0.00"),
            new BigDecimal("0.10"), new BigDecimal("0.03"), new BigDecimal("0.003"));

    private final SettlementCalculator calculator = new SettlementCalculator();

    private SettlementAmounts calculate(long confirmedSales, long reward, CreatorBusinessType type) {
        return calculator.calculate(new SettlementInput(confirmedSales, reward, 0, 0, 0, 0, type, RATES));
    }

    @Nested
    @DisplayName("ST-01 · ST-02 — 시안 더미 수치")
    class Dummy {

        @Test
        @DisplayName("ST-01 07b D1 — 5,480,000 · 리워드 10% · 비사업자")
        void d1Individual() {
            SettlementAmounts a = calculate(5_480_000, 548_000, CreatorBusinessType.INDIVIDUAL);

            assertThat(a.pgFeeAmount()).isEqualTo(164_400);
            assertThat(a.platformFeeAmount()).isZero();
            assertThat(a.rewardAmount()).isEqualTo(548_000);
            assertThat(a.rewardVatAmount()).isEqualTo(54_800);
            assertThat(a.brandPayoutAmount()).isEqualTo(4_712_800);
            assertThat(a.withholdingIncomeAmount()).isEqualTo(16_440);
            assertThat(a.withholdingLocalAmount()).isEqualTo(1_644);
            assertThat(a.withholdingAmount()).isEqualTo(18_084);
            assertThat(a.creatorPayoutAmount()).isEqualTo(529_916);
            assertThat(a.creatorVatAmount()).isZero();
            assertThat(a.platformShareAmount()).isEqualTo(54_800);
            calculator.verify(a);
        }

        @Test
        @DisplayName("ST-02 07b D6 — 사업자는 부가세를 그대로 받아 플랫폼 몫 0")
        void d6Business() {
            SettlementAmounts a = calculate(5_480_000, 548_000, CreatorBusinessType.BUSINESS);

            assertThat(a.creatorVatAmount()).isEqualTo(54_800);
            assertThat(a.creatorPayoutAmount()).isEqualTo(602_800);
            assertThat(a.withholdingAmount()).isZero();
            assertThat(a.brandPayoutAmount()).isEqualTo(4_712_800);
            assertThat(a.platformShareAmount()).isZero();
            calculator.verify(a);
        }

        @Test
        @DisplayName("ST-02 스튜디오 D1 — 6,120,000 · 리워드 15% · 비사업자 · 상한 5,396,720")
        void studioD1() {
            SettlementAmounts a = calculate(6_120_000, 918_000, CreatorBusinessType.INDIVIDUAL);

            assertThat(a.pgFeeAmount()).isEqualTo(183_600);
            assertThat(a.rewardVatAmount()).isEqualTo(91_800);
            assertThat(a.withholdingAmount()).isEqualTo(30_294);
            assertThat(a.creatorPayoutAmount()).isEqualTo(887_706);
            assertThat(a.brandPayoutAmount()).isEqualTo(4_926_600);
            assertThat(a.platformShareAmount()).isEqualTo(91_800);
            calculator.verify(a);

            assertThat(calculator.maxRewardAmount(settlement(a))).isEqualTo(5_396_720);
        }

        @Test
        @DisplayName("ST-02 파트너 C1 — 6,300,000 · 상한 5,555,450(10원 절사)")
        void partnerC1() {
            SettlementAmounts a = calculate(6_300_000, 630_000, CreatorBusinessType.INDIVIDUAL);

            assertThat(calculator.maxRewardAmount(settlement(a))).isEqualTo(5_555_450);
        }
    }

    @Nested
    @DisplayName("합의 재계산 — 리워드 이하 행만 바뀐다")
    class Recalculate {

        @Test
        @DisplayName("확정 거래액 · PG · 플랫폼 수수료 · 재발송비 · 원래 리워드는 그대로 · 검산 통과")
        void onlyRewardRows() {
            SettlementAmounts original = calculator.calculate(new SettlementInput(5_480_000, 548_000, 12_000, 6_000,
                    0, 0, CreatorBusinessType.INDIVIDUAL, RATES));
            Settlement settlement = settlement(original);

            SettlementAmounts agreed = calculator.recalculateForReward(settlement, 600_000);
            settlement.applyAmounts(agreed);

            assertThat(settlement.getConfirmedSalesAmount()).isEqualTo(5_480_000);
            assertThat(settlement.getPgFeeAmount()).isEqualTo(164_400);
            assertThat(settlement.getPlatformFeeAmount()).isZero();
            assertThat(settlement.getReshipFeeAmount()).isEqualTo(12_000);
            assertThat(settlement.getConsumerDeliveryFeeAmount()).isEqualTo(6_000);
            assertThat(settlement.getOriginalRewardAmount()).isEqualTo(548_000);
            assertThat(settlement.getRewardAmount()).isEqualTo(600_000);
            assertThat(settlement.getRewardVatAmount()).isEqualTo(60_000);
            assertThat(settlement.getBrandPayoutAmount())
                    .isEqualTo(5_480_000 - 164_400 - 600_000 - 60_000 + 12_000 + 6_000);
            assertThat(settlement.getWithholdingAmount()).isEqualTo(18_000 + 1_800);
            calculator.verify(agreed);
        }

        @Test
        @DisplayName("미리보기는 저장된 정산을 바꾸지 않는다")
        void previewIsPure() {
            Settlement settlement = settlement(calculate(5_480_000, 548_000, CreatorBusinessType.INDIVIDUAL));

            SettlementAmounts preview = calculator.preview(settlement, 300_000);

            assertThat(preview.rewardAmount()).isEqualTo(300_000);
            assertThat(settlement.getRewardAmount()).isEqualTo(548_000);
        }

        @Test
        @DisplayName("상한까지 올려도 브랜드 수취액은 0 이상")
        void upperBoundKeepsBrandNonNegative() {
            Settlement settlement = settlement(calculate(6_120_000, 918_000, CreatorBusinessType.INDIVIDUAL));
            long upper = calculator.maxRewardAmount(settlement);

            SettlementAmounts atUpper = calculator.recalculateForReward(settlement, upper);

            assertThat(atUpper.brandPayoutAmount()).isBetween(0L, 20L);
        }
    }

    @Nested
    @DisplayName("차감 — 0 바닥 · 이월 · 플랫폼 몫")
    class Clawback {

        @Test
        @DisplayName("차감 > 리워드 → creator_base 0 · 남은 차감은 이월 · 검산 통과")
        void rewardFloorsAtZero() {
            SettlementAmounts a = calculator.calculate(new SettlementInput(1_000_000, 100_000, 0, 0, 0, 130_000,
                    CreatorBusinessType.INDIVIDUAL, RATES));

            assertThat(a.rewardClawbackAmount()).isEqualTo(100_000);
            assertThat(a.creatorBaseAmount()).isZero();
            assertThat(a.creatorPayoutAmount()).isZero();
            assertThat(a.withholdingAmount()).isZero();
            assertThat(a.rewardClawbackCarryover()).isEqualTo(30_000);
            calculator.verify(a);
        }

        @Test
        @DisplayName("사업자 차감 — 플랫폼 몫에 리워드 부가세 − 인플루언서 부가세가 남는다")
        void businessVatResidual() {
            SettlementAmounts a = calculator.calculate(new SettlementInput(5_480_000, 548_000, 0, 0, 0, 48_000,
                    CreatorBusinessType.BUSINESS, RATES));

            assertThat(a.creatorBaseAmount()).isEqualTo(500_000);
            assertThat(a.creatorVatAmount()).isEqualTo(50_000);
            assertThat(a.creatorPayoutAmount()).isEqualTo(550_000);
            assertThat(a.platformShareAmount()).isEqualTo((54_800 - 50_000) + 48_000);
            calculator.verify(a);
        }

        @Test
        @DisplayName("브랜드 차감 — 수취액에서 빠지고 플랫폼 몫으로 · 0 미만이면 이월")
        void brandClawback() {
            SettlementAmounts a = calculator.calculate(new SettlementInput(100_000, 10_000, 0, 0, 200_000, 0,
                    CreatorBusinessType.INDIVIDUAL, RATES));

            long before = 100_000 - 3_000 - 10_000 - 1_000;
            assertThat(a.brandClawbackAmount()).isEqualTo(before);
            assertThat(a.brandPayoutAmount()).isZero();
            assertThat(a.brandClawbackCarryover()).isEqualTo(200_000 - before);
            assertThat(a.platformShareAmount()).isEqualTo(1_000 + before);
            calculator.verify(a);
        }
    }

    @Nested
    @DisplayName("절사 — 전 항목 DOWN · 소득세 · 지방소득세 각각")
    class Rounding {

        @Test
        @DisplayName("1,234,567 — PG · 부가세 · 원천징수 각각 절사")
        void truncates() {
            SettlementAmounts a = calculate(12_345_678, 1_234_567, CreatorBusinessType.INDIVIDUAL);

            assertThat(a.pgFeeAmount()).isEqualTo(370_370);          // 370,370.34
            assertThat(a.rewardVatAmount()).isEqualTo(123_456);      // 123,456.7
            assertThat(a.withholdingIncomeAmount()).isEqualTo(37_037); // 37,037.01
            assertThat(a.withholdingLocalAmount()).isEqualTo(3_703);   // 3,703.701
            assertThat(a.withholdingAmount()).isEqualTo(40_740);
            calculator.verify(a);
        }

        @Test
        @DisplayName("항목 리워드 단가는 RewardCalculator 와 같다 — 24,000 × 15% = 3,600 · 27,200 × 12.5% 절사")
        void unitReward() {
            assertThat(calculator.unitReward(24_000, new BigDecimal("15.0"))).isEqualTo(3_600);
            assertThat(calculator.unitReward(27_210, new BigDecimal("12.5"))).isEqualTo(3_401);
        }
    }

    @Test
    @DisplayName("검산 불일치 → SETTLEMENT_GENERATION_INCONSISTENT")
    void verifyFails() {
        SettlementAmounts a = calculate(5_480_000, 548_000, CreatorBusinessType.INDIVIDUAL);
        SettlementAmounts broken = new SettlementAmounts(a.rates(), a.businessType(), a.confirmedSalesAmount(),
                a.pgFeeAmount(), a.platformFeeAmount(), a.rewardAmount(), a.rewardVatAmount(), a.reshipFeeAmount(),
                a.consumerDeliveryFeeAmount(), a.brandClawbackAmount(), a.brandPayoutAmount() + 1,
                a.rewardClawbackAmount(), a.creatorBaseAmount(), a.withholdingIncomeAmount(),
                a.withholdingLocalAmount(), a.withholdingAmount(), a.creatorVatAmount(), a.creatorPayoutAmount(),
                a.platformShareAmount(), 0, 0);

        assertThatThrownBy(() -> calculator.verify(broken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_GENERATION_INCONSISTENT);
    }

    private static Settlement settlement(SettlementAmounts amounts) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 10, 0);
        return Settlement.builder()
                .settlementNumber("STL-2610-001")
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
}

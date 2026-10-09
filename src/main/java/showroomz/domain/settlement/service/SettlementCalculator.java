package showroomz.domain.settlement.service;

import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.RewardCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 정산 산식의 단일 소유자(44 어드민 설계서 0-6 · 2-4 · 2-6 · 4-1 · 4-3) — 세 화면이 같은 숫자를 보는 근거가 이 클래스 하나다.
 * 성과 관리 · 조정 미리보기도 따로 계산하지 않고 여기를 부른다.
 *
 * <p>원 단위는 <b>전 항목 절사</b>(공통 결정 #5) — 원천징수는 소득세 · 지방소득세를 <b>각각</b> 절사하고, 조정 상한만 10원 미만을
 * 버린다. 세무 확인에서 뒤집히면 {@link #ROUNDING} 하나다(13절 B-2). 리워드 단가는 계약서 PDF 와 같은 값이어야 하므로
 * {@link RewardCalculator#calcUnitReward}를 그대로 쓴다.
 *
 * <p>상태가 없다 — 스프링 없이 단위 테스트한다.
 */
@Component
public class SettlementCalculator {

    /** 원 단위 처리 — 전 항목 절사. 음수에서도 0 쪽으로 버린다(RewardCalculator 와 같은 이유). */
    public static final RoundingMode ROUNDING = RoundingMode.DOWN;

    // ------------------------------------------------------------------ 항목 리워드(2-3)

    /** 항목 단가 리워드 — 1원 절사 · 계약서와 같은 값. */
    public long unitReward(long unitPrice, BigDecimal rewardRate) {
        return RewardCalculator.calcUnitReward(Math.toIntExact(unitPrice), rewardRate);
    }

    // ------------------------------------------------------------------ 산식(2-4)

    public SettlementAmounts calculate(SettlementInput in) {
        SettlementRates rates = in.rates();
        long confirmedSales = in.confirmedSalesAmount();
        long reward = in.rewardAmount();

        long pgFee = apply(confirmedSales, rates.pgFeeRate());
        long platformFee = apply(confirmedSales, rates.platformFeeRate());
        long rewardVat = apply(reward, rates.rewardVatRate());

        long brandBeforeClawback = confirmedSales - pgFee - platformFee - reward - rewardVat
                + in.reshipFeeAmount() + in.consumerDeliveryFeeAmount();
        // 브랜드 수취액은 0 아래로 내려가지 않는다 — 남은 차감은 다음 정산으로 이월(6-2).
        long brandClawback = Math.min(Math.max(0, in.brandClawbackPending()), Math.max(0, brandBeforeClawback));
        long brandPayout = brandBeforeClawback - brandClawback;

        // 리워드 차감도 0 바닥 — 실지급 음수가 분배로 나가지 않는다(13절 B-10).
        long rewardClawback = Math.min(Math.max(0, in.rewardClawbackPending()), Math.max(0, reward));
        long creatorBase = reward - rewardClawback;

        long withholdingIncome = 0;
        long withholdingLocal = 0;
        long creatorVat = 0;
        long creatorPayout;
        if (in.businessType() == CreatorBusinessType.BUSINESS) {
            creatorVat = apply(creatorBase, rates.rewardVatRate());
            creatorPayout = creatorBase + creatorVat;
        } else {
            withholdingIncome = apply(creatorBase, rates.withholdingIncomeRate());
            withholdingLocal = apply(creatorBase, rates.withholdingLocalRate());
            creatorPayout = creatorBase - withholdingIncome - withholdingLocal;
        }
        long withholding = withholdingIncome + withholdingLocal;
        // 사업자 건은 리워드 부가세를 그대로 넘기므로 차감이 없으면 0 — 차감이 있으면 회수된 리워드분의 부가세가 남는다.
        long platformShare = platformFee + (rewardVat - creatorVat) + brandClawback + rewardClawback;

        return new SettlementAmounts(rates, in.businessType(), confirmedSales, pgFee, platformFee, reward, rewardVat,
                in.reshipFeeAmount(), in.consumerDeliveryFeeAmount(), brandClawback, brandPayout, rewardClawback,
                creatorBase, withholdingIncome, withholdingLocal, withholding, creatorVat, creatorPayout, platformShare,
                Math.max(0, in.brandClawbackPending()) - brandClawback,
                Math.max(0, in.rewardClawbackPending()) - rewardClawback);
    }

    /**
     * 검산(2-6) — 생성 트랜잭션 안에서 <b>반드시</b> 맞아야 한다. 어긋나면 생성을 롤백한다 — 틀린 정산이 양측에 공개되는 것보다
     * 한 회차 늦는 쪽이 낫다. 차감은 양변에서 상쇄되어 식에 따로 쓰지 않는다.
     *
     * <pre>confirmed_sales + reship_fee + consumer_delivery_fee
     *   = brand_payout + creator_payout + withholding + platform_share + pg_fee</pre>
     */
    public void verify(SettlementAmounts a) {
        long left = a.confirmedSalesAmount() + a.reshipFeeAmount() + a.consumerDeliveryFeeAmount();
        long right = a.brandPayoutAmount() + a.creatorPayoutAmount() + a.withholdingAmount()
                + a.platformShareAmount() + a.pgFeeAmount();
        if (left != right) {
            throw new BusinessException(ErrorCode.SETTLEMENT_GENERATION_INCONSISTENT,
                    "정산 검산 불일치 — 재원 %d · 분배 %d".formatted(left, right));
        }
        if (a.brandPayoutAmount() < 0 || a.creatorPayoutAmount() < 0 || a.platformShareAmount() < 0
                || a.withholdingAmount() < 0 || a.rewardAmount() < 0) {
            throw new BusinessException(ErrorCode.SETTLEMENT_GENERATION_INCONSISTENT,
                    "정산 금액 음수 — 브랜드 %d · 인플루언서 %d · 플랫폼 %d".formatted(
                            a.brandPayoutAmount(), a.creatorPayoutAmount(), a.platformShareAmount()));
        }
    }

    // ------------------------------------------------------------------ 저장된 정산 기준(4-1 · 4-3)

    /** 저장된 정산의 산식 입력 — 요율 · 차감은 행의 스냅샷이다. 차감은 이미 반영된 값을 그대로 다시 준다. */
    public SettlementInput inputOf(Settlement s, long rewardAmount) {
        return new SettlementInput(s.getConfirmedSalesAmount(), rewardAmount, s.getReshipFeeAmount(),
                s.getConsumerDeliveryFeeAmount(), s.getBrandClawbackAmount(), s.getRewardClawbackAmount(),
                s.getCreatorBusinessType(), ratesOf(s));
    }

    public SettlementRates ratesOf(Settlement s) {
        return new SettlementRates(s.getPgFeeRate(), s.getPlatformFeeRate(), s.getRewardVatRate(),
                s.getWithholdingIncomeRate(), s.getWithholdingLocalRate());
    }

    /**
     * 조정 미리보기(4-2 {@code preview}) — 같은 입력에 리워드만 바꾼다. 모달의 「실지급 · 브랜드 수취액 자동 계산」이 정산 화면과
     * 같은 숫자를 낸다.
     */
    public SettlementAmounts preview(Settlement settlement, long rewardAmount) {
        return calculate(inputOf(settlement, rewardAmount));
    }

    /**
     * 합의 후 재계산(4-3) — 리워드 <b>이하</b> 행만 다시 계산한다. 확정 거래액 · PG · 플랫폼 수수료 · 재발송비 · 소비자 배송비 ·
     * {@code original_reward_amount} · 명세는 그대로다. 검산을 통과한 값만 돌려준다.
     *
     * <p>합의 금액이 이미 반영된 리워드 차감보다 작으면 남는 차감이 {@link SettlementAmounts#rewardClawbackCarryover()}로 나온다 —
     * 호출자가 다음 정산으로 넘긴다(6-2).
     */
    public SettlementAmounts recalculateForReward(Settlement settlement, long rewardAmount) {
        SettlementAmounts amounts = preview(settlement, rewardAmount);
        verify(amounts);
        return amounts;
    }

    /**
     * 조정 상한(4-1) — 「조정 후 브랜드 수취액 ≥ 0」을 식으로 옮긴 것 · 리워드 부가세 포함 · 10원 미만 절사.
     *
     * <pre>floor10((confirmed_sales − pg − platform + reship + consumer_delivery − brand_clawback) ÷ (1 + reward_vat_rate))</pre>
     */
    public long maxRewardAmount(Settlement s) {
        long base = s.getConfirmedSalesAmount() - s.getPgFeeAmount() - s.getPlatformFeeAmount()
                + s.getReshipFeeAmount() + s.getConsumerDeliveryFeeAmount() - s.getBrandClawbackAmount();
        if (base <= 0) {
            return 0;
        }
        long upper = BigDecimal.valueOf(base)
                .divide(BigDecimal.ONE.add(s.getRewardVatRate()), 0, ROUNDING)
                .longValueExact();
        return upper - upper % 10;
    }

    private static long apply(long amount, BigDecimal rate) {
        if (amount == 0 || rate == null || rate.signum() == 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, ROUNDING).longValueExact();
    }
}

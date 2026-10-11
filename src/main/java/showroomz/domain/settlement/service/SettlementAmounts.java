package showroomz.domain.settlement.service;

import showroomz.domain.member.creator.type.CreatorBusinessType;

/**
 * 산식 결과(44 어드민 설계서 2-4) — 정산 행의 금액 컬럼과 1:1. {@link SettlementCalculator#verify}가 2-6 검산을 본다.
 *
 * @param brandClawbackAmount     이번 정산에 실제 반영한 브랜드 측 차감(0 바닥 · 남은 금액은 {@link #brandClawbackCarryover})
 * @param rewardClawbackAmount    이번 정산에 실제 반영한 인플루언서 측 차감(리워드 한도 · 남은 금액은 {@link #rewardClawbackCarryover})
 * @param creatorBaseAmount       차감 후 리워드 = 원천징수 · 인플루언서 부가세의 기준
 * @param brandClawbackCarryover  다음 정산으로 넘길 브랜드 측 차감(6-2 이월)
 * @param rewardClawbackCarryover 다음 정산으로 넘길 인플루언서 측 차감(6-2 이월)
 */
public record SettlementAmounts(SettlementRates rates, CreatorBusinessType businessType,
                                long confirmedSalesAmount, long pgFeeAmount, long platformFeeAmount,
                                long rewardAmount, long rewardVatAmount, long reshipFeeAmount,
                                long consumerDeliveryFeeAmount, long brandClawbackAmount, long brandPayoutAmount,
                                long rewardClawbackAmount, long creatorBaseAmount, long withholdingIncomeAmount,
                                long withholdingLocalAmount, long withholdingAmount, long creatorVatAmount,
                                long creatorPayoutAmount, long platformShareAmount,
                                long brandClawbackCarryover, long rewardClawbackCarryover) {

    /** 「차감 전 브랜드 수취액」 — 07b D3-b · 파트너 분해. */
    public long brandPayoutBeforeClawback() {
        return brandPayoutAmount + brandClawbackAmount;
    }
}

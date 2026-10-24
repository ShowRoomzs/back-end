package showroomz.domain.settlement.service;

import showroomz.domain.member.creator.type.CreatorBusinessType;

/**
 * 산식 입력(44 어드민 설계서 2-4) — 집계가 끝난 금액만 받는다. 명세 행 집계는 생성 서비스의 몫이다.
 *
 * @param confirmedSalesAmount      확정 거래액(배송비 제외)
 * @param rewardAmount              리워드(공급가) — 항목별 {@code unit_reward × settled_quantity}의 합 또는 합의 금액
 * @param reshipFeeAmount           교환 · 반려 재발송비(소비자 부담분) — 브랜드 가산
 * @param consumerDeliveryFeeAmount 소비자 결제 배송비 — 브랜드 가산(13절 B-6)
 * @param brandClawbackPending      이번 정산에서 회수할 브랜드 측 차감 합(이월 전)
 * @param rewardClawbackPending     이번 정산에서 회수할 인플루언서 측 차감 합(이월 전)
 * @param returnDeductionFeeAmount  반품 시 차감한 최초 배송비(무료배송 · 소비자 귀책) — 브랜드 가산(1021 기-18 · 설계서 6-1)
 * @param freightAdjustmentAmount   귀책 변경 운임 정산 — 부호 있음(소비자 부담 +, 소비자 환급 −) · 브랜드 가감(1021 기-93)
 */
public record SettlementInput(long confirmedSalesAmount, long rewardAmount, long reshipFeeAmount,
                              long consumerDeliveryFeeAmount, long brandClawbackPending, long rewardClawbackPending,
                              CreatorBusinessType businessType, SettlementRates rates,
                              long returnDeductionFeeAmount, long freightAdjustmentAmount) {

    /** 1021 이전 산식 입력 — 최초 배송비 · 운임 정산 0. */
    public SettlementInput(long confirmedSalesAmount, long rewardAmount, long reshipFeeAmount,
                           long consumerDeliveryFeeAmount, long brandClawbackPending, long rewardClawbackPending,
                           CreatorBusinessType businessType, SettlementRates rates) {
        this(confirmedSalesAmount, rewardAmount, reshipFeeAmount, consumerDeliveryFeeAmount, brandClawbackPending,
                rewardClawbackPending, businessType, rates, 0, 0);
    }
}

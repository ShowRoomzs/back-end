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
 */
public record SettlementInput(long confirmedSalesAmount, long rewardAmount, long reshipFeeAmount,
                              long consumerDeliveryFeeAmount, long brandClawbackPending, long rewardClawbackPending,
                              CreatorBusinessType businessType, SettlementRates rates) {
}

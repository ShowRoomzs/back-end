package showroomz.domain.settlement.service;

import java.math.BigDecimal;

/**
 * 정산 요율 스냅샷(44 어드민 설계서 0-3 · 9-1) — 생성 시점의 설정값을 정산 행에 적고, 지난 정산은 그때 요율로 읽힌다.
 *
 * @param pgFeeRate             PG 수수료율(잠정 3.0% · [자문대기-PG])
 * @param platformFeeRate       플랫폼 수수료율(베타 0 · 0원이어도 행을 남긴다)
 * @param rewardVatRate         리워드 부가세율 10%
 * @param withholdingIncomeRate 원천징수 소득세율 3%
 * @param withholdingLocalRate  원천징수 지방소득세율 0.3%
 * @param rewardVatInclusive    리워드 = 부가세 포함(2026-11-10) — 브랜드 별도 부가세 없음 · 사업자 부가세는 리워드 안에서 나뉜다.
 *                              false 는 그 전 정산(리워드 + 부가세 10% 를 브랜드가 따로 부담)
 */
public record SettlementRates(BigDecimal pgFeeRate, BigDecimal platformFeeRate, BigDecimal rewardVatRate,
                              BigDecimal withholdingIncomeRate, BigDecimal withholdingLocalRate,
                              boolean rewardVatInclusive) {

    /** 부가세 포함 규칙 이전 정산(별도 부담) — 기존 행 · 단위 테스트용. */
    public SettlementRates(BigDecimal pgFeeRate, BigDecimal platformFeeRate, BigDecimal rewardVatRate,
                           BigDecimal withholdingIncomeRate, BigDecimal withholdingLocalRate) {
        this(pgFeeRate, platformFeeRate, rewardVatRate, withholdingIncomeRate, withholdingLocalRate, false);
    }
}

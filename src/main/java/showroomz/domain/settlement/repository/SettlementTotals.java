package showroomz.domain.settlement.repository;

/** 어드민 07a 합계 행 · 툴바 — 조건에 맞는 정산의 금액 합(정산 행 스냅샷). */
public record SettlementTotals(long count, long confirmedSalesAmount, long brandPayoutAmount, long pgFeeAmount,
                               long creatorPayoutAmount, long withholdingAmount, long creatorVatAmount,
                               long rewardVatAmount, long platformFeeAmount) {

    public static final SettlementTotals EMPTY = new SettlementTotals(0, 0, 0, 0, 0, 0, 0, 0, 0);
}

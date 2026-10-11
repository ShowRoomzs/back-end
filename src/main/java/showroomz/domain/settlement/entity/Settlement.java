package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.service.SettlementAmounts;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 회차 — 공구 1건 = 정산 1건(44 어드민 설계서 0-1 · 1-2). 세 서피스(어드민 07a · 07b / 파트너 13 / 스튜디오 12)가
 * <b>같은 행</b>을 읽는다.
 *
 * <p>금액은 생성 시점의 스냅샷이다(0-3) — 분해 표의 행 하나가 컬럼 하나이고 적용 요율도 같이 적는다. 조정 합의로 리워드가 바뀌면
 * 리워드 이하 컬럼만 다시 쓰고 {@link #originalRewardAmount}는 그대로 둔다(4-3).
 *
 * <p>상태 전이의 경합 차단은 리포지토리 조건부 UPDATE({@code SettlementRepository#transition})가 먼저 하고, 이 클래스는 같은
 * 트랜잭션에서 딸린 필드를 채운다 — 공구({@code GroupBuy})와 같은 방식이다.
 */
@Entity
@Table(name = "settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long id;

    @Column(name = "settlement_number", nullable = false, length = 16, unique = true)
    private String settlementNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false, unique = true, updatable = false)
    private GroupBuy groupBuy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false, updatable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private Creator creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    /** 생성 시점 스냅샷 — 생성 뒤 온보딩 정보가 바뀌어도 이미 생긴 정산의 세무 처리는 바뀌지 않는다(0-9). */
    @Enumerated(EnumType.STRING)
    @Column(name = "creator_business_type", nullable = false, length = 16)
    private CreatorBusinessType creatorBusinessType;

    // ── 대상 기간 · 사건 시각 ─────────────────────────────────────────────

    @Column(name = "period_start_at", nullable = false)
    private LocalDateTime periodStartAt;

    @Column(name = "period_end_at", nullable = false)
    private LocalDateTime periodEndAt;

    @Column(name = "orders_closed_at", nullable = false)
    private LocalDateTime ordersClosedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "review_due_at", nullable = false)
    private LocalDateTime reviewDueAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirm_reason", length = 10)
    private SettlementConfirmReason confirmReason;

    @Column(name = "payout_due_date")
    private LocalDate payoutDueDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ── 브랜드 축 ────────────────────────────────────────────────────────

    @Column(name = "gross_order_amount", nullable = false)
    private long grossOrderAmount;

    @Column(name = "cancel_deduction", nullable = false)
    private long cancelDeduction;

    @Column(name = "cancel_count", nullable = false)
    private int cancelCount;

    @Column(name = "return_deduction", nullable = false)
    private long returnDeduction;

    @Column(name = "return_count", nullable = false)
    private int returnCount;

    @Column(name = "delivery_exception_deduction", nullable = false)
    private long deliveryExceptionDeduction;

    @Column(name = "delivery_exception_count", nullable = false)
    private int deliveryExceptionCount;

    @Column(name = "confirmed_sales_amount", nullable = false)
    private long confirmedSalesAmount;

    @Column(name = "pg_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal pgFeeRate;

    @Column(name = "pg_fee_amount", nullable = false)
    private long pgFeeAmount;

    @Column(name = "platform_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal platformFeeRate;

    @Column(name = "platform_fee_amount", nullable = false)
    private long platformFeeAmount;

    @Column(name = "original_reward_amount", nullable = false, updatable = false)
    private long originalRewardAmount;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Column(name = "reward_vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal rewardVatRate;

    @Column(name = "reward_vat_amount", nullable = false)
    private long rewardVatAmount;

    @Column(name = "reship_fee_amount", nullable = false)
    private long reshipFeeAmount;

    @Column(name = "reship_count", nullable = false)
    private int reshipCount;

    @Column(name = "consumer_delivery_fee_amount", nullable = false)
    private long consumerDeliveryFeeAmount;

    @Column(name = "brand_clawback_amount", nullable = false)
    private long brandClawbackAmount;

    @Column(name = "brand_payout_amount", nullable = false)
    private long brandPayoutAmount;

    // ── 인플루언서 축 ────────────────────────────────────────────────────

    @Column(name = "reward_clawback_amount", nullable = false)
    private long rewardClawbackAmount;

    @Column(name = "withholding_income_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal withholdingIncomeRate;

    @Column(name = "withholding_local_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal withholdingLocalRate;

    @Column(name = "withholding_amount", nullable = false)
    private long withholdingAmount;

    @Column(name = "creator_vat_amount", nullable = false)
    private long creatorVatAmount;

    @Column(name = "creator_payout_amount", nullable = false)
    private long creatorPayoutAmount;

    // ── 플랫폼 ───────────────────────────────────────────────────────────

    @Column(name = "platform_share_amount", nullable = false)
    private long platformShareAmount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 생성(2-7) — 상태 {@code REVIEWING}. 금액은 {@link SettlementAmounts}(검산을 통과한 값)를 그대로 옮긴다.
     *
     * @param breakdown 차감 전 분해 행(총 주문 · 취소 · 반품 · 배송 예외 · 재발송비 건수)
     */
    @Builder
    private Settlement(String settlementNumber, GroupBuy groupBuy, Contract contract, Market market, Creator creator,
                       CreatorBusinessType creatorBusinessType, LocalDateTime periodStartAt, LocalDateTime periodEndAt,
                       LocalDateTime ordersClosedAt, LocalDateTime createdAt, LocalDateTime reviewDueAt,
                       Breakdown breakdown, SettlementAmounts amounts) {
        this.settlementNumber = settlementNumber;
        this.groupBuy = groupBuy;
        this.contract = contract;
        this.market = market;
        this.creator = creator;
        this.status = SettlementStatus.REVIEWING;
        this.creatorBusinessType = creatorBusinessType;
        this.periodStartAt = periodStartAt;
        this.periodEndAt = periodEndAt;
        this.ordersClosedAt = ordersClosedAt;
        this.createdAt = createdAt;
        this.reviewDueAt = reviewDueAt;
        this.grossOrderAmount = breakdown.grossOrderAmount();
        this.cancelDeduction = breakdown.cancelDeduction();
        this.cancelCount = breakdown.cancelCount();
        this.returnDeduction = breakdown.returnDeduction();
        this.returnCount = breakdown.returnCount();
        this.deliveryExceptionDeduction = breakdown.deliveryExceptionDeduction();
        this.deliveryExceptionCount = breakdown.deliveryExceptionCount();
        this.reshipCount = breakdown.reshipCount();
        this.confirmedSalesAmount = amounts.confirmedSalesAmount();
        this.pgFeeRate = amounts.rates().pgFeeRate();
        this.platformFeeRate = amounts.rates().platformFeeRate();
        this.rewardVatRate = amounts.rates().rewardVatRate();
        this.withholdingIncomeRate = amounts.rates().withholdingIncomeRate();
        this.withholdingLocalRate = amounts.rates().withholdingLocalRate();
        this.pgFeeAmount = amounts.pgFeeAmount();
        this.platformFeeAmount = amounts.platformFeeAmount();
        this.originalRewardAmount = amounts.rewardAmount();
        this.reshipFeeAmount = amounts.reshipFeeAmount();
        this.consumerDeliveryFeeAmount = amounts.consumerDeliveryFeeAmount();
        applyAmounts(amounts);
    }

    /** 분해 행 중 산식 입력이 아닌 것 — 건수 · 차감 전 총액. */
    public record Breakdown(long grossOrderAmount, long cancelDeduction, int cancelCount, long returnDeduction,
                            int returnCount, long deliveryExceptionDeduction, int deliveryExceptionCount,
                            int reshipCount) {
    }

    // ── 파생값 ───────────────────────────────────────────────────────────

    public Long getGroupBuyId() {
        return groupBuy == null ? null : groupBuy.getId();
    }

    public Long getMarketId() {
        return market == null ? null : market.getId();
    }

    public Long getCreatorId() {
        return creator == null ? null : creator.getId();
    }

    public Long getContractId() {
        return contract == null ? null : contract.getId();
    }

    public boolean isBusinessCreator() {
        return creatorBusinessType == CreatorBusinessType.BUSINESS;
    }

    /** 「차감 전 브랜드 수취액」 — 열을 따로 두지 않는다(1-2 · 07b D3-b). */
    public long getBrandPayoutBeforeClawback() {
        return brandPayoutAmount + brandClawbackAmount;
    }

    /** 「차감 후 리워드」 — 원천징수 · 인플루언서 부가세의 기준(2-4). */
    public long getRewardAfterClawback() {
        return Math.max(0, rewardAmount - rewardClawbackAmount);
    }

    /** 조정 요청 창 — 상태 · 마감만 본다. 협의가 이미 있는지는 이슈 스레드 모듈의 리더가 더한다(3-1). */
    public boolean isInReviewWindow(LocalDateTime now) {
        return status == SettlementStatus.REVIEWING && !now.isAfter(reviewDueAt);
    }

    /** 자동 확정의 확정 시각 — 마감 다음 날 00:00:00(규칙값 · 0-10). 배치가 늦어도 지급 예정일이 밀리지 않는다. */
    public LocalDateTime autoConfirmAt() {
        return reviewDueAt.toLocalDate().plusDays(1).atStartOfDay();
    }

    // ── 상태 전이에 딸린 필드 ────────────────────────────────────────────

    /** 리워드 이하 컬럼 — 생성과 합의 재계산(4-3)이 같은 메서드로 쓴다. 확정 거래액 · 수수료는 건드리지 않는다. */
    public void applyAmounts(SettlementAmounts amounts) {
        this.rewardAmount = amounts.rewardAmount();
        this.rewardVatAmount = amounts.rewardVatAmount();
        this.brandClawbackAmount = amounts.brandClawbackAmount();
        this.brandPayoutAmount = amounts.brandPayoutAmount();
        this.rewardClawbackAmount = amounts.rewardClawbackAmount();
        this.withholdingAmount = amounts.withholdingAmount();
        this.creatorVatAmount = amounts.creatorVatAmount();
        this.creatorPayoutAmount = amounts.creatorPayoutAmount();
        this.platformShareAmount = amounts.platformShareAmount();
    }

    public void applyAdjusting() {
        this.status = SettlementStatus.ADJUSTING;
    }

    public void applyConfirmed(SettlementConfirmReason reason, LocalDateTime confirmedAt, LocalDate payoutDueDate) {
        this.status = SettlementStatus.PAYOUT_SCHEDULED;
        this.confirmReason = reason;
        this.confirmedAt = confirmedAt;
        this.payoutDueDate = payoutDueDate;
    }

    /** 수취자 행에서 파생한 상태 — PAYOUT_SCHEDULED ↔ PAYOUT_FAILED(0-4). PAID 는 {@link #applyPaid}. */
    public void applyDerivedStatus(SettlementStatus status) {
        this.status = status;
    }

    public void applyPaid(LocalDateTime paidAt) {
        this.status = SettlementStatus.PAID;
        this.paidAt = paidAt;
    }
}

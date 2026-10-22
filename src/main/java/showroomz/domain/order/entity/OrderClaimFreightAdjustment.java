package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.FreightAdjustmentDirection;
import showroomz.domain.order.type.FreightAdjustmentTrigger;
import showroomz.domain.order.type.FreightSettledVia;

import java.time.LocalDateTime;

/**
 * 귀책 변경 운임 정산 1건(1021 설계서 4-1 · 결정 노트 기-93 · 보완).
 *
 * <p>검수 · 이의 처리에서 귀책이 바뀌면 소비자에게 청구 · 환급하는 운임과 브랜드 정산의 「± 운임 정산」을 <b>이 행 하나</b>가 든다 — 어드민 06b B2 ·
 * 06c M3 · 07b D3 · 소비자 C10-5 가 「같은 산식」이라고 한 것을 저장으로 보장한다. 운임은 전부 그 상품의 기본 배송비(편도) 정액이다.
 *
 * <p>소비자와의 결제가 추가 결제({@link FreightSettledVia#CHARGE})로 끝난 운임은 재발송비 결제({@code order_claim_charge})로 정산의
 * 「+ 재발송비」에 이미 들어가므로 「± 운임 정산」 합에서 뺀다.
 */
@Entity
@Table(name = "order_claim_freight_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderClaimFreightAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adjustment_id")
    private Long id;

    @Column(name = "claim_collection_id", nullable = false)
    private Long claimCollectionId;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(name = "delivery_group_id", nullable = false)
    private Long deliveryGroupId;

    @Column(name = "group_buy_id")
    private Long groupBuyId;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private FreightAdjustmentTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private FreightAdjustmentDirection direction;

    /** 착불 운임(정액) — 소비자 부담. */
    @Column(name = "collect_freight", nullable = false)
    private int collectFreight;

    /** 선불 운임 환급(정액) — 소비자 환급. */
    @Column(name = "prepaid_freight", nullable = false)
    private int prepaidFreight;

    /** 차감했던 최초 배송비 환원 — 소비자 환급. */
    @Column(name = "initial_fee_restored", nullable = false)
    private int initialFeeRestored;

    /** 합계(양수) — 부호는 {@link #direction}. */
    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settled_via", nullable = false, length = 16)
    private FreightSettledVia settledVia;

    @Column(name = "refund_task_id")
    private Long refundTaskId;

    @Column(name = "charge_id")
    private Long chargeId;

    @Column(name = "applied_settlement_id")
    private Long appliedSettlementId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public OrderClaimFreightAdjustment(Long claimCollectionId, Long claimId, Long deliveryGroupId, Long groupBuyId,
                                       Long marketId, FreightAdjustmentTrigger trigger,
                                       FreightAdjustmentDirection direction, int collectFreight, int prepaidFreight,
                                       int initialFeeRestored, FreightSettledVia settledVia, Long refundTaskId,
                                       Long chargeId, LocalDateTime createdAt) {
        this.claimCollectionId = claimCollectionId;
        this.claimId = claimId;
        this.deliveryGroupId = deliveryGroupId;
        this.groupBuyId = groupBuyId;
        this.marketId = marketId;
        this.trigger = trigger;
        this.direction = direction;
        this.collectFreight = collectFreight;
        this.prepaidFreight = prepaidFreight;
        this.initialFeeRestored = initialFeeRestored;
        // 정산 금액은 운임만이다 — 차감했던 최초 배송비 환원은 요청의 차감액을 0으로 되돌려 「+ 반품 시 차감한 최초 배송비」에서 빠지므로
        // 여기서 다시 빼면 이중이다(1021 설계서 6-1). initial_fee_restored 는 표시용.
        this.amount = collectFreight + prepaidFreight;
        this.settledVia = settledVia;
        this.refundTaskId = refundTaskId;
        this.chargeId = chargeId;
        this.createdAt = createdAt;
    }

    /** 브랜드 정산의 부호 있는 금액. */
    public long signedAmount() {
        return direction.signed(amount);
    }

    public void settleVia(FreightSettledVia via, Long refundTaskId, Long chargeId) {
        this.settledVia = via;
        this.refundTaskId = refundTaskId;
        this.chargeId = chargeId;
    }

    public void markApplied(Long settlementId) {
        this.appliedSettlementId = settlementId;
    }
}

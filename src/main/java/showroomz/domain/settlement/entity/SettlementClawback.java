package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.ClawbackStatus;
import showroomz.domain.settlement.type.ClawbackUnrecoverableReason;

import java.time.LocalDateTime;

/**
 * 차감 1행 — 한 환불의 한 측(44 어드민 설계서 1-7 · 6절). 한 환불은 측별 2행이고 이월은 같은 번호의 seq + 1 행이다.
 *
 * <p><b>상태 전이는 리포지토리 조건부 UPDATE 로만 바꾼다</b>({@code SettlementClawbackRepository}) — 이 클래스에는 생성 외의 쓰기
 * 메서드가 없다.
 */
@Entity
@Table(name = "settlement_clawback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementClawback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clawback_id")
    private Long id;

    @Column(name = "clawback_number", nullable = false, length = 16)
    private String clawbackNumber;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 8)
    private ClawbackSide side;

    @Column(name = "origin_settlement_id", nullable = false)
    private Long originSettlementId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "delivery_group_id")
    private Long deliveryGroupId;

    @Column(name = "order_product_id")
    private Long orderProductId;

    @Column(name = "refund_task_id")
    private Long refundTaskId;

    /** {@code OperatorRefundReason} 이름 — 반품 통과(운영자 개설)는 null. */
    @Column(name = "reason", length = 32)
    private String reason;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ClawbackStatus status;

    @Column(name = "applied_settlement_id")
    private Long appliedSettlementId;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "unrecoverable_reason", length = 32)
    private ClawbackUnrecoverableReason unrecoverableReason;

    @Column(name = "unrecoverable_at")
    private LocalDateTime unrecoverableAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private SettlementClawback(String clawbackNumber, int seq, ClawbackSide side, Long originSettlementId, Long orderId,
                               Long deliveryGroupId, Long orderProductId, Long refundTaskId, String reason,
                               long refundAmount, long amount, Long marketId, Long creatorId, LocalDateTime createdAt) {
        this.clawbackNumber = clawbackNumber;
        this.seq = seq;
        this.side = side;
        this.originSettlementId = originSettlementId;
        this.orderId = orderId;
        this.deliveryGroupId = deliveryGroupId;
        this.orderProductId = orderProductId;
        this.refundTaskId = refundTaskId;
        this.reason = reason;
        this.refundAmount = refundAmount;
        this.amount = amount;
        this.marketId = marketId;
        this.creatorId = creatorId;
        this.status = ClawbackStatus.PENDING;
        this.createdAt = createdAt;
    }

    /** 이월 — 같은 번호 · 다음 seq · 남은 금액 · PENDING. */
    public SettlementClawback carryOver(long remaining, int nextSeq, LocalDateTime now) {
        return SettlementClawback.builder()
                .clawbackNumber(clawbackNumber).seq(nextSeq).side(side).originSettlementId(originSettlementId)
                .orderId(orderId).deliveryGroupId(deliveryGroupId).orderProductId(orderProductId)
                .refundTaskId(refundTaskId).reason(reason).refundAmount(refundAmount).amount(remaining)
                .marketId(marketId).creatorId(creatorId).createdAt(now)
                .build();
    }
}

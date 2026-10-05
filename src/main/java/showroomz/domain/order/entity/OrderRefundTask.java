package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;

/**
 * 환불 집행 큐(34 설계서 1-9) — <b>환불은 브랜드가 절대 실행하지 않는다</b>(§34-8). 브랜드 액션·배치는
 * 큐 행만 쌓고, 집행(포트원 부분 취소 호출)은 어드민 거래 관리가 한다.
 *
 * <p>{@code refundAmount}는 예정액이지 확정액이 아니다 — 반송 왕복 배송비 차감(§34-13 #12)은 약관 근거 대기라
 * 전액으로 적재하고 집행 단계에서 조정한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_refund_task")
public class OrderRefundTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_task_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_group_id", nullable = false)
    private OrderDeliveryGroup deliveryGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private RefundTaskSource source;

    /** 근거 행 id — 취소 요청 등. */
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RefundTaskStatus status;

    /** 집행 결과({@code payment_cancel} 행) — 어드민이 채운다. */
    @Column(name = "payment_cancel_id")
    private Long paymentCancelId;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "executed_by")
    private Long executedBy;

    @Builder
    public OrderRefundTask(OrderDeliveryGroup deliveryGroup, Order order, RefundTaskSource source, Long sourceId,
                           Integer refundAmount) {
        this.deliveryGroup = deliveryGroup;
        this.order = order;
        this.source = source;
        this.sourceId = sourceId;
        this.refundAmount = refundAmount;
        this.status = RefundTaskStatus.PENDING;
    }

    public boolean isPending() {
        return status == RefundTaskStatus.PENDING;
    }

    /** 집행 완료 — PG 취소({@code payment_cancel})를 붙이는 것은 어드민 거래 관리의 몫이다. */
    public void markExecuted(int executedAmount, Long executedBy, LocalDateTime now) {
        this.refundAmount = executedAmount;
        this.status = RefundTaskStatus.DONE;
        this.executedBy = executedBy;
        this.executedAt = now;
    }
}

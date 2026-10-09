package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.order.type.OperatorRefundReason;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;

/**
 * 환불 큐(34 설계서 1-9 · 1009 기획 수정본 2절) — <b>환불은 브랜드가 절대 실행하지 않는다</b>(§34-8). 브랜드 액션·배치는
 * 큐 행만 쌓는다.
 *
 * <p>큐를 비우는 주체 — {@link RefundTaskOrigin#PG_AUTO}(취소 승인 · 직권 취소 · 반품 검수 통과 · 반송 완료)는 커밋 직후
 * {@code RefundExecutor}가 포트원 <b>부분 취소</b>로 바로 집행한다. {@link RefundTaskOrigin#OPERATOR}(운영자 사유 환불)만
 * 어드민 환불 관리에서 재확인 뒤 집행한다. 전이는 {@code RefundTransitions}의 짧은 트랜잭션이 한다.
 *
 * <p>{@code refundAmount}는 예정액이다 — 반송 왕복 배송비 차감(§34-13 #12)은 약관 근거 대기라 전액으로 적재한다.
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

    /** 근거 행 id — 취소 요청 · 클레임 요청(collection) 등. */
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RefundTaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16)
    private RefundTaskOrigin origin;

    /** 취소할 결제 — 적재 시점의 주문 결제({@code orders.paid_payment_id}). 결제가 없는 주문(시드)은 null 이고 자동 집행하지 않는다. */
    @Column(name = "payment_id", length = 64)
    private String paymentId;

    /** 운영자 사유 환불의 사유 — PG 자동은 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 30)
    private OperatorRefundReason reasonCode;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    /** 운영자 사유 환불을 편입한 운영자 — PG 자동은 null. */
    @Column(name = "requested_by")
    private Long requestedBy;

    /** 집행 시도 횟수 — 자동 재시도 상한 판정. */
    @Column(name = "attempt", nullable = false)
    private int attempt;

    /** 마지막 실패 사유(PG 거절 문구 등) — 어드민 「실패」 탭. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** 집행 결과({@code payment_cancel} 행). */
    @Column(name = "payment_cancel_id")
    private Long paymentCancelId;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /** 집행한 운영자 — PG 자동 집행은 null. */
    @Column(name = "executed_by")
    private Long executedBy;

    @Builder
    public OrderRefundTask(OrderDeliveryGroup deliveryGroup, Order order, RefundTaskSource source, Long sourceId,
                           Integer refundAmount, RefundTaskOrigin origin, String paymentId,
                           OperatorRefundReason reasonCode, String reasonDetail, Long requestedBy) {
        this.deliveryGroup = deliveryGroup;
        this.order = order;
        this.source = source;
        this.sourceId = sourceId;
        this.refundAmount = refundAmount;
        this.status = RefundTaskStatus.PENDING;
        this.origin = origin != null ? origin : RefundTaskOrigin.PG_AUTO;
        this.paymentId = paymentId;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.requestedBy = requestedBy;
        this.attempt = 0;
    }

    /** 환불번호 {@code RFD-918} — 저장하지 않고 id 를 포맷한다(39 설계서 1-4). 이력 detail 의 접두로 큐 행과 이력을 잇는다. */
    public String refundNo() {
        return id == null ? null : "RFD-" + id;
    }

    public boolean isPending() {
        return status == RefundTaskStatus.PENDING;
    }

    /** 집행을 시작할 수 있는 상태 — 대기 · 실패(재시도). */
    public boolean isExecutable() {
        return status == RefundTaskStatus.PENDING || status == RefundTaskStatus.FAILED;
    }

    /** 집행 시작 — 결제 행을 잠근 트랜잭션 안에서만. */
    public void startExecution() {
        this.status = RefundTaskStatus.EXECUTING;
        this.attempt++;
    }

    /** PG 취소 확인 — 집행 완료. */
    public void markDone(Long paymentCancelId, Long executedBy, LocalDateTime now) {
        this.status = RefundTaskStatus.DONE;
        this.paymentCancelId = paymentCancelId;
        this.executedBy = executedBy;
        this.executedAt = now;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = RefundTaskStatus.FAILED;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    /** PG 를 부르지 않고 집행 완료로 기록 — 결제 밖에서 환불된 건(운영자 수동 기록). */
    public void markExecuted(int executedAmount, Long executedBy, LocalDateTime now) {
        this.refundAmount = executedAmount;
        this.status = RefundTaskStatus.DONE;
        this.executedBy = executedBy;
        this.executedAt = now;
    }
}

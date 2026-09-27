package showroomz.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.payment.type.CancelRequester;
import showroomz.domain.payment.type.PaymentCancelStatus;

import java.time.LocalDateTime;

/**
 * 취소 요청 한 건(결제 계획서 3-2 {@code payment_cancel}). 이번 범위는 전액 취소만이지만 부분 취소를 막지 않는 모양이다.
 * 결과 기록은 {@code PaymentCancelRepository}의 조건부 UPDATE로 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_cancel", indexes = @Index(name = "idx_payment_cancel_payment", columnList = "payment_id, status"))
public class PaymentCancel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancel_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentCancelStatus status;

    @Column(name = "pg_cancellation_id", length = 100)
    private String pgCancellationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by", nullable = false, length = 20)
    private CancelRequester requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    private PaymentCancel(Payment payment, int amount, String reason, CancelRequester requestedBy, LocalDateTime requestedAt) {
        this.payment = payment;
        this.amount = amount;
        this.reason = reason;
        this.status = PaymentCancelStatus.REQUESTED;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public static PaymentCancel requested(Payment payment, int amount, String reason, CancelRequester requestedBy,
                                          LocalDateTime requestedAt) {
        return new PaymentCancel(payment, amount, reason, requestedBy, requestedAt);
    }
}

package showroomz.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.payment.type.ReconciliationIssueKind;

import java.time.LocalDateTime;

/**
 * 일일 대사가 발견한 어긋남(결제 계획서 4-8). {@code (payment_id, kind)} UK — 같은 어긋남을 매일 새로 쌓지 않는다.
 * 대사 배치는 상태를 직접 쓰지 않는다 — confirm을 다시 부르는 것뿐이고, 그래도 남으면 사람이 본다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_reconciliation_issue",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_reconciliation_issue_payment_kind",
                columnNames = {"payment_id", "kind"})
)
public class PaymentReconciliationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40)
    private ReconciliationIssueKind kind;

    @Column(name = "portone_status", length = 30)
    private String portoneStatus;

    @Column(name = "portone_amount")
    private Long portoneAmount;

    @Column(name = "our_status", length = 30)
    private String ourStatus;

    @Column(name = "our_amount")
    private Integer ourAmount;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    private PaymentReconciliationIssue(String paymentId, ReconciliationIssueKind kind, String portoneStatus,
                                       Long portoneAmount, String ourStatus, Integer ourAmount, LocalDateTime detectedAt) {
        this.paymentId = paymentId;
        this.kind = kind;
        this.portoneStatus = portoneStatus;
        this.portoneAmount = portoneAmount;
        this.ourStatus = ourStatus;
        this.ourAmount = ourAmount;
        this.detectedAt = detectedAt;
    }

    public static PaymentReconciliationIssue detected(String paymentId, ReconciliationIssueKind kind, String portoneStatus,
                                                      Long portoneAmount, String ourStatus, Integer ourAmount,
                                                      LocalDateTime detectedAt) {
        return new PaymentReconciliationIssue(paymentId, kind, portoneStatus, portoneAmount, ourStatus, ourAmount, detectedAt);
    }

    /** 같은 어긋남이 다시 보였다 — 최신 관측값으로 갱신하고 미해결로 되돌린다. */
    public void observedAgain(String portoneStatus, Long portoneAmount, String ourStatus, Integer ourAmount,
                              LocalDateTime detectedAt) {
        this.portoneStatus = portoneStatus;
        this.portoneAmount = portoneAmount;
        this.ourStatus = ourStatus;
        this.ourAmount = ourAmount;
        this.detectedAt = detectedAt;
        this.resolvedAt = null;
        this.resolutionNote = null;
    }

    public void resolve(String note, LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
        this.resolutionNote = note;
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }
}

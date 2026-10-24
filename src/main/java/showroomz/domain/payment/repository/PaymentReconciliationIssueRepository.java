package showroomz.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import showroomz.domain.payment.entity.PaymentReconciliationIssue;
import showroomz.domain.payment.type.ReconciliationIssueKind;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentReconciliationIssueRepository extends JpaRepository<PaymentReconciliationIssue, Long> {

    Optional<PaymentReconciliationIssue> findByPaymentIdAndKind(String paymentId, ReconciliationIssueKind kind);

    long countByResolvedAtIsNull();

    /** 대사 미해결 지표의 가장 이른 감지 시각(55 계획서 A4). */
    @Query("SELECT MIN(i.detectedAt) FROM PaymentReconciliationIssue i WHERE i.resolvedAt IS NULL")
    LocalDateTime findOldestUnresolvedDetectedAt();
}

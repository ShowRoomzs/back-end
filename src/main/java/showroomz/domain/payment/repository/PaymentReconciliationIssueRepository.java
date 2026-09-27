package showroomz.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.payment.entity.PaymentReconciliationIssue;
import showroomz.domain.payment.type.ReconciliationIssueKind;

import java.util.Optional;

public interface PaymentReconciliationIssueRepository extends JpaRepository<PaymentReconciliationIssue, Long> {

    Optional<PaymentReconciliationIssue> findByPaymentIdAndKind(String paymentId, ReconciliationIssueKind kind);

    long countByResolvedAtIsNull();
}

package showroomz.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.payment.entity.PaymentCancel;
import showroomz.domain.payment.type.PaymentCancelStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    List<PaymentCancel> findByPayment_PaymentIdAndStatus(String paymentId, PaymentCancelStatus status);

    List<PaymentCancel> findByPayment_PaymentIdOrderByIdAsc(String paymentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PaymentCancel c SET c.status = :to, c.pgCancellationId = :pgCancellationId, c.completedAt = :completedAt, "
            + "c.rawResponse = :raw "
            + "WHERE c.payment.paymentId = :paymentId AND c.status = showroomz.domain.payment.type.PaymentCancelStatus.REQUESTED")
    int finishRequested(@Param("paymentId") String paymentId, @Param("to") PaymentCancelStatus to,
                        @Param("pgCancellationId") String pgCancellationId, @Param("completedAt") LocalDateTime completedAt,
                        @Param("raw") String raw);
}

package showroomz.domain.order.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimPayment;
import showroomz.domain.order.type.ClaimPaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 클레임 결제의 상태 전이 — 전부 조건부 UPDATE 다(주문 결제와 같은 수법). 1행 확인이 경합의 방어선이다.
 */
public interface OrderClaimPaymentRepository extends JpaRepository<OrderClaimPayment, String> {

    long countByChargeId(Long chargeId);

    List<OrderClaimPayment> findByChargeIdOrderByAttemptDesc(Long chargeId);

    /** READY(· 뒤늦게 결제된 FAILED) → PAID — 포트원 조회 결과가 결제 완료일 때. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaimPayment p SET p.status = showroomz.domain.order.type.ClaimPaymentStatus.PAID, "
            + "p.pgTxId = :pgTxId, p.paidAt = :paidAt, p.rawResponse = :raw "
            + "WHERE p.paymentId = :paymentId AND p.status IN (showroomz.domain.order.type.ClaimPaymentStatus.READY, "
            + "showroomz.domain.order.type.ClaimPaymentStatus.FAILED)")
    int markPaid(@Param("paymentId") String paymentId, @Param("pgTxId") String pgTxId,
                 @Param("paidAt") LocalDateTime paidAt, @Param("raw") String raw);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaimPayment p SET p.status = showroomz.domain.order.type.ClaimPaymentStatus.FAILED, "
            + "p.failCode = :failCode, p.failedAt = :now "
            + "WHERE p.paymentId = :paymentId AND p.status = showroomz.domain.order.type.ClaimPaymentStatus.READY")
    int markFailed(@Param("paymentId") String paymentId, @Param("failCode") String failCode,
                   @Param("now") LocalDateTime now);

    /** 취소 선점 — 선점한 호출만 포트원을 부른다. {@code from}은 결제 완료(환불)이거나 아직 확정 전(자동 취소)이다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaimPayment p SET p.status = showroomz.domain.order.type.ClaimPaymentStatus.CANCEL_REQUESTED, "
            + "p.cancelRequestedAt = :now WHERE p.paymentId = :paymentId AND p.status IN :from")
    int requestCancel(@Param("paymentId") String paymentId, @Param("from") List<ClaimPaymentStatus> from,
                      @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaimPayment p SET p.status = showroomz.domain.order.type.ClaimPaymentStatus.CANCELLED, "
            + "p.cancelledAt = :now WHERE p.paymentId = :paymentId "
            + "AND p.status = showroomz.domain.order.type.ClaimPaymentStatus.CANCEL_REQUESTED")
    int markCancelled(@Param("paymentId") String paymentId, @Param("now") LocalDateTime now);

    /** 배치 — 오래된 결제 대기(앱이 콜백을 못 보낸 결제). */
    @Query("SELECT p.paymentId FROM OrderClaimPayment p "
            + "WHERE p.status = showroomz.domain.order.type.ClaimPaymentStatus.READY AND p.createdAt < :before "
            + "ORDER BY p.createdAt ASC")
    List<String> findStaleReadyIds(@Param("before") LocalDateTime before, Pageable pageable);

    /** 배치 — 취소를 선점했는데 PG 취소가 끝나지 않은 결제. */
    @Query("SELECT p.paymentId FROM OrderClaimPayment p "
            + "WHERE p.status = showroomz.domain.order.type.ClaimPaymentStatus.CANCEL_REQUESTED "
            + "ORDER BY p.cancelRequestedAt ASC")
    List<String> findCancelRequestedIds(Pageable pageable);
}

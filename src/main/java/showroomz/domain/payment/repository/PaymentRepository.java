package showroomz.domain.payment.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.type.MismatchReason;
import showroomz.domain.payment.type.PaymentStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 결제 전이는 전부 <b>조건부 UPDATE</b>다(결제 계획서 4-7). 통과(1행)한 호출만 부수 효과·외부 호출을 이어 간다.
 * {@code clearAutomatically}로 영속성 컨텍스트를 비운다 — 갱신 전에 읽어 둔 엔티티가 옛 상태를 커밋 시 덮어쓰지 않게.
 */
public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** 환불 집행의 직렬화 지점 — 한 결제에 부분 취소를 하나씩만 보낸다(1009 기획 수정본 2-4). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findForUpdate(@Param("paymentId") String paymentId);

    /** 부분 취소 확인 — 누적액을 올린다. 상태는 건드리지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.cancelledAmount = p.cancelledAmount + :amount WHERE p.paymentId = :paymentId")
    int addCancelledAmount(@Param("paymentId") String paymentId, @Param("amount") int amount);

    /**
     * 부분 취소 누적이 결제액에 닿았다 — PAID → CANCELLED. 주문 취소 연쇄는 없다(항목은 이미 각 경로가 닫았다). 이렇게 닫아 두어야
     * 포트원 CANCELLED 웹훅 · 대사가 이 결제를 「주문 전체 취소」로 수렴시키지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.CANCELLED "
            + "WHERE p.paymentId = :paymentId AND p.status = showroomz.domain.payment.type.PaymentStatus.PAID "
            + "AND p.cancelledAmount >= p.amount")
    int closeIfFullyRefunded(@Param("paymentId") String paymentId);

    Optional<Payment> findFirstByOrder_IdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findByOrder_IdOrderByAttemptDesc(Long orderId);

    @Query("SELECT COALESCE(MAX(p.attempt), 0) FROM Payment p WHERE p.order.id = :orderId")
    int findMaxAttempt(@Param("orderId") Long orderId);

    List<Payment> findByCreatedAtBetween(LocalDateTime from, LocalDateTime until);

    /** 취소 수렴 대상 — 선점됐지만 결과를 모르는 결제(4-4 둘째 단계). */
    @Query("SELECT p.paymentId FROM Payment p WHERE p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED "
            + "AND p.nextCancelRetryAt IS NOT NULL AND p.nextCancelRetryAt <= :now ORDER BY p.nextCancelRetryAt ASC")
    List<String> findIdsToConvergeCancel(@Param("now") LocalDateTime now, Pageable pageable);

    long countByStatusAndCancelRequestedAtBefore(PaymentStatus status, LocalDateTime before);

    long countByStatus(PaymentStatus status);

    long countByStatusAndModifiedAtAfter(PaymentStatus status, LocalDateTime after);

    // ------------------------------------------------------------------ 전이

    /** 상태만 바꾸는 범용 전이. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :to WHERE p.paymentId = :paymentId AND p.status IN :from")
    int transition(@Param("paymentId") String paymentId, @Param("from") Collection<PaymentStatus> from,
                   @Param("to") PaymentStatus to);

    /** 4-5 ④(a) — 주문이 이 결제로 PAID가 된 뒤 결제 행을 PAID로. 0행이면 불변식 위반이다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.PAID, p.paidAt = :paidAt, "
            + "p.pgProvider = :pgProvider, p.pgTxId = :pgTxId, p.pgMethodJson = :pgMethodJson "
            + "WHERE p.paymentId = :paymentId AND p.status IN :from")
    int markPaid(@Param("paymentId") String paymentId, @Param("from") Collection<PaymentStatus> from,
                 @Param("paidAt") LocalDateTime paidAt, @Param("pgProvider") String pgProvider,
                 @Param("pgTxId") String pgTxId, @Param("pgMethodJson") String pgMethodJson);

    /** 4-5 ⑥ — 포트원이 명시적으로 거절한 경우뿐이다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.FAILED, p.failedAt = :failedAt, "
            + "p.failCode = :failCode, p.failMessage = :failMessage "
            + "WHERE p.paymentId = :paymentId AND p.status = showroomz.domain.payment.type.PaymentStatus.READY")
    int markFailed(@Param("paymentId") String paymentId, @Param("failedAt") LocalDateTime failedAt,
                   @Param("failCode") String failCode, @Param("failMessage") String failMessage);

    /**
     * 취소 선점 — 통과한 트랜잭션이 커밋된 뒤에만 포트원을 부른다(4-7 ③④). {@code mismatchReason}이 null이면 사용자·운영자
     * 취소다. {@code nextCancelRetryAt}을 함께 두어 결과를 모른 채 끝나면 수렴 단계가 집는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED, "
            + "p.cancelRequestedAt = :now, p.mismatchReason = :reason, p.cancelAttempts = 0, "
            + "p.nextCancelRetryAt = :nextRetryAt "
            + "WHERE p.paymentId = :paymentId AND p.status IN :from")
    int claimCancel(@Param("paymentId") String paymentId, @Param("from") Collection<PaymentStatus> from,
                    @Param("reason") MismatchReason reason, @Param("now") LocalDateTime now,
                    @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /**
     * 취소 확인 — 사용자·운영자 취소는 CANCELLED(4-5 ⑤). 사유 컬럼을 WHERE에 넣어, 선점 이후 다른 호출이 사유를 바꿨으면
     * 0행이 되게 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.CANCELLED, p.nextCancelRetryAt = NULL "
            + "WHERE p.paymentId = :paymentId AND p.status IN :from AND p.mismatchReason IS NULL")
    int markCancelledByRequest(@Param("paymentId") String paymentId, @Param("from") Collection<PaymentStatus> from);

    /** 취소 확인 — 시스템 자동 취소는 CANCELLED_MISMATCH. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.CANCELLED_MISMATCH, p.nextCancelRetryAt = NULL "
            + "WHERE p.paymentId = :paymentId AND p.status IN :from AND p.mismatchReason IS NOT NULL")
    int markCancelledByMismatch(@Param("paymentId") String paymentId, @Param("from") Collection<PaymentStatus> from);

    /** 사용자 취소를 포트원이 명시적으로 거절 — PAID로 복귀(4-7 ④ T9'). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.PAID, "
            + "p.cancelRequestedAt = NULL, p.nextCancelRetryAt = NULL, p.cancelAttempts = 0 "
            + "WHERE p.paymentId = :paymentId AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED "
            + "AND p.mismatchReason IS NULL")
    int revertCancelToPaid(@Param("paymentId") String paymentId);

    /** 자동 취소 거절·재시도 상한 — 시스템이 돌려주지 못한 돈. 운영자 처리 대기(종결). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_FAILED, p.nextCancelRetryAt = NULL "
            + "WHERE p.paymentId = :paymentId AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED")
    int markCancelFailed(@Param("paymentId") String paymentId);

    /** 취소 호출 타임아웃 — 다음 재조회 시각을 지수로 미룬다(4-4 둘째 단계). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.cancelAttempts = :attempts, p.nextCancelRetryAt = :nextRetryAt "
            + "WHERE p.paymentId = :paymentId AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED")
    int scheduleCancelRetry(@Param("paymentId") String paymentId, @Param("attempts") int attempts,
                            @Param("nextRetryAt") LocalDateTime nextRetryAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.preRegisteredAt = :at WHERE p.paymentId = :paymentId AND p.preRegisteredAt IS NULL")
    int markPreRegistered(@Param("paymentId") String paymentId, @Param("at") LocalDateTime at);

    /** 상태 전이와 무관한 별도 짧은 트랜잭션(4-5 ⑧). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.rawResponse = :raw WHERE p.paymentId = :paymentId")
    int saveRawResponse(@Param("paymentId") String paymentId, @Param("raw") String raw);

    /** 주문의 살아 있는 결제를 내린다 — 만료·결제 전 취소·재시도(SUPERSEDED). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :to WHERE p.order.id = :orderId "
            + "AND p.status = showroomz.domain.payment.type.PaymentStatus.READY")
    int closeReadyOfOrder(@Param("orderId") Long orderId, @Param("to") PaymentStatus to);
}

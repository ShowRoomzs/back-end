package showroomz.domain.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 환불 큐 — 주문 모듈은 INSERT 하고, 집행은 {@code RefundTransitions}(PG 자동 · 운영자 집행 공용)가 한다(1009 기획 수정본 2-4).
 */
public interface OrderRefundTaskRepository extends JpaRepository<OrderRefundTask, Long> {

    /** 소비자 앱 「환불 처리 중」 판정(C10 설계서 1-2 · 2-4 #4) — 아직 돈이 나가지 않은 환불이 남은 배송 그룹. */
    @Query("SELECT DISTINCT t.deliveryGroup.id FROM OrderRefundTask t WHERE t.order.id IN :orderIds "
            + "AND t.status IN (showroomz.domain.order.type.RefundTaskStatus.PENDING, "
            + "showroomz.domain.order.type.RefundTaskStatus.EXECUTING, showroomz.domain.order.type.RefundTaskStatus.FAILED)")
    List<Long> findPendingGroupIdsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /** 환불 집행이 같은 큐 행을 두 번 닫지 않게 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM OrderRefundTask t WHERE t.id = :id")
    Optional<OrderRefundTask> findForUpdate(@Param("id") Long id);

    /** 같은 결제에 집행 중인 환불이 있는가 — 부분 취소는 한 결제에 하나씩만 보낸다(결제 행 잠금 아래에서 본다). */
    boolean existsByPaymentIdAndStatus(String paymentId, RefundTaskStatus status);

    /** 자동 재시도 대상 — 커밋 뒤 집행을 놓친 대기 건 · 재시도 여유가 남은 실패 건. */
    @Query("SELECT t.id FROM OrderRefundTask t WHERE t.origin = :origin AND t.paymentId IS NOT NULL "
            + "AND ((t.status = showroomz.domain.order.type.RefundTaskStatus.PENDING AND t.createdAt <= :pendingBefore) "
            + "  OR (t.status = showroomz.domain.order.type.RefundTaskStatus.FAILED AND t.attempt < :maxAttempts "
            + "      AND t.modifiedAt <= :failedBefore)) ORDER BY t.id ASC")
    List<Long> findIdsToRetry(@Param("origin") RefundTaskOrigin origin,
                              @Param("pendingBefore") LocalDateTime pendingBefore,
                              @Param("failedBefore") LocalDateTime failedBefore,
                              @Param("maxAttempts") int maxAttempts, Pageable pageable);

    /** 결과를 모른 채 오래 머문 집행 중 건 — 포트원 조회로 결론을 낸다. */
    @Query("SELECT t.id FROM OrderRefundTask t WHERE t.status = showroomz.domain.order.type.RefundTaskStatus.EXECUTING "
            + "AND t.modifiedAt <= :before ORDER BY t.id ASC")
    List<Long> findStaleExecutingIds(@Param("before") LocalDateTime before, Pageable pageable);

    /** 같은 결제에 아직 돈이 나가지 않은 환불 합 — 운영자 사유 환불 편입이 취소 가능 잔액을 넘지 않게 한다. */
    @Query("SELECT COALESCE(SUM(t.refundAmount), 0) FROM OrderRefundTask t WHERE t.paymentId = :paymentId "
            + "AND t.status IN (showroomz.domain.order.type.RefundTaskStatus.PENDING, "
            + "showroomz.domain.order.type.RefundTaskStatus.EXECUTING, showroomz.domain.order.type.RefundTaskStatus.FAILED)")
    long sumOutstandingByPayment(@Param("paymentId") String paymentId);

    /** 어드민 환불 관리(06c) 탭 목록 — 출처 · 상태로 거른다. */
    @Query(value = "SELECT t FROM OrderRefundTask t JOIN FETCH t.order o JOIN FETCH t.deliveryGroup g "
            + "WHERE t.status IN :statuses AND (:origin IS NULL OR t.origin = :origin) "
            + "AND t.createdAt >= :from ORDER BY t.id DESC",
            countQuery = "SELECT COUNT(t) FROM OrderRefundTask t WHERE t.status IN :statuses "
                    + "AND (:origin IS NULL OR t.origin = :origin) AND t.createdAt >= :from")
    org.springframework.data.domain.Page<OrderRefundTask> findForAdmin(
            @Param("statuses") Collection<RefundTaskStatus> statuses, @Param("origin") RefundTaskOrigin origin,
            @Param("from") LocalDateTime from, Pageable pageable);

    @Query("SELECT t.status, t.origin, COUNT(t) FROM OrderRefundTask t GROUP BY t.status, t.origin")
    List<Object[]> countByStatusAndOrigin();

    List<OrderRefundTask> findByDeliveryGroupIdOrderByIdAsc(Long deliveryGroupId);

    List<OrderRefundTask> findByOrderIdOrderByIdAsc(Long orderId);
}

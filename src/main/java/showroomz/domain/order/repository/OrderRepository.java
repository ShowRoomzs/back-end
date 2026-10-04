package showroomz.domain.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.type.OrderStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 주문 전이는 전부 <b>조건부 UPDATE</b>다(결제 계획서 4-1). {@code @Version}은 두지 않는다 — 웹훅과 complete가 동시에 와서
 * 둘 중 하나가 낙관적 락 예외로 500을 내는 것보다 0행 갱신으로 조용히 끝나는 쪽이 맞다.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 문의에 연결된 주문 카드용 — 주문 상품까지 한 번에 조회한다 */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderProducts WHERE o.id IN :orderIds")
    List<Order> findAllByIdInWithProducts(@Param("orderIds") Collection<Long> orderIds);

    /** 본인 주문인지 — 문의에 주문을 연결할 때 남의 주문 정보가 내 문의 카드에 그려지지 않게 막는다. */
    boolean existsByIdAndUser_Id(Long orderId, Long userId);

    Optional<Order> findByUser_IdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** 재시도·취소처럼 한 주문의 결제 행을 고쳐 쓰는 경로 — 주문 행을 잠가 같은 주문의 동시 요청을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findForUpdate(@Param("orderId") Long orderId);

    /** 만료 대상 — 인덱스 (status, expires_at). */
    @Query("SELECT o.id FROM Order o WHERE o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING "
            + "AND o.expiresAt <= :now ORDER BY o.expiresAt ASC, o.id ASC")
    List<Long> findIdsToExpire(@Param("now") LocalDateTime now, Pageable pageable);

    // ------------------------------------------------------------------ 전이

    /** → PAID. 이 결제가 주문의 결제다({@code paid_payment_id}) — 4-5 ④(a). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = showroomz.domain.order.type.OrderStatus.PAID, o.paidPaymentId = :paymentId, "
            + "o.paidAt = :paidAt "
            + "WHERE o.id = :orderId AND o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING")
    int markPaid(@Param("orderId") Long orderId, @Param("paymentId") String paymentId, @Param("paidAt") LocalDateTime paidAt);

    /** → EXPIRED. 만료 시각이 지났을 때만(4-4 ②). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = showroomz.domain.order.type.OrderStatus.EXPIRED, o.expiredAt = :now "
            + "WHERE o.id = :orderId AND o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING AND o.expiresAt <= :now")
    int markExpired(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /** 결제 전 취소 — PAYMENT_PENDING에서만. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = showroomz.domain.order.type.OrderStatus.CANCELLED, o.cancelledAt = :now, "
            + "o.cancelReason = :reason "
            + "WHERE o.id = :orderId AND o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING")
    int cancelPending(@Param("orderId") Long orderId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    /** 결제 후 취소 — 이 결제로 PAID인 주문만(4-1 · 4-5 ⑤). 포트원 취소가 확인된 뒤에 부른다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = showroomz.domain.order.type.OrderStatus.CANCELLED, o.cancelledAt = :now, "
            + "o.cancelReason = :reason "
            + "WHERE o.id = :orderId AND o.status = showroomz.domain.order.type.OrderStatus.PAID AND o.paidPaymentId = :paymentId")
    int cancelPaid(@Param("orderId") Long orderId, @Param("paymentId") String paymentId, @Param("now") LocalDateTime now,
                   @Param("reason") String reason);

    /** 재고 복원 1회 규칙 — 1행일 때만 항목마다 재고를 되돌린다(4-3). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.stockReleasedAt = :now WHERE o.id = :orderId AND o.stockReleasedAt IS NULL")
    int claimStockRelease(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /** 만료 미루기 — 포트원 PENDING(승인 진행 중). 5분씩 최대 3회(4-4). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.expiresAt = :newExpiresAt, o.expiryDeferrals = o.expiryDeferrals + 1 "
            + "WHERE o.id = :orderId AND o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING")
    int deferExpiry(@Param("orderId") Long orderId, @Param("newExpiresAt") LocalDateTime newExpiresAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.expiryCheckFailures = o.expiryCheckFailures + 1 "
            + "WHERE o.id = :orderId AND o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING")
    int recordExpiryCheckFailure(@Param("orderId") Long orderId);

}

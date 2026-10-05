package showroomz.domain.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimCollection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderClaimCollectionRepository extends JpaRepository<OrderClaimCollection, Long> {

    /** 멱등 — 같은 키의 재시도가 요청을 둘 만들지 않는다(주문 생성과 같은 규약). */
    Optional<OrderClaimCollection> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** 회수 송장 입력·정정·자동 취소가 같은 요청을 다툰다 — 요청 행을 잠가 줄을 세운다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM OrderClaimCollection k WHERE k.id = :id")
    Optional<OrderClaimCollection> findForUpdate(@Param("id") Long id);

    /** 회수 추적 대상 — 회수 중인 클레임이 있고 송장이 있는 요청. id 커서로 이어 읽는다. */
    @Query("SELECT k FROM OrderClaimCollection k WHERE k.carrier IS NOT NULL AND k.trackingNumber IS NOT NULL "
            + "AND k.id > :afterId AND EXISTS (SELECT c FROM OrderClaim c WHERE c.collection = k "
            + "    AND c.status = showroomz.domain.order.type.ClaimStatus.COLLECTING) ORDER BY k.id ASC")
    List<OrderClaimCollection> findCollectionTrackingTargets(@Param("afterId") Long afterId, Pageable pageable);

    /** 회수 추적 갱신 — 폴링 당시 송장이 그대로일 때만(그사이 정정됐으면 0행). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaimCollection k SET k.lastTrackingAt = :at, k.lastTrackingLabel = :label "
            + "WHERE k.id = :id AND k.carrier = :carrier AND k.trackingNumber = :trackingNumber")
    int touchTracking(@Param("id") Long id, @Param("carrier") showroomz.domain.order.type.DeliveryCarrier carrier,
                      @Param("trackingNumber") String trackingNumber, @Param("at") LocalDateTime at,
                      @Param("label") String label);

    /** 추적상 브랜드 도착 — 처음 감지한 시각만 적는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaimCollection k SET k.arrivedAt = :at "
            + "WHERE k.id = :id AND k.arrivedAt IS NULL AND k.carrier = :carrier AND k.trackingNumber = :trackingNumber")
    int markArrived(@Param("id") Long id, @Param("carrier") showroomz.domain.order.type.DeliveryCarrier carrier,
                    @Param("trackingNumber") String trackingNumber, @Param("at") LocalDateTime at);

    /** 그 소비자가 그 하위주문에 남긴 결제 대기 초안 — 내용을 고쳐 다시 요청하면 먼저 지운다(묶인 수량·재고를 푼다). */
    @Query("SELECT DISTINCT k.id FROM OrderClaimCollection k WHERE k.userId = :userId "
            + "AND k.deliveryGroup.id = :deliveryGroupId "
            + "AND EXISTS (SELECT c FROM OrderClaim c WHERE c.collection = k "
            + "    AND c.status = showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING)")
    List<Long> findDraftIds(@Param("userId") Long userId, @Param("deliveryGroupId") Long deliveryGroupId);

    /**
     * 결제 없이 남은 오래된 초안 — 삭제 배치의 대상. 결제창이 아직 열려 있을 수 있는 것(READY 결제가 있는 것)은 빼고,
     * 그 결제가 실패로 정리된 다음 회차에 지운다.
     */
    @Query("SELECT k.id FROM OrderClaimCollection k WHERE k.createdAt < :before "
            + "AND EXISTS (SELECT c FROM OrderClaim c WHERE c.collection = k "
            + "    AND c.status = showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING) "
            + "AND NOT EXISTS (SELECT p FROM OrderClaimPayment p WHERE p.collectionId = k.id "
            + "    AND p.status = showroomz.domain.order.type.ClaimPaymentStatus.READY) "
            + "ORDER BY k.id ASC")
    List<Long> findStaleDraftIds(@Param("before") LocalDateTime before, Pageable pageable);

    /** 회수 송장 등록 기한이 지났는데 아직 회수 대기인 요청 — 자동 취소 배치의 대상(전이 #14). */
    @Query("SELECT k.id FROM OrderClaimCollection k WHERE k.invoiceDueAt < :now "
            + "AND EXISTS (SELECT c FROM OrderClaim c WHERE c.collection = k "
            + "    AND c.status = showroomz.domain.order.type.ClaimStatus.REQUESTED) "
            + "ORDER BY k.invoiceDueAt ASC, k.id ASC")
    List<Long> findIdsWithExpiredInvoice(@Param("now") LocalDateTime now, Pageable pageable);
}

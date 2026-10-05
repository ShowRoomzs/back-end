package showroomz.domain.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** 회수 송장 등록 기한이 지났는데 아직 회수 대기인 요청 — 자동 취소 배치의 대상(전이 #14). */
    @Query("SELECT k.id FROM OrderClaimCollection k WHERE k.invoiceDueAt < :now "
            + "AND EXISTS (SELECT c FROM OrderClaim c WHERE c.collection = k "
            + "    AND c.status = showroomz.domain.order.type.ClaimStatus.REQUESTED) "
            + "ORDER BY k.invoiceDueAt ASC, k.id ASC")
    List<Long> findIdsWithExpiredInvoice(@Param("now") LocalDateTime now, Pageable pageable);
}

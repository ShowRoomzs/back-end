package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimHistory;

import java.util.List;

/** append-only — UPDATE·DELETE 메서드를 두지 않는다(35 설계서 1-9). */
public interface OrderClaimHistoryRepository extends JpaRepository<OrderClaimHistory, Long> {

    @Query("SELECT h FROM OrderClaimHistory h WHERE h.claim.id = :claimId ORDER BY h.occurredAt DESC, h.id DESC")
    List<OrderClaimHistory> findByClaimId(@Param("claimId") Long claimId);
}

package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimNotice;

import java.util.List;

public interface OrderClaimNoticeRepository extends JpaRepository<OrderClaimNotice, Long> {

    @Query("SELECT n FROM OrderClaimNotice n WHERE n.claim.id = :claimId ORDER BY n.seq ASC")
    List<OrderClaimNotice> findByClaimId(@Param("claimId") Long claimId);
}

package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimAttachment;

import java.util.Collection;
import java.util.List;

public interface OrderClaimAttachmentRepository extends JpaRepository<OrderClaimAttachment, Long> {

    @Query("SELECT a FROM OrderClaimAttachment a WHERE a.claim.id IN :claimIds ORDER BY a.claim.id ASC, a.sortOrder ASC")
    List<OrderClaimAttachment> findByClaimIds(@Param("claimIds") Collection<Long> claimIds);
}

package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimAttachment;

import java.util.Collection;
import java.util.List;

public interface OrderClaimAttachmentRepository extends JpaRepository<OrderClaimAttachment, Long> {

    @Query("SELECT a FROM OrderClaimAttachment a WHERE a.claim.id IN :claimIds ORDER BY a.claim.id ASC, a.sortOrder ASC")
    List<OrderClaimAttachment> findByClaimIds(@Param("claimIds") Collection<Long> claimIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OrderClaimAttachment a WHERE a.claim.id IN :claimIds")
    int deleteByClaimIds(@Param("claimIds") Collection<Long> claimIds);

    /** 목록의 「증빙 N장」 — [클레임 id, 주인, 장수]. */
    @Query("SELECT a.claim.id, a.owner, COUNT(a) FROM OrderClaimAttachment a WHERE a.claim.id IN :claimIds "
            + "GROUP BY a.claim.id, a.owner")
    List<Object[]> countByClaimIds(@Param("claimIds") Collection<Long> claimIds);
}

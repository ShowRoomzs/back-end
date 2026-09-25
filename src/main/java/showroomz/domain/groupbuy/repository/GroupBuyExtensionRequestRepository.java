package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupBuyExtensionRequestRepository extends JpaRepository<GroupBuyExtensionRequest, Long> {

    Optional<GroupBuyExtensionRequest> findByGroupBuyId(Long groupBuyId);

    boolean existsByGroupBuyId(Long groupBuyId);

    /** 목록 비고 조립 — 페이지의 공구 id로 한 번에 모은다(설계서 4-2 N+1 주의). */
    @Query("SELECT e.groupBuy.id FROM GroupBuyExtensionRequest e "
            + "WHERE e.groupBuy.id IN :groupBuyIds "
            + "AND e.status = showroomz.domain.groupbuy.type.ExtensionRequestStatus.PENDING")
    List<Long> findPendingGroupBuyIds(@Param("groupBuyIds") Collection<Long> groupBuyIds);
}

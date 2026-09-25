package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupBuyChangeRequestRepository extends JpaRepository<GroupBuyChangeRequest, Long> {

    List<GroupBuyChangeRequest> findByGroupBuyIdOrderByRequestedAtDescIdDesc(Long groupBuyId);

    Optional<GroupBuyChangeRequest> findFirstByGroupBuyIdAndStatus(Long groupBuyId, ChangeRequestStatus status);

    boolean existsByGroupBuyIdAndStatus(Long groupBuyId, ChangeRequestStatus status);

    /** 목록 비고 조립 — 검토 중 요청을 페이지 단위로 한 번에 모은다. */
    @Query("SELECT r FROM GroupBuyChangeRequest r "
            + "WHERE r.groupBuy.id IN :groupBuyIds "
            + "AND r.status = showroomz.domain.groupbuy.type.ChangeRequestStatus.PENDING")
    List<GroupBuyChangeRequest> findPendingByGroupBuyIds(@Param("groupBuyIds") Collection<Long> groupBuyIds);
}

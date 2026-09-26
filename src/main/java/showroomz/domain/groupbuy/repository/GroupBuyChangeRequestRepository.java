package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;

import java.time.LocalDateTime;
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

    /**
     * 운영자 판정(32 설계 7절) — <b>경로의 요청 id를 PENDING일 때만</b> 바꾼다. 운영자 A 승인 · B 반려 · 스케줄러 만료가
     * 겹치면 하나만 1행이다. 호출자는 성공 후 엔티티에도 같은 값을 반영한다(clearAutomatically를 쓰지 않는 이유는
     * {@code GroupBuyAdminSuspensionRepository.submitAppeal}과 같다).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuyChangeRequest r "
            + "SET r.status = :result, r.decisionReason = :reason, r.decidedAt = :now, r.decidedBy = :operatorId "
            + "WHERE r.id = :requestId AND r.groupBuy.id = :groupBuyId "
            + "AND r.status = showroomz.domain.groupbuy.type.ChangeRequestStatus.PENDING")
    int decide(@Param("groupBuyId") Long groupBuyId,
               @Param("requestId") Long requestId,
               @Param("result") ChangeRequestStatus result,
               @Param("reason") String reason,
               @Param("operatorId") Long operatorId,
               @Param("now") LocalDateTime now);
}

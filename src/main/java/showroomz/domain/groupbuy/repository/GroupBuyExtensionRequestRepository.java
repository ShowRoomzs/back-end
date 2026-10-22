package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.GroupBuyActorType;

import java.time.LocalDateTime;
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

    /**
     * 인플루언서 응답(수락·거절) — PENDING일 때만 바꾼다(31 설계 5-1 · 5-2). 스케줄러의 만료({@link #expirePending})도
     * 같은 조건이라 한 요청에 「거절」과 「만료」가 둘 다 기록되지 않는다 — 둘 중 하나만 남는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuyExtensionRequest e "
            + "SET e.status = :result, e.respondedAt = :now, e.responseActorType = :actorType, "
            + "    e.rejectReasonCode = :rejectReasonCode, e.rejectMemo = :rejectMemo "
            + "WHERE e.id = :extensionRequestId "
            + "AND e.status = showroomz.domain.groupbuy.type.ExtensionRequestStatus.PENDING")
    int respond(@Param("extensionRequestId") Long extensionRequestId,
                @Param("result") ExtensionRequestStatus result,
                @Param("actorType") GroupBuyActorType actorType,
                @Param("rejectReasonCode") String rejectReasonCode,
                @Param("rejectMemo") String rejectMemo,
                @Param("now") LocalDateTime now);

    /** 무응답 = 변경 없이 종결(§29-6) — 종료 전이가 부른다. 이미 응답한 요청은 건드리지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuyExtensionRequest e "
            + "SET e.status = showroomz.domain.groupbuy.type.ExtensionRequestStatus.EXPIRED, "
            + "    e.respondedAt = :now, e.responseActorType = showroomz.domain.groupbuy.type.GroupBuyActorType.SYSTEM "
            + "WHERE e.groupBuy.id = :groupBuyId "
            + "AND e.status = showroomz.domain.groupbuy.type.ExtensionRequestStatus.PENDING")
    int expirePending(@Param("groupBuyId") Long groupBuyId, @Param("now") LocalDateTime now);

    /**
     * 스튜디오 홈 할 일 「연장 응답」 · 달력 「연장 응답 기한」(1017 설계서 5-1 · 5-3) — 내 진행중 공구의 응답 대기 연장 요청.
     * 조건은 공구 GNB 배지({@code CreatorGroupBuyActionPredicate} extensionToAnswer)와 같다. 공구 · 계약 · 브랜드를 함께 읽는다.
     */
    @Query("SELECT e FROM GroupBuyExtensionRequest e JOIN FETCH e.groupBuy g JOIN FETCH g.contract JOIN FETCH g.market "
            + "WHERE g.creator.id = :creatorId "
            + "AND e.status = showroomz.domain.groupbuy.type.ExtensionRequestStatus.PENDING "
            + "AND g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.IN_PROGRESS AND g.endAt > :now "
            + "ORDER BY g.endAt ASC, e.id ASC")
    List<GroupBuyExtensionRequest> findPendingForCreator(@Param("creatorId") Long creatorId,
                                                         @Param("now") LocalDateTime now);
}

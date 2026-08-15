package showroomz.domain.showroom.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.showroom.entity.ShowroomVisit;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowroomVisitRepository extends JpaRepository<ShowroomVisit, Long> {

    /** §22-4 30분 세션 판정 — 직전 세션 안에 이미 들어온 방문자면 순방문을 새로 세지 않는다. */
    boolean existsByCreator_IdAndVisitorKeyAndVisitedAtAfter(Long creatorId, String visitorKey, LocalDateTime since);

    /** 순방문 — 방문 횟수(적재 시점에 세션 중복을 걸렀으므로 행 수가 곧 순방문이다). */
    @Query("SELECT COUNT(v) FROM ShowroomVisit v " +
           "WHERE v.creator.id = :creatorId AND v.visitedAt >= :from AND v.visitedAt < :to")
    long countVisits(@Param("creatorId") Long creatorId,
                     @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to);

    /** 방문자 수 — 중복 제거한 사람 수. 팔로우 전환율의 분모다(횟수가 아니라 사람 기준). */
    @Query("SELECT COUNT(DISTINCT v.visitorKey) FROM ShowroomVisit v " +
           "WHERE v.creator.id = :creatorId AND v.visitedAt >= :from AND v.visitedAt < :to")
    long countVisitors(@Param("creatorId") Long creatorId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);

    /** 유입 경로 — {소스, 방문 횟수} 행. */
    @Query("SELECT v.source, COUNT(v) FROM ShowroomVisit v " +
           "WHERE v.creator.id = :creatorId AND v.visitedAt >= :from AND v.visitedAt < :to " +
           "GROUP BY v.source")
    List<Object[]> countVisitsBySource(@Param("creatorId") Long creatorId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /**
     * §22-4 팔로워 행동 — 기간 내 방문한 <b>팔로워 1인당 방문 횟수</b> 목록.
     * 행 수 = 방문한 팔로워 수, 합 = 팔로워 방문 횟수, 2 이상인 행 수 = 재방문한 팔로워 수라서
     * 평균 방문 횟수·재방문율·방문자 중 팔로워 비중을 이 목록 하나로 모두 계산한다(개인 식별자는 꺼내지 않는다).
     */
    @Query("SELECT COUNT(v) FROM ShowroomVisit v " +
           "WHERE v.creator.id = :creatorId AND v.visitedAt >= :from AND v.visitedAt < :to " +
           "AND v.user.id IN (SELECT cf.user.id FROM CreatorFollow cf WHERE cf.creator.id = :creatorId) " +
           "GROUP BY v.user.id")
    List<Long> countVisitsPerFollower(@Param("creatorId") Long creatorId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);
}

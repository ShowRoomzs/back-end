package showroomz.domain.message.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.domain.connection.entity.Connection;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ThreadStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageThreadRepository extends JpaRepository<MessageThread, Long> {

    Optional<MessageThread> findByConnection(Connection connection);

    /**
     * §13-1 좌측 목록 — 운영자 채널(OPERATOR_MARKET) 최상단 고정 + STATUS=OPEN만, 최근 메시지순.
     * 목록 아바타(counterpartImageUrl)가 CREATOR.USER.PROFILE_IMAGE_URL에 있으므로 user까지 fetch한다
     * — 지연 로딩으로 두면 스레드 수만큼 추가 쿼리가 나간다(N+1).
     */
    @Query("SELECT t FROM MessageThread t JOIN FETCH t.connection c " +
           "LEFT JOIN FETCH c.creator cr LEFT JOIN FETCH cr.user " +
           "WHERE t.status = :status AND c.market = :market " +
           "ORDER BY CASE WHEN c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_MARKET THEN 0 ELSE 1 END, " +
           "t.lastMessageAt DESC NULLS LAST")
    Page<MessageThread> findOpenThreadsForMarket(@Param("market") Market market,
                                                  @Param("status") ThreadStatus status,
                                                  Pageable pageable);

    /** §14-3 `연결됨` 탭 — 운영자 채널(OPERATOR_CREATOR) 최상단 고정 + STATUS=OPEN만, 최근 메시지순. */
    @Query("SELECT t FROM MessageThread t JOIN FETCH t.connection c LEFT JOIN FETCH c.market " +
           "WHERE t.status = :status AND c.creator = :creator " +
           "ORDER BY CASE WHEN c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_CREATOR THEN 0 ELSE 1 END, " +
           "t.lastMessageAt DESC NULLS LAST")
    Page<MessageThread> findOpenThreadsForCreator(@Param("creator") Creator creator,
                                                   @Param("status") ThreadStatus status,
                                                   Pageable pageable);

    /**
     * 배지 집계 전용 — 스레드 엔티티를 통째로 로드하지 않고 id만 가져온다. 목록과 달리 배지는
     * 정렬·표시 정보가 전혀 필요 없고, 30~60초 폴링(§0) 대상이라 로딩 비용을 줄일 값어치가 있다.
     */
    @Query("SELECT t.id FROM MessageThread t JOIN t.connection c " +
           "WHERE t.status = :status AND c.market = :market")
    List<Long> findOpenThreadIdsForMarket(@Param("market") Market market,
                                           @Param("status") ThreadStatus status);

    @Query("SELECT t.id FROM MessageThread t JOIN t.connection c " +
           "WHERE t.status = :status AND c.creator = :creator")
    List<Long> findOpenThreadIdsForCreator(@Param("creator") Creator creator,
                                            @Param("status") ThreadStatus status);
}

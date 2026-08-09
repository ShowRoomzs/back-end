package showroomz.domain.message.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ParticipantType;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByThreadAndClientMessageId(MessageThread thread, String clientMessageId);

    /** §3-4 커서 페이징 — 최신순, cursor 미지정(첫 페이지). */
    List<Message> findByThreadOrderByIdDesc(MessageThread thread, Pageable pageable);

    /** §3-4 커서 페이징 — cursor(직전 페이지 마지막 id)보다 작은 것만. */
    List<Message> findByThreadAndIdLessThanOrderByIdDesc(MessageThread thread, Long cursor, Pageable pageable);

    Optional<Message> findTopByThreadOrderByIdDesc(MessageThread thread);

    /**
     * 여러 스레드의 안 읽은 수를 <b>한 쿼리로</b> 집계한다 — 배지(`/connections/summary`)는 30~60초
     * 폴링 대상(§0)이고 목록도 매 진입마다 불리므로, 스레드당 쿼리를 도는 방식은 스레드 수만큼 비용이 는다.
     *
     * <p>읽음 위치는 `ThreadParticipant`를 애드혹 조인으로 붙여 판정한다 — 참가자 행이 아직 없으면
     * (한 번도 읽지 않은 스레드) `lastReadMessageId`가 null이라 전체가 안 읽은 수가 된다.
     *
     * <p><b>본인이 보낸 메시지는 제외</b>한다. 전송 API는 읽음 처리를 하지 않으므로 이 조건이 없으면
     * 자기가 보낸 메시지가 자기 배지를 올린다.
     *
     * <p>스레드는 LEFT JOIN이라 안 읽은 메시지가 없는 스레드도 count 0으로 한 행 돌아온다.
     */
    @Query("SELECT t.id, COUNT(m.id) FROM MessageThread t " +
           "LEFT JOIN ThreadParticipant p ON p.thread = t " +
           "    AND p.participantType = :participantType AND p.participantId = :participantId " +
           "LEFT JOIN Message m ON m.thread = t " +
           "    AND (p.lastReadMessageId IS NULL OR m.id > p.lastReadMessageId) " +
           "    AND NOT (m.senderType = :participantType AND m.senderId = :participantId) " +
           "WHERE t.id IN :threadIds " +
           "GROUP BY t.id")
    List<Object[]> countUnreadByThreadIds(@Param("threadIds") List<Long> threadIds,
                                           @Param("participantType") ParticipantType participantType,
                                           @Param("participantId") Long participantId);
}

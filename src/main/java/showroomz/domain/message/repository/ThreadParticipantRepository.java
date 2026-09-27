package showroomz.domain.message.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.entity.ThreadParticipant;
import showroomz.domain.message.type.ParticipantType;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ThreadParticipantRepository extends JpaRepository<ThreadParticipant, Long> {

    Optional<ThreadParticipant> findByThreadAndParticipantTypeAndParticipantId(
            MessageThread thread, ParticipantType participantType, Long participantId);

    /**
     * 읽음 위치 upsert — 행이 없으면 만들고, 있으면 <b>앞으로만</b> 옮긴다(28-1 수정계획 3).
     *
     * <p>조회 후 저장으로 나누면 ① 같은 스레드를 처음 동시에 열 때 둘 다 INSERT해 {@code uk_thread_participant}
     * 충돌(500)이 나고 ② 최신 id를 먼저 읽은 요청이 늦게 커밋하면 읽음 위치가 뒤로 간다. 한 문장이라 둘 다 없다.
     *
     * <p>{@code last_read_at}을 먼저 대입한다 — MySQL은 대입을 왼쪽부터 적용하므로 옛 {@code last_read_message_id}와
     * 비교하려면 앞에 있어야 한다. {@code VALUES()}는 MySQL 8.0.20부터 deprecated 경고가 나지만 H2(MySQL 모드)와
     * 함께 도는 문법이라 행 별칭 대신 쓴다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO thread_participant
                (thread_id, participant_type, participant_id, last_read_message_id, last_read_at, created_at, modified_at)
            VALUES (:threadId, :participantType, :participantId, :messageId, :now, :now, :now)
            ON DUPLICATE KEY UPDATE
                last_read_at = CASE WHEN COALESCE(last_read_message_id, 0) <= VALUES(last_read_message_id)
                                    THEN VALUES(last_read_at) ELSE last_read_at END,
                last_read_message_id = GREATEST(COALESCE(last_read_message_id, 0), VALUES(last_read_message_id)),
                modified_at = VALUES(modified_at)
            """, nativeQuery = true)
    void upsertReadPosition(@Param("threadId") Long threadId, @Param("participantType") String participantType,
                            @Param("participantId") Long participantId, @Param("messageId") Long messageId,
                            @Param("now") LocalDateTime now);
}

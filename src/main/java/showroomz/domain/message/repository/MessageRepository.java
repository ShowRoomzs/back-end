package showroomz.domain.message.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;

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

    long countByThreadAndIdGreaterThan(MessageThread thread, Long id);

    long countByThread(MessageThread thread);
}

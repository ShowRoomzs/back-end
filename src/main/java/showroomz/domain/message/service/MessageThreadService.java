package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.entity.ThreadParticipant;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.repository.ThreadParticipantRepository;
import showroomz.domain.message.type.ParticipantType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAIR/OPERATOR_MARKET/OPERATOR_CREATOR 스레드 전부에 공통으로 적용되는 핵심 로직(§1-3·§14-5 —
 * "스레드 쪽 로직은 완전히 동일하다"). 파트너센터·쇼룸 스튜디오 양쪽 API 서비스가 이 컴포넌트를 공유한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageThreadService {

    private static final int PREVIEW_MAX_LENGTH = 255;

    private final MessageThreadRepository messageThreadRepository;
    private final ThreadParticipantRepository threadParticipantRepository;
    private final MessageRepository messageRepository;

    /** CONNECTION이 처음 CONNECTED가 되는 순간(최초 수락 또는 재연결) 호출한다(§1-3). */
    @Transactional
    public MessageThread activateThread(Connection connection) {
        MessageThread thread = messageThreadRepository.findByConnection(connection)
                .orElseGet(() -> messageThreadRepository.save(MessageThread.openFor(connection)));
        thread.open();
        return thread;
    }

    public record SendResult(Message message, boolean created) {
    }

    /**
     * §13-10 멱등 전송 — clientMessageId 충돌 시 신규 저장 대신 기존 메시지를 그대로 반환한다.
     * 재전송(retry)은 이 메서드를 그대로 다시 호출하면 되고, 별도 분기가 필요 없다.
     */
    @Transactional
    public SendResult sendMessage(MessageThread thread, ParticipantType senderType, Long senderId,
                                   String clientMessageId, String content) {
        return messageRepository.findByThreadAndClientMessageId(thread, clientMessageId)
                .map(existing -> new SendResult(existing, false))
                .orElseGet(() -> {
                    if (content == null || content.isBlank()) {
                        throw new BusinessException(ErrorCode.MESSAGE_EMPTY);
                    }
                    if (!thread.isOpen()) {
                        throw new BusinessException(ErrorCode.THREAD_DORMANT);
                    }
                    Message saved = messageRepository.save(
                            Message.create(thread, senderType, senderId, clientMessageId, content));
                    thread.recordLastMessage(preview(content), saved.getCreatedAt());
                    return new SendResult(saved, true);
                });
    }

    /** §3-4 커서 페이징 — 최신순. cursor가 null이면 첫 페이지. */
    public List<Message> getMessages(MessageThread thread, Long cursor, int size) {
        Pageable pageable = Pageable.ofSize(size);
        return cursor == null
                ? messageRepository.findByThreadOrderByIdDesc(thread, pageable)
                : messageRepository.findByThreadAndIdLessThanOrderByIdDesc(thread, cursor, pageable);
    }

    @Transactional
    public void markRead(MessageThread thread, ParticipantType participantType, Long participantId) {
        Long latestId = messageRepository.findTopByThreadOrderByIdDesc(thread)
                .map(Message::getId)
                .orElse(null);
        if (latestId == null) {
            return;
        }
        ThreadParticipant participant = threadParticipantRepository
                .findByThreadAndParticipantTypeAndParticipantId(thread, participantType, participantId)
                .orElseGet(() -> ThreadParticipant.create(thread, participantType, participantId));
        participant.markRead(latestId);
        threadParticipantRepository.save(participant);
    }

    public long countUnread(MessageThread thread, ParticipantType participantType, Long participantId) {
        return threadParticipantRepository
                .findByThreadAndParticipantTypeAndParticipantId(thread, participantType, participantId)
                .map(p -> p.getLastReadMessageId() == null
                        ? messageRepository.countByThread(thread)
                        : messageRepository.countByThreadAndIdGreaterThan(thread, p.getLastReadMessageId()))
                .orElseGet(() -> messageRepository.countByThread(thread));
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > PREVIEW_MAX_LENGTH ? content.substring(0, PREVIEW_MAX_LENGTH) : content;
    }
}

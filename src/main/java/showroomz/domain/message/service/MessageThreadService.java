package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.entity.ThreadParticipant;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.repository.ThreadParticipantRepository;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.AllowedAttachmentExtensions;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final MessageAttachmentRepository messageAttachmentRepository;

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
     * 재전송(retry)은 이 메서드를 그대로 다시 호출하면 되고, 별도 분기가 필요 없다 — 첨부는 첫 요청에서
     * 이미 붙었으므로 재시도 시 다시 연결을 시도하지 않는다(멱등 분기 안에서 attachmentIds를 아예 안 본다).
     */
    @Transactional
    public SendResult sendMessage(MessageThread thread, ParticipantType senderType, Long senderId,
                                   String clientMessageId, String content, List<Long> attachmentIds) {
        return messageRepository.findByThreadAndClientMessageId(thread, clientMessageId)
                .map(existing -> new SendResult(existing, false))
                .orElseGet(() -> {
                    boolean hasAttachments = attachmentIds != null && !attachmentIds.isEmpty();
                    if ((content == null || content.isBlank()) && !hasAttachments) {
                        throw new BusinessException(ErrorCode.MESSAGE_EMPTY);
                    }
                    if (!thread.isOpen()) {
                        throw new BusinessException(ErrorCode.THREAD_DORMANT);
                    }

                    List<MessageAttachment> attachments = hasAttachments
                            ? validateAttachments(thread, senderType, senderId, attachmentIds)
                            : List.of();

                    Message saved = messageRepository.save(
                            Message.create(thread, senderType, senderId, clientMessageId, content));

                    if (hasAttachments) {
                        linkAttachments(saved, thread, senderType, senderId, attachmentIds);
                    }

                    thread.recordLastMessage(preview(content, attachments), saved.getCreatedAt());
                    return new SendResult(saved, true);
                });
    }

    /**
     * §4-5 검증 1~3, 5번 — attachmentIds 순서 그대로 반환한다(4번의 message IS NULL 확인과 실제 연결은
     * 조건부 UPDATE(linkAttachments)에서 최종적으로 처리 — 여기서는 좋은 에러 메시지를 위한 사전 검증).
     */
    private List<MessageAttachment> validateAttachments(MessageThread thread, ParticipantType senderType,
                                                          Long senderId, List<Long> attachmentIds) {
        if (attachmentIds.size() > AllowedAttachmentExtensions.MAX_ATTACHMENT_COUNT) {
            throw new BusinessException(ErrorCode.ATTACHMENT_COUNT_EXCEEDED);
        }

        List<MessageAttachment> found = messageAttachmentRepository.findAllByIdIn(attachmentIds);
        Map<Long, MessageAttachment> byId = found.stream()
                .collect(Collectors.toMap(MessageAttachment::getId, a -> a));

        long totalSize = 0L;
        for (Long id : attachmentIds) {
            MessageAttachment attachment = byId.get(id);
            if (attachment == null) {
                throw new BusinessException(ErrorCode.ATTACHMENT_ACCESS_DENIED);
            }
            if (!attachment.getThread().getId().equals(thread.getId())
                    || attachment.getUploaderType() != senderType
                    || !attachment.getUploaderId().equals(senderId)) {
                throw new BusinessException(ErrorCode.ATTACHMENT_ACCESS_DENIED);
            }
            if (attachment.getStatus() != AttachmentStatus.UPLOADED) {
                throw new BusinessException(ErrorCode.ATTACHMENT_NOT_UPLOADED);
            }
            if (attachment.getMessage() != null) {
                throw new BusinessException(ErrorCode.ATTACHMENT_ALREADY_ATTACHED);
            }
            totalSize += attachment.getSizeBytes();
        }
        if (totalSize > AllowedAttachmentExtensions.MAX_TOTAL_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.ATTACHMENT_SIZE_EXCEEDED);
        }
        return attachmentIds.stream().map(byId::get).toList();
    }

    /** 조건부 UPDATE — 검증~연결 사이의 경합(동시 재전송 등)에 대한 최종 방어선. 하나라도 실패하면 전체 롤백. */
    private void linkAttachments(Message message, MessageThread thread, ParticipantType senderType,
                                  Long senderId, List<Long> attachmentIds) {
        for (int i = 0; i < attachmentIds.size(); i++) {
            int updated = messageAttachmentRepository.attachToMessage(
                    attachmentIds.get(i), message, i, thread, senderType, senderId, AttachmentStatus.UPLOADED);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.ATTACHMENT_ALREADY_ATTACHED);
            }
        }
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

    /**
     * 여러 스레드의 안 읽은 수를 한 쿼리로 집계한다(threadId → count). 결과에 없는 스레드는 0으로 본다.
     * 스레드당 개별 카운트를 돌면 배지 폴링(§0)·목록 조회 비용이 스레드 수에 비례해 늘어난다.
     */
    public Map<Long, Long> countUnreadByThreadIds(List<Long> threadIds, ParticipantType participantType,
                                                   Long participantId) {
        if (threadIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.countUnreadByThreadIds(threadIds, participantType, participantId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** 배지 합계 — countUnreadByThreadIds와 같은 한 쿼리를 쓰고 값만 합친다. */
    public long sumUnread(List<Long> threadIds, ParticipantType participantType, Long participantId) {
        return countUnreadByThreadIds(threadIds, participantType, participantId).values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    /** §13-11 — 텍스트 없이 첨부만 보낸 경우, 목록 미리보기는 첨부 종류 기반 대체 문구를 쓴다. */
    private String preview(String content, List<MessageAttachment> attachments) {
        if (content != null && !content.isBlank()) {
            return content.length() > PREVIEW_MAX_LENGTH ? content.substring(0, PREVIEW_MAX_LENGTH) : content;
        }
        if (attachments.isEmpty()) {
            return null;
        }
        AttachmentType type = attachments.get(0).getAttachmentType();
        String label = switch (type) {
            case IMAGE -> "사진";
            case VIDEO -> "동영상";
            case DOCUMENT -> "파일";
        };
        return attachments.size() > 1 ? label + " " + attachments.size() + "개" : label;
    }
}

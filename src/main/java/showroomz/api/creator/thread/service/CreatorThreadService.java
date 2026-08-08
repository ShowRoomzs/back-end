package showroomz.api.creator.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.api.creator.thread.dto.MessageItem;
import showroomz.api.creator.thread.dto.MessageListResponse;
import showroomz.api.creator.thread.dto.SendMessageRequest;
import showroomz.api.creator.thread.dto.ThreadListItem;
import showroomz.api.creator.thread.dto.ThreadSummaryResponse;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorThreadService {

    private static final String OPERATOR_CHANNEL_NAME = "SHOWROOMZ 운영팀";

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final ConnectionRepository connectionRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final MessageThreadService messageThreadService;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageAttachmentService messageAttachmentService;

    /** §14-3 `연결됨` 탭. */
    public PageResponse<ThreadListItem> getThreads(String creatorEmail, PagingRequest pagingRequest) {
        Creator creator = getMyCreator(creatorEmail);
        Page<ThreadListItem> page = messageThreadRepository
                .findOpenThreadsForCreator(creator, ThreadStatus.OPEN, pagingRequest.toPageable())
                .map(thread -> toListItem(thread, creator));
        return PageResponse.of(page);
    }

    public ThreadSummaryResponse getSummary(String creatorEmail) {
        Creator creator = getMyCreator(creatorEmail);
        List<MessageThread> threads = messageThreadRepository
                .findOpenThreadsForCreator(creator, ThreadStatus.OPEN, Pageable.unpaged())
                .getContent();

        long unread = threads.stream()
                .mapToLong(t -> messageThreadService.countUnread(t, ParticipantType.CREATOR, creator.getId()))
                .sum();
        long pendingRequests = connectionRepository.countByTypeAndCreatorAndStatus(
                ConnectionType.PAIR, creator, ConnectionStatus.REQUESTED);
        return new ThreadSummaryResponse(unread, pendingRequests);
    }

    public MessageListResponse getMessages(String creatorEmail, Long threadId, Long cursor, int size) {
        Creator creator = getMyCreator(creatorEmail);
        MessageThread thread = getMyThread(creator, threadId);

        List<Message> fetched = messageThreadService.getMessages(thread, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<Message> page = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        Map<Long, List<AttachmentSummary>> attachmentsByMessage = loadAttachments(page);
        List<MessageItem> items = page.stream()
                .map(m -> toMessageItem(m, creator.getId(), attachmentsByMessage.getOrDefault(m.getId(), List.of())))
                .toList();
        return new MessageListResponse(items, nextCursor, hasNext);
    }

    public record SendMessageOutcome(MessageItem item, boolean created) {
    }

    @Transactional
    public SendMessageOutcome sendMessage(String creatorEmail, Long threadId, SendMessageRequest request) {
        Creator creator = getMyCreator(creatorEmail);
        MessageThread thread = getMyThread(creator, threadId);

        MessageThreadService.SendResult result = messageThreadService.sendMessage(
                thread, ParticipantType.CREATOR, creator.getId(),
                request.getClientMessageId(), request.getContent(), request.getAttachmentIds());

        Map<Long, List<AttachmentSummary>> attachments = loadAttachments(List.of(result.message()));
        MessageItem item = toMessageItem(result.message(), creator.getId(),
                attachments.getOrDefault(result.message().getId(), List.of()));
        return new SendMessageOutcome(item, result.created());
    }

    @Transactional
    public void markRead(String creatorEmail, Long threadId) {
        Creator creator = getMyCreator(creatorEmail);
        MessageThread thread = getMyThread(creator, threadId);
        messageThreadService.markRead(thread, ParticipantType.CREATOR, creator.getId());
    }

    /** §4-1 ① — presign 발급. */
    @Transactional
    public PresignResponse createPresignedUpload(String creatorEmail, Long threadId, PresignRequest request) {
        Creator creator = getMyCreator(creatorEmail);
        MessageThread thread = getMyThread(creator, threadId);
        return messageAttachmentService.createPresignedUpload(thread, ParticipantType.CREATOR, creator.getId(), request);
    }

    /** §4-1 ③ — 업로드 완료 통지. */
    @Transactional
    public AttachmentSummary completeUpload(String creatorEmail, Long attachmentId, CompleteAttachmentRequest request) {
        Creator creator = getMyCreator(creatorEmail);
        return messageAttachmentService.completeUpload(
                ParticipantType.CREATOR, creator.getId(), attachmentId, request.getDurationSeconds());
    }

    private Map<Long, List<AttachmentSummary>> loadAttachments(List<Message> messages) {
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageAttachmentRepository.findByMessage_IdInOrderBySortOrderAsc(messageIds).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getMessage().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparing(MessageAttachment::getSortOrder,
                                                Comparator.nullsLast(Comparator.naturalOrder())))
                                        .map(messageAttachmentService::toSummary)
                                        .toList())));
    }

    private ThreadListItem toListItem(MessageThread thread, Creator creator) {
        Connection connection = thread.getConnection();
        boolean isOperator = connection.getType() == ConnectionType.OPERATOR_CREATOR;
        String name = isOperator ? OPERATOR_CHANNEL_NAME : connection.getMarket().getMarketName();
        long unread = messageThreadService.countUnread(thread, ParticipantType.CREATOR, creator.getId());

        // 계약 도메인은 이번 스코프 밖 — hasContract는 항상 false로 스텁(§3-2, 계약 작업 시 연결).
        return new ThreadListItem(
                thread.getId(), name, isOperator ? null : connection.getMarket().getMarketImageUrl(),
                isOperator, false, thread.getLastMessagePreview(), thread.getLastMessageAt(), unread);
    }

    private MessageItem toMessageItem(Message message, Long myCreatorId, List<AttachmentSummary> attachments) {
        boolean mine = message.getSenderType() == ParticipantType.CREATOR && message.getSenderId().equals(myCreatorId);
        return new MessageItem(message.getId(), message.getSenderType(), mine, message.getContent(), attachments, message.getCreatedAt());
    }

    private MessageThread getMyThread(Creator creator, Long threadId) {
        MessageThread thread = messageThreadRepository.findById(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THREAD_NOT_FOUND));

        Creator threadCreator = thread.getConnection().getCreator();
        if (threadCreator == null || !threadCreator.getId().equals(creator.getId())) {
            throw new BusinessException(ErrorCode.THREAD_ACCESS_DENIED);
        }
        return thread;
    }

    private Creator getMyCreator(String creatorEmail) {
        Users user = userRepository.findByUsername(creatorEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}

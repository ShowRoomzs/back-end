package showroomz.api.creator.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.api.common.thread.dto.ThreadAdjustmentBadge;
import showroomz.api.common.thread.service.ThreadAdjustmentViews;
import showroomz.api.creator.thread.dto.MessageItem;
import showroomz.api.creator.thread.dto.MessageListResponse;
import showroomz.api.creator.thread.dto.SendMessageRequest;
import showroomz.api.creator.thread.dto.ThreadListItem;
import showroomz.api.creator.thread.dto.ThreadSummaryResponse;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.api.common.thread.dto.MessageCardResponse;
import showroomz.domain.message.service.MessageCardReader;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentThreadGuard;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final MessageCardReader messageCardReader;
    private final ContractRepository contractRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final ThreadAdjustmentViews adjustmentViews;
    private final SettlementAdjustmentThreadGuard adjustmentThreadGuard;

    /**
     * §14-3 `연결됨` 탭. 안 읽은 수는 페이지 전체를 한 쿼리로 집계한다(스레드당 카운트 금지).
     * keyword는 좌측 목록 상단의 "브랜드명 검색"(S1~S11) — 비어 있으면 전체를 내려준다.
     */
    public PageResponse<ThreadListItem> getThreads(String creatorEmail, String keyword, PagingRequest pagingRequest) {
        Creator creator = getMyCreator(creatorEmail);
        Page<MessageThread> threads = messageThreadRepository
                .findOpenThreadsForCreator(creator, ThreadStatus.OPEN, normalizeKeyword(keyword),
                        pagingRequest.toPageable());

        Map<Long, Long> unreadByThread = messageThreadService.countUnreadByThreadIds(
                threads.getContent().stream().map(MessageThread::getId).toList(),
                ParticipantType.CREATOR, creator.getId());

        Set<Long> marketsWithContract = marketsWithReceivedContract(creator, threads.getContent());
        Map<Long, String> groupBuyTitles = groupBuyRepository.findTitleMapByIds(
                threads.getContent().stream().map(MessageThread::getSubjectId).filter(Objects::nonNull).distinct().toList());
        Map<Long, ThreadAdjustmentBadge> adjustments = adjustmentViews.badges(threads.getContent(),
                SettlementParty.CREATOR);

        return PageResponse.of(threads.map(thread ->
                toListItem(thread, unreadByThread, marketsWithContract, groupBuyTitles, adjustments)));
    }

    /**
     * [계약 확인] 게이트(§14-2) — 페이지의 브랜드 중 나에게 <b>도착한</b> 계약이 있는 곳을 한 쿼리로 모은다.
     * 브랜드가 작성 중이거나 운영자가 반려한 계약은 스튜디오에 존재하지 않는 계약이라 세지 않는다.
     */
    private Set<Long> marketsWithReceivedContract(Creator creator, List<MessageThread> threads) {
        List<Long> marketIds = threads.stream()
                .map(thread -> thread.getConnection().getMarket())
                .filter(Objects::nonNull)
                .map(market -> market.getId())
                .distinct()
                .toList();
        if (marketIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(contractRepository.findMarketIdsWithReceivedContract(
                creator.getId(), marketIds, ContractStatus.RECEIVED_BY_CREATOR));
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    /** 탭 배지용 — 폴링 대상(§0)이라 스레드 수와 무관하게 쿼리 3회로 고정한다. */
    public ThreadSummaryResponse getSummary(String creatorEmail) {
        Creator creator = getMyCreator(creatorEmail);
        List<Long> threadIds = messageThreadRepository
                .findOpenThreadIdsForCreator(creator, ThreadStatus.OPEN);

        long unread = messageThreadService.sumUnread(threadIds, ParticipantType.CREATOR, creator.getId());
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
        Map<Long, MessageCardReader.CardView> cards = loadCards(page);
        Long respondableProposalId = cards.isEmpty() ? null
                : adjustmentViews.respondableProposalId(thread, SettlementParty.CREATOR);
        List<MessageItem> items = page.stream()
                .map(m -> toMessageItem(m, creator.getId(), attachmentsByMessage.getOrDefault(m.getId(), List.of()),
                        cards.get(m.getId()), respondableProposalId))
                .toList();
        return new MessageListResponse(items, nextCursor, hasNext);
    }

    public record SendMessageOutcome(MessageItem item, boolean created) {
    }

    @Transactional
    public SendMessageOutcome sendMessage(String creatorEmail, Long threadId, SendMessageRequest request) {
        Creator creator = getMyCreator(creatorEmail);
        MessageThread thread = getMyThread(creator, threadId);
        adjustmentThreadGuard.requireWritable(thread);

        MessageThreadService.SendResult result = messageThreadService.sendMessage(
                thread, ParticipantType.CREATOR, creator.getId(),
                request.getClientMessageId(), request.getContent(), request.getAttachmentIds());

        Map<Long, List<AttachmentSummary>> attachments = loadAttachments(List.of(result.message()));
        MessageItem item = toMessageItem(result.message(), creator.getId(),
                attachments.getOrDefault(result.message().getId(), List.of()), null, null);
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
        adjustmentThreadGuard.requireWritable(thread);
        return messageAttachmentService.createPresignedUpload(thread, ParticipantType.CREATOR, creator.getId(), request);
    }

    /** §4-1 ③ — 업로드 완료 통지. */
    @Transactional
    public AttachmentSummary completeUpload(String creatorEmail, Long attachmentId, CompleteAttachmentRequest request) {
        Creator creator = getMyCreator(creatorEmail);
        return messageAttachmentService.completeUpload(
                ParticipantType.CREATOR, creator.getId(), attachmentId, request.getDurationSeconds());
    }

    /**
     * §13-8 · §13-9 — 첨부 다운로드 URL <b>일괄</b> 발급. 파일 하나를 누를 때도, 메시지의 「전체 다운로드」도 이 길이다.
     * 스레드 참가자면 상대(브랜드)가 보낸 첨부도 받을 수 있어야 하므로 업로더 본인 여부가 아니라
     * <b>첨부가 속한 스레드가 내 것인지</b>로 권한을 판정한다. 스레드는 서로 다른 것만 한 번씩 본다.
     *
     * <p>하나라도 권한이 없으면 전체를 거절한다 — 서명은 전부 검증한 뒤에 한다.
     */
    public List<AttachmentDownloadResponse> getDownloadUrls(String creatorEmail, List<Long> attachmentIds) {
        Creator creator = getMyCreator(creatorEmail);
        List<MessageAttachment> attachments = messageAttachmentService.loadForDownload(attachmentIds);
        attachments.stream()
                .map(attachment -> attachment.getThread().getId())
                .distinct()
                .forEach(threadId -> getMyThread(creator, threadId));
        return messageAttachmentService.createDownloadUrls(attachments, ParticipantType.CREATOR, creator.getId());
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

    /**
     * 공구 3자 스레드는 같은 브랜드의 두 번째 · 세 번째 줄이다 — 종류와 공구를 함께 내려 구분하게 한다(30-1 1-5).
     * [계약 확인] 게이트는 연결 쌍의 대화에만 붙는다.
     */
    private ThreadListItem toListItem(MessageThread thread, Map<Long, Long> unreadByThread,
                                      Set<Long> marketsWithContract, Map<Long, String> groupBuyTitles,
                                      Map<Long, ThreadAdjustmentBadge> adjustments) {
        Connection connection = thread.getConnection();
        boolean isOperator = connection.getType() == ConnectionType.OPERATOR_CREATOR;
        String name = isOperator ? OPERATOR_CHANNEL_NAME : connection.getMarket().getMarketName();
        long unread = unreadByThread.getOrDefault(thread.getId(), 0L);
        boolean hasContract = !isOperator && !thread.getKind().isGroupBuy()
                && marketsWithContract.contains(connection.getMarket().getId());

        return new ThreadListItem(
                thread.getId(), name, isOperator ? null : connection.getMarket().getMarketImageUrl(),
                isOperator, hasContract, thread.getLastMessagePreview(), thread.getLastMessageAt(), unread,
                thread.getKind(), thread.getSubjectId(),
                thread.getSubjectId() == null ? null : groupBuyTitles.get(thread.getSubjectId()),
                adjustments.get(thread.getId()));
    }

    /** 카드가 있는 페이지만 읽는다 — 카드 없는 대화가 대부분이다. */
    private Map<Long, MessageCardReader.CardView> loadCards(List<Message> messages) {
        return messages.stream().anyMatch(Message::isCard) ? messageCardReader.read(messages) : Map.of();
    }

    /**
     * 카드는 내가 누른 요청으로 생겼어도 내 말풍선이 아니다 — {@code mine}은 항상 false다(36 설계 7절).
     * 운영자 이름 · id · 자동 안내 표시는 이 응답에 싣지 않는다 — 상대에게 운영팀은 「SHOWROOMZ 운영팀」뿐이다(§36-4).
     */
    private MessageItem toMessageItem(Message message, Long myCreatorId, List<AttachmentSummary> attachments,
                                      MessageCardReader.CardView card, Long respondableProposalId) {
        boolean mine = !message.isCard()
                && message.getSenderType() == ParticipantType.CREATOR && message.getSenderId().equals(myCreatorId);
        return new MessageItem(message.getId(), message.getSenderType(), mine, message.getContent(), attachments,
                message.getCreatedAt(), message.getMessageType(), MessageCardResponse.from(card, respondableProposalId));
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

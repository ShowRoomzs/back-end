package showroomz.api.seller.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.thread.dto.MessageItem;
import showroomz.api.seller.thread.dto.MessageListResponse;
import showroomz.api.seller.thread.dto.SendMessageRequest;
import showroomz.api.seller.thread.dto.ThreadListItem;
import showroomz.api.seller.thread.dto.ThreadSummaryResponse;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
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
public class SellerThreadService {

    private static final String OPERATOR_CHANNEL_NAME = "SHOWROOMZ 운영팀";

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final MessageThreadService messageThreadService;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageAttachmentService messageAttachmentService;

    /** §13-1 좌측 목록. */
    public PageResponse<ThreadListItem> getThreads(String sellerEmail, PagingRequest pagingRequest) {
        Market market = getMyMarket(sellerEmail);
        Page<ThreadListItem> page = messageThreadRepository
                .findOpenThreadsForMarket(market, ThreadStatus.OPEN, pagingRequest.toPageable())
                .map(thread -> toListItem(thread, market));
        return PageResponse.of(page);
    }

    /** GNB 배지용 — 폴링 대상. */
    public ThreadSummaryResponse getSummary(String sellerEmail) {
        Market market = getMyMarket(sellerEmail);
        List<MessageThread> threads = messageThreadRepository
                .findOpenThreadsForMarket(market, ThreadStatus.OPEN, Pageable.unpaged())
                .getContent();

        long total = threads.stream()
                .mapToLong(t -> messageThreadService.countUnread(t, ParticipantType.SELLER, market.getId()))
                .sum();
        return new ThreadSummaryResponse(total);
    }

    /** §3-4 커서 페이징 — size+1개를 조회해 hasNext를 판정한다(별도 count 쿼리 없이). */
    public MessageListResponse getMessages(String sellerEmail, Long threadId, Long cursor, int size) {
        Market market = getMyMarket(sellerEmail);
        MessageThread thread = getMyThread(market, threadId);

        List<Message> fetched = messageThreadService.getMessages(thread, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<Message> page = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        Map<Long, List<AttachmentSummary>> attachmentsByMessage = loadAttachments(page);
        List<MessageItem> items = page.stream()
                .map(m -> toMessageItem(m, market.getId(), attachmentsByMessage.getOrDefault(m.getId(), List.of())))
                .toList();
        return new MessageListResponse(items, nextCursor, hasNext);
    }

    public record SendMessageOutcome(MessageItem item, boolean created) {
    }

    @Transactional
    public SendMessageOutcome sendMessage(String sellerEmail, Long threadId, SendMessageRequest request) {
        Market market = getMyMarket(sellerEmail);
        MessageThread thread = getMyThread(market, threadId);

        MessageThreadService.SendResult result = messageThreadService.sendMessage(
                thread, ParticipantType.SELLER, market.getId(),
                request.getClientMessageId(), request.getContent(), request.getAttachmentIds());

        Map<Long, List<AttachmentSummary>> attachments = loadAttachments(List.of(result.message()));
        MessageItem item = toMessageItem(result.message(), market.getId(),
                attachments.getOrDefault(result.message().getId(), List.of()));
        return new SendMessageOutcome(item, result.created());
    }

    @Transactional
    public void markRead(String sellerEmail, Long threadId) {
        Market market = getMyMarket(sellerEmail);
        MessageThread thread = getMyThread(market, threadId);
        messageThreadService.markRead(thread, ParticipantType.SELLER, market.getId());
    }

    /** §4-1 ① — presign 발급. */
    @Transactional
    public PresignResponse createPresignedUpload(String sellerEmail, Long threadId, PresignRequest request) {
        Market market = getMyMarket(sellerEmail);
        MessageThread thread = getMyThread(market, threadId);
        return messageAttachmentService.createPresignedUpload(thread, ParticipantType.SELLER, market.getId(), request);
    }

    /** §4-1 ③ — 업로드 완료 통지. */
    @Transactional
    public AttachmentSummary completeUpload(String sellerEmail, Long attachmentId, CompleteAttachmentRequest request) {
        Market market = getMyMarket(sellerEmail);
        return messageAttachmentService.completeUpload(
                ParticipantType.SELLER, market.getId(), attachmentId, request.getDurationSeconds());
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

    private ThreadListItem toListItem(MessageThread thread, Market market) {
        Connection connection = thread.getConnection();
        boolean isOperator = connection.getType() == ConnectionType.OPERATOR_MARKET;
        String name = isOperator ? OPERATOR_CHANNEL_NAME : connection.getCreator().getShowroomName();
        long unread = messageThreadService.countUnread(thread, ParticipantType.SELLER, market.getId());

        return new ThreadListItem(
                thread.getId(), name, null, isOperator, connection.getStatus(),
                thread.getLastMessagePreview(), thread.getLastMessageAt(), unread);
    }

    private MessageItem toMessageItem(Message message, Long myMarketId, List<AttachmentSummary> attachments) {
        boolean mine = message.getSenderType() == ParticipantType.SELLER && message.getSenderId().equals(myMarketId);
        return new MessageItem(message.getId(), message.getSenderType(), mine, message.getContent(), attachments, message.getCreatedAt());
    }

    private MessageThread getMyThread(Market market, Long threadId) {
        MessageThread thread = messageThreadRepository.findById(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THREAD_NOT_FOUND));

        Market threadMarket = thread.getConnection().getMarket();
        if (threadMarket == null || !threadMarket.getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.THREAD_ACCESS_DENIED);
        }
        return thread;
    }

    private Market getMyMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }
}

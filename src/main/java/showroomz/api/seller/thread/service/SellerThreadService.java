package showroomz.api.seller.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
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
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
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
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final MessageCardReader messageCardReader;
    private final GroupBuyRepository groupBuyRepository;

    /**
     * §13-1 좌측 목록. 안 읽은 수는 페이지 전체를 한 쿼리로 집계한다(스레드당 카운트 금지).
     * keyword는 좌측 목록 상단의 "쇼룸명 검색"(A1~A11) — 비어 있으면 전체를 내려준다.
     */
    public PageResponse<ThreadListItem> getThreads(String sellerEmail, String keyword, PagingRequest pagingRequest) {
        Market market = getMyMarket(sellerEmail);
        Page<MessageThread> threads = messageThreadRepository
                .findOpenThreadsForMarket(market, ThreadStatus.OPEN, normalizeKeyword(keyword),
                        pagingRequest.toPageable());

        Map<Long, Long> unreadByThread = messageThreadService.countUnreadByThreadIds(
                threads.getContent().stream().map(MessageThread::getId).toList(),
                ParticipantType.SELLER, market.getId());
        Map<Long, String> groupBuyTitles = groupBuyRepository.findTitleMapByIds(
                threads.getContent().stream().map(MessageThread::getSubjectId).filter(Objects::nonNull).distinct().toList());

        return PageResponse.of(threads.map(thread -> toListItem(thread, unreadByThread, groupBuyTitles)));
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    /** GNB 배지용 — 폴링 대상(§0)이라 스레드 수와 무관하게 쿼리 2회로 고정한다. */
    public ThreadSummaryResponse getSummary(String sellerEmail) {
        Market market = getMyMarket(sellerEmail);
        List<Long> threadIds = messageThreadRepository
                .findOpenThreadIdsForMarket(market, ThreadStatus.OPEN);

        long total = messageThreadService.sumUnread(threadIds, ParticipantType.SELLER, market.getId());
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
        Map<Long, MessageCardReader.CardView> cards = loadCards(page);
        List<MessageItem> items = page.stream()
                .map(m -> toMessageItem(m, market.getId(), attachmentsByMessage.getOrDefault(m.getId(), List.of()),
                        cards.get(m.getId())))
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
                attachments.getOrDefault(result.message().getId(), List.of()), null);
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

    /**
     * §13-8 · §13-9 — 첨부 다운로드 URL <b>일괄</b> 발급. 파일 하나를 누를 때도, 메시지의 「전체 다운로드」도 이 길이다.
     * 스레드 참가자면 상대(인플루언서)가 보낸 첨부도 받을 수 있어야 하므로 업로더 본인 여부가 아니라
     * <b>첨부가 속한 스레드가 내 것인지</b>로 권한을 판정한다. 스레드는 서로 다른 것만 한 번씩 본다.
     *
     * <p>하나라도 권한이 없으면 전체를 거절한다 — 서명은 전부 검증한 뒤에 한다.
     */
    public List<AttachmentDownloadResponse> getDownloadUrls(String sellerEmail, List<Long> attachmentIds) {
        Market market = getMyMarket(sellerEmail);
        List<MessageAttachment> attachments = messageAttachmentService.loadForDownload(attachmentIds);
        attachments.stream()
                .map(attachment -> attachment.getThread().getId())
                .distinct()
                .forEach(threadId -> getMyThread(market, threadId));
        return messageAttachmentService.createDownloadUrls(attachments, ParticipantType.SELLER, market.getId());
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

    /** 공구 3자 스레드는 같은 상대의 두 번째 · 세 번째 줄이다 — 종류와 공구를 함께 내려 구분하게 한다(30-1 1-5). */
    private ThreadListItem toListItem(MessageThread thread, Map<Long, Long> unreadByThread,
                                      Map<Long, String> groupBuyTitles) {
        Connection connection = thread.getConnection();
        boolean isOperator = connection.getType() == ConnectionType.OPERATOR_MARKET;
        String name = isOperator ? OPERATOR_CHANNEL_NAME : connection.getCreator().getShowroomName();
        long unread = unreadByThread.getOrDefault(thread.getId(), 0L);

        return new ThreadListItem(
                thread.getId(), name, isOperator ? null : profileImageUrlOf(connection.getCreator()),
                isOperator, connection.getStatus(),
                isOperator ? null : connection.getCreator().getId(), connection.getId(),
                thread.getLastMessagePreview(), thread.getLastMessageAt(), unread,
                thread.getKind(), thread.getSubjectId(),
                thread.getSubjectId() == null ? null : groupBuyTitles.get(thread.getSubjectId()));
    }

    /** 인플루언서 프로필 이미지는 CREATOR가 아니라 USERS에 있다(운영자 채널은 creator가 null). */
    private String profileImageUrlOf(Creator creator) {
        if (creator == null || creator.getUser() == null) {
            return null;
        }
        return creator.getUser().getProfileImageUrl();
    }

    /** 카드가 있는 페이지만 읽는다 — 카드 없는 대화가 대부분이다. */
    private Map<Long, MessageCardReader.CardView> loadCards(List<Message> messages) {
        return messages.stream().anyMatch(Message::isCard) ? messageCardReader.read(messages) : Map.of();
    }

    /**
     * 카드는 내가 누른 요청으로 생겼어도 내 말풍선이 아니다 — {@code mine}은 항상 false다(36 설계 7절).
     * 운영자 이름 · id · 자동 안내 표시는 이 응답에 싣지 않는다 — 상대에게 운영팀은 「SHOWROOMZ 운영팀」뿐이다(§36-4).
     */
    private MessageItem toMessageItem(Message message, Long myMarketId, List<AttachmentSummary> attachments,
                                      MessageCardReader.CardView card) {
        boolean mine = !message.isCard()
                && message.getSenderType() == ParticipantType.SELLER && message.getSenderId().equals(myMarketId);
        return new MessageItem(message.getId(), message.getSenderType(), mine, message.getContent(), attachments,
                message.getCreatedAt(), message.getMessageType(), MessageCardResponse.from(card));
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

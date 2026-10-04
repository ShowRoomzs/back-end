package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadKind;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

/**
 * 공구 스레드 포트 구현(30-1 1-4) — 판매 포트를 주문 도메인이 구현한 것처럼 스레드 포트는 메시지 도메인이 구현한다.
 *
 * <p>3자 스레드는 그 쌍의 PAIR 연결에 {@link ThreadKind}만 달리해 붙인다. 참여자 · 접근 판정 · 목록 · 안 읽은 수가
 * 연결 기준이라 연결·소통 화면이 손대지 않고 3자 스레드를 보여준다. 운영자는 이 테이블의 참여자가 아니라
 * 보낸 사람({@code ADMIN})으로만 나타난다 — 운영자 열람 화면은 게시물·소통 모니터링 소관이다.
 *
 * <p>개설은 호출자의 트랜잭션에 합류한다 — 스레드 · 첫 글 · 이슈(또는 이행 확인) 행이 함께 커밋되거나 함께 사라진다.
 * 기획 제외 패키지는 쓰지 않는다 — 마켓·연결 해석은 도메인 리포지토리로만 한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageGroupBuyThreadGateway implements GroupBuyThreadGateway {

    private static final String ISSUE_FIRST_MESSAGE_ID = "group-buy-issue-open";
    private static final String FULFILLMENT_FIRST_MESSAGE_ID = "group-buy-fulfillment-%s";

    private final ConnectionRepository connectionRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final MessageRepository messageRepository;
    private final MessageThreadService messageThreadService;

    /**
     * 계약의 {@code connection}은 스레드 경유로 상대가 고정된 계약에만 있다. 없으면 읽는 시점의 CONNECTED
     * 연결에서 찾는다 — 계약 상세의 「스레드 열기」와 같은 규칙이다. 그 사이 연결이 끊겼으면 empty가 맞다.
     */
    @Override
    public Optional<Long> findPairThreadId(GroupBuy groupBuy) {
        Contract contract = groupBuy.getContract();
        Optional<Connection> connection = contract.getConnection() != null
                ? Optional.of(contract.getConnection())
                : connectionRepository.findConnectedPair(groupBuy.getMarket().getId(), groupBuy.getCreator().getId());
        return connection.flatMap(pair -> messageThreadRepository.findByConnectionAndKind(pair, ThreadKind.CONNECTION))
                .map(thread -> thread.getId());
    }

    @Override
    @Transactional
    public Long openIssueThread(GroupBuy groupBuy, FulfillmentSide openerSide, GroupBuyIssueType issueType,
                                String content) {
        MessageThread thread = openThread(groupBuy, ThreadKind.GROUP_BUY_ISSUE);
        post(thread, senderType(openerSide), senderId(groupBuy, openerSide), ISSUE_FIRST_MESSAGE_ID, content);
        return thread.getId();
    }

    @Override
    @Transactional
    public Long openAdminIssueThread(GroupBuy groupBuy, Long operatorId, GroupBuyIssueType issueType, String content) {
        MessageThread thread = openThread(groupBuy, ThreadKind.GROUP_BUY_ISSUE);
        post(thread, ParticipantType.ADMIN, operatorId, ISSUE_FIRST_MESSAGE_ID, content);
        return thread.getId();
    }

    /**
     * 양측이 모두 미이행을 내면 두 번째 사유는 같은 스레드의 글이 된다 — 어드민 상세의 {@code fulfillment.threadId}가
     * 단일 값이다. 호출자가 공구를 {@code PESSIMISTIC_WRITE}로 잠그고 들어오므로 조회 후 생성이 경합하지 않는다.
     */
    @Override
    @Transactional
    public Long openFulfillmentDisputeThread(GroupBuy groupBuy, FulfillmentSide checkerSide, String reason) {
        MessageThread thread = messageThreadRepository
                .findFirstByKindAndSubjectIdOrderByIdAsc(ThreadKind.GROUP_BUY_FULFILLMENT, groupBuy.getId())
                .orElseGet(() -> openThread(groupBuy, ThreadKind.GROUP_BUY_FULFILLMENT));
        post(thread, senderType(checkerSide), senderId(groupBuy, checkerSide),
                FULFILLMENT_FIRST_MESSAGE_ID.formatted(checkerSide.name().toLowerCase()), reason);
        return thread.getId();
    }

    @Override
    public Optional<GroupBuyActorType> findLastSpeaker(Long threadId) {
        if (threadId == null) {
            return Optional.empty();
        }
        return messageThreadRepository.findById(threadId)
                .flatMap(messageRepository::findTopByThreadOrderByIdDesc)
                .map(message -> switch (message.getSenderType()) {
                    case SELLER -> GroupBuyActorType.SELLER;
                    case CREATOR -> GroupBuyActorType.CREATOR;
                    case ADMIN -> GroupBuyActorType.ADMIN;
                });
    }

    /** 끊긴 쌍에는 열지 않는다 — 끊긴 연결에 붙은 스레드는 양측 목록에서 찾을 길이 없다(30-1 7절 #3). */
    private MessageThread openThread(GroupBuy groupBuy, ThreadKind kind) {
        Connection pair = connectedPair(groupBuy)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_THREAD_UNAVAILABLE));
        return messageThreadRepository.save(MessageThread.openForGroupBuy(pair, kind, groupBuy.getId()));
    }

    private Optional<Connection> connectedPair(GroupBuy groupBuy) {
        Connection fixed = groupBuy.getContract().getConnection();
        if (fixed != null && fixed.getStatus() == ConnectionStatus.CONNECTED) {
            return Optional.of(fixed);
        }
        return connectionRepository.findConnectedPair(groupBuy.getMarket().getId(), groupBuy.getCreator().getId());
    }

    private void post(MessageThread thread, ParticipantType senderType, Long senderId, String clientMessageId,
                      String content) {
        messageThreadService.sendMessage(thread, senderType, senderId, clientMessageId, content, null);
    }

    private static ParticipantType senderType(FulfillmentSide side) {
        return side == FulfillmentSide.SELLER ? ParticipantType.SELLER : ParticipantType.CREATOR;
    }

    /** 연결·소통의 보낸 사람 규칙 — 브랜드는 마켓 id, 인플루언서는 크리에이터 id다. */
    private static Long senderId(GroupBuy groupBuy, FulfillmentSide side) {
        return side == FulfillmentSide.SELLER ? groupBuy.getMarket().getId() : groupBuy.getCreator().getId();
    }
}

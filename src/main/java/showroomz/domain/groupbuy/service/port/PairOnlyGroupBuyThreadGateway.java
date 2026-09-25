package showroomz.domain.groupbuy.service.port;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

/**
 * 연결·소통의 스레드 모델 변경 전 구현 — PAIR 스레드 해석만 한다(설계서 5-3).
 *
 * <p>이슈·미이행 스레드 개설은 {@code GROUP_BUY_THREAD_UNAVAILABLE}로 거절한다. 스레드 없이 이슈 행만
 * 만들면 「스레드로 이동」이 갈 곳이 없고, B5e의 「중복 개설 방지」가 가리킬 대상도 없다.
 * 연결·소통이 {@code thread_kind}를 도입하면 이 클래스를 실제 구현으로 교체한다.
 *
 * <p>기획 제외 패키지는 쓰지 않는다 — 마켓·연결 해석은 도메인 리포지토리로만 한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PairOnlyGroupBuyThreadGateway implements GroupBuyThreadGateway {

    private final ConnectionRepository connectionRepository;
    private final MessageThreadRepository messageThreadRepository;

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
        return connection.flatMap(messageThreadRepository::findByConnection).map(thread -> thread.getId());
    }

    @Override
    public Long openIssueThread(GroupBuy groupBuy, FulfillmentSide openerSide, GroupBuyIssueType issueType,
                                String content) {
        throw new BusinessException(ErrorCode.GROUP_BUY_THREAD_UNAVAILABLE);
    }

    @Override
    public Long openAdminIssueThread(GroupBuy groupBuy, GroupBuyIssueType issueType, String content) {
        throw new BusinessException(ErrorCode.GROUP_BUY_THREAD_UNAVAILABLE);
    }

    @Override
    public Long openFulfillmentDisputeThread(GroupBuy groupBuy, FulfillmentSide checkerSide, String reason) {
        throw new BusinessException(ErrorCode.GROUP_BUY_THREAD_UNAVAILABLE);
    }
}

package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentThreadPort;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

/**
 * 정산 조정 3자 스레드 포트 구현(44 이슈 스레드 설계서 0-4 · 2-1 ⑥) — 그 쌍의 PAIR 연결에 {@link ThreadKind#SETTLEMENT_ADJUSTMENT}
 * 스레드를 붙인다. 참여자 · 접근 판정 · 목록 · 안 읽은 수 · 첨부 규칙이 연결 기준 그대로 동작한다.
 *
 * <p>계약이 스레드 경유로 상대를 고정했으면 그 연결을, 아니면 지금 CONNECTED 인 쌍을 쓴다(공구 스레드 포트와 같은 규칙). 끊긴 쌍에는
 * 열지 않는다 — 끊긴 연결에 붙은 스레드는 양측 목록에서 찾을 길이 없다(10-2 #4).
 */
@Component
@RequiredArgsConstructor
public class MessageSettlementAdjustmentThreadGateway implements SettlementAdjustmentThreadPort {

    private final GroupBuyRepository groupBuyRepository;
    private final ConnectionRepository connectionRepository;
    private final MessageThreadRepository messageThreadRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long openAdjustmentThread(Long groupBuyId, Long marketId, Long creatorId) {
        Connection pair = connectedPair(groupBuyId, marketId, creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_THREAD_UNAVAILABLE));
        return messageThreadRepository.save(
                MessageThread.openForGroupBuy(pair, ThreadKind.SETTLEMENT_ADJUSTMENT, groupBuyId)).getId();
    }

    private Optional<Connection> connectedPair(Long groupBuyId, Long marketId, Long creatorId) {
        Connection fixed = groupBuyRepository.findById(groupBuyId).map(GroupBuy::getContract)
                .map(contract -> contract.getConnection()).orElse(null);
        if (fixed != null && fixed.getStatus() == ConnectionStatus.CONNECTED) {
            return Optional.of(fixed);
        }
        return connectionRepository.findConnectedPair(marketId, creatorId);
    }
}

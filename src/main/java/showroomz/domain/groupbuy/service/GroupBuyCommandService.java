package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyIssueStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * 다른 모듈이 공구에 알려주는 사실(설계서 5-2 · 5-3). 공구 모듈은 정산·스레드를 폴링하지 않는다.
 *
 * <p>정산 상태머신(정산대기 → 운영자 확인 → 이체완료)은 공구에 두지 않는다 — 공구가 아는 것은
 * {@code SETTLED} 하나다(§32-6). 정산 명세도 여기서 계산하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GroupBuyCommandService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;
    private final GroupBuyIssueRepository issueRepository;
    private final GroupBuySalesReader salesReader;
    private final GroupBuyHistoryRecorder historyRecorder;

    /** 정산 → 공구 · 이체 완료 통보. ENDED → SETTLED. */
    public void markSettled(Long groupBuyId, LocalDateTime transferredAt) {
        GroupBuy groupBuy = load(groupBuyId);
        if (groupBuyRepository.transition(groupBuyId, EnumSet.of(GroupBuyStatus.ENDED), GroupBuyStatus.SETTLED) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
        groupBuy.applySettled(transferredAt);
        historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.SETTLED, null, transferredAt);
    }

    /** 정산 → 공구 · B6 「전 주문 종결 · 실적 확정」. 상태는 바꾸지 않는다. */
    public void recordSalesFinalized(Long groupBuyId, LocalDateTime finalizedAt) {
        historyRecorder.recordBySystem(load(groupBuyId), GroupBuyEventType.SALES_FINALIZED, null, finalizedAt);
    }

    /**
     * 공구 → 정산 · 정산 게이트. ENDED ∧ 미종결 0 ∧ 양측 이행 확인 ∧ 보류 아님.
     * <b>판매 포트가 비어 있으면 false</b>다 — 모르는 미종결 건수를 0으로 읽으면 게이트가 거짓으로 열린다(설계서 0-6).
     */
    @Transactional(readOnly = true)
    public boolean isSettlementReady(Long groupBuyId) {
        GroupBuy groupBuy = load(groupBuyId);
        if (groupBuy.getStatus() != GroupBuyStatus.ENDED) {
            return false;
        }
        boolean allOrdersClosed = salesReader.readClosure(groupBuyId)
                .map(closure -> closure.unclosedCount() == 0)
                .orElse(false);
        List<GroupBuyFulfillmentCheck> checks = fulfillmentCheckRepository.findByGroupBuyId(groupBuyId);
        return allOrdersClosed && checks.size() == 2 && !isSettlementOnHold(groupBuy, checks);
    }

    /** 정산 보류 = 파생값 — 미이행이 있고 양측 동의 종결이 아직이다(설계서 1-9). 별도 hold 컬럼을 두지 않는다. */
    public static boolean isSettlementOnHold(GroupBuy groupBuy, List<GroupBuyFulfillmentCheck> checks) {
        return groupBuy.getFulfillmentResolvedAt() == null
                && checks.stream().anyMatch(GroupBuyFulfillmentCheck::isUnfulfilled);
    }

    /** 연결·소통 → 공구 · 미이행 스레드 양측 동의 종결(제20조⑤). 당사자 합의이지 운영자 판정이 아니다. */
    public void resolveFulfillmentDispute(Long groupBuyId, String note, LocalDateTime resolvedAt) {
        GroupBuy groupBuy = load(groupBuyId);
        if (groupBuy.getFulfillmentResolvedAt() != null) {
            return;
        }
        groupBuy.applyFulfillmentResolved(note, resolvedAt);
        historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.FULFILLMENT_RESOLVED, null, resolvedAt);
    }

    /** 연결·소통 → 공구 · 이슈 스레드 종결. 이후 새 이견은 새 이슈다. */
    public void closeIssue(Long groupBuyId, LocalDateTime closedAt) {
        issueRepository.findFirstByGroupBuyIdAndStatus(groupBuyId, GroupBuyIssueStatus.OPEN)
                .ifPresent(issue -> issue.close(closedAt));
    }

    private GroupBuy load(Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
    }
}

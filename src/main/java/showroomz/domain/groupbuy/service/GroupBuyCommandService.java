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

    /**
     * 연결·소통 → 공구 · 이행 3자 스레드 양측 동의 종결(제20조⑤ · 32 설계 8-4). 당사자 합의이지 운영자 판정이 아니다 —
     * 미이행 확인 행은 고치지 않는다(제20조② 불가역). <b>정산 보류는 여기서 풀리지 않는다</b> — 해제는 정산 관리가 한다.
     * 멱등 — 이미 합의 시각이 있으면 무시한다.
     */
    public void recordFulfillmentAgreement(Long groupBuyId, LocalDateTime agreedAt, String note) {
        GroupBuy groupBuy = groupBuyRepository.findForUpdate(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        if (groupBuy.getFulfillmentAgreedAt() != null) {
            return;
        }
        groupBuy.applyFulfillmentAgreed(note, agreedAt);
        historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.FULFILLMENT_AGREED, "양측 동의", agreedAt);
    }

    /**
     * 정산 관리 → 공구 · 정산 보류 해제(32 설계 8-4). <b>합의 없이 해제가 오면 거부한다</b> — 제20조⑤ 「양측 모두 동의해야
     * 종결·보류 해제」. D-2(보류 출구 없음)의 답이 나올 때까지 이 가드를 둔다. 멱등.
     */
    public void releaseFulfillmentHold(Long groupBuyId, Long operatorId, String operatorName,
                                       LocalDateTime releasedAt) {
        GroupBuy groupBuy = groupBuyRepository.findForUpdate(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        if (groupBuy.getFulfillmentResolvedAt() != null) {
            return;
        }
        if (groupBuy.getFulfillmentAgreedAt() == null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FULFILLMENT_NOT_AGREED);
        }
        groupBuy.applyFulfillmentHoldReleased(releasedAt);
        historyRecorder.record(groupBuy, GroupBuyEventType.FULFILLMENT_RESOLVED,
                GroupBuyActor.admin(operatorId, operatorName), "정산 관리", null, releasedAt);
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

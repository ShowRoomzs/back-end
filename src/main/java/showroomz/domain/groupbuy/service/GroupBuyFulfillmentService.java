package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 계약 이행 확인 쓰기 — 측별 1회 · 2지 · 불가역(제20조② · 30 설계 1-9).
 *
 * <p>파트너와 스튜디오가 <b>같은 메서드</b>를 측만 바꿔 부른다(31 설계 5-4). 불가역·스레드 개설·보류 파생 규칙이
 * 두 벌이 되면 한쪽만 고쳐진다. 버튼 판정(허용 여부)은 각 서피스의 권한 정책이 먼저 하고 들어온다.
 *
 * <p>미이행이면 <b>같은 트랜잭션에서</b> 3자 스레드를 연다 — 스레드 없이 확인만 남으면 「첫 글로 등록되었습니다」가 거짓이 된다.
 */
@Service
@RequiredArgsConstructor
public class GroupBuyFulfillmentService {

    private final GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;
    private final GroupBuyThreadGateway threadGateway;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final GroupBuyNotifier notifier;

    /**
     * @param lockedGroupBuy 호출자가 {@code PESSIMISTIC_WRITE}로 잠근 공구
     * @param side           확인하는 측 — 확인 대상은 <b>상대의 의무</b>다
     * @param reason         미이행 사유 — trim 후 값. UNFULFILLED면 필수
     * @param checkerId      셀러 id 또는 크리에이터 id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupBuyFulfillmentCheck check(GroupBuy lockedGroupBuy, FulfillmentSide side, FulfillmentResult result,
                                          String reason, Long checkerId, LocalDateTime now) {
        boolean unfulfilled = result == FulfillmentResult.UNFULFILLED;
        if (unfulfilled && reason == null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FULFILLMENT_REASON_REQUIRED);
        }
        Long threadId = unfulfilled ? threadGateway.openFulfillmentDisputeThread(lockedGroupBuy, side, reason) : null;

        GroupBuyFulfillmentCheck check;
        try {
            check = fulfillmentCheckRepository.saveAndFlush(GroupBuyFulfillmentCheck.manual(lockedGroupBuy, side,
                    result, unfulfilled ? reason : null, checkerId, threadId, now));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(group_buy_id, checker_side) — 선검사를 통과한 동시 제출은 DB가 떨어뜨린다.
            throw new BusinessException(ErrorCode.GROUP_BUY_FULFILLMENT_ALREADY_CHECKED);
        }

        GroupBuyEventType eventType = unfulfilled
                ? GroupBuyEventType.FULFILLMENT_DISPUTED : GroupBuyEventType.FULFILLMENT_CONFIRMED;
        String detail = unfulfilled ? reason : null;
        if (side == FulfillmentSide.SELLER) {
            historyRecorder.recordBySeller(lockedGroupBuy, eventType, detail, check.getId(), now);
            notifier.notifyCreator(lockedGroupBuy, eventType.name());
        } else {
            historyRecorder.recordByCreator(lockedGroupBuy, eventType, detail, check.getId(), now);
            notifier.notifySeller(lockedGroupBuy, eventType.name());
        }
        if (unfulfilled) {
            notifier.notifyAdmin(lockedGroupBuy, eventType.name());
        }
        return check;
    }
}

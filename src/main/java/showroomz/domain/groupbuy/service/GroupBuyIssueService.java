package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyIssue;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyIssueStatus;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 이슈 스레드 개설 — 파트너(30 설계 4-6)와 어드민(32 설계 8-3)이 <b>같은 메서드</b>를 연 사람만 바꿔 부른다.
 * 열린 이슈 1건 제약 · 스레드 포트 동일 트랜잭션 · 이력 규칙이 두 벌이 되면 한쪽만 고쳐진다.
 *
 * <p>이슈는 공구 상태를 바꾸지 않고 정산도 보류하지 않는다(§29-10). 스레드 개설과 이슈 INSERT가 같은 트랜잭션이다 —
 * 스레드만 생기고 이슈 행이 없으면 「중복 개설 방지」가 깨진다. 허용 여부 판정은 각 서피스의 권한 정책이 먼저 한다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyIssueService {

    private final GroupBuyIssueRepository issueRepository;
    private final GroupBuyThreadGateway threadGateway;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final GroupBuyNotifier notifier;

    /**
     * @param lockedGroupBuy 호출자가 {@code PESSIMISTIC_WRITE}로 잠근 공구
     * @param opener         이력의 행위자
     * @param openerId       {@code group_buy_issue.opener_id} — 셀러 id 또는 운영자 id
     * @param content        trim 후 값
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupBuyIssue open(GroupBuy lockedGroupBuy, GroupBuyActor opener, Long openerId,
                              GroupBuyIssueType issueType, String content, LocalDateTime now) {
        if (issueRepository.existsByGroupBuyIdAndStatus(lockedGroupBuy.getId(),
                GroupBuyIssueStatus.OPEN)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ISSUE_ALREADY_OPEN);
        }
        Long threadId = switch (opener.type()) {
            case SELLER -> threadGateway.openIssueThread(lockedGroupBuy, FulfillmentSide.SELLER, issueType, content);
            case CREATOR -> threadGateway.openIssueThread(lockedGroupBuy, FulfillmentSide.CREATOR, issueType, content);
            case ADMIN -> threadGateway.openAdminIssueThread(lockedGroupBuy, issueType, content);
            case SYSTEM -> throw new IllegalArgumentException("시스템은 이슈를 열지 않는다");
        };
        GroupBuyIssue issue;
        try {
            issue = issueRepository.saveAndFlush(GroupBuyIssue.open(lockedGroupBuy, opener.type(), openerId,
                    issueType, content, threadId, now));
        } catch (DataIntegrityViolationException e) {
            // open_group_buy_id UNIQUE — 선검사를 통과한 동시 개설은 DB가 떨어뜨린다.
            throw new BusinessException(ErrorCode.GROUP_BUY_ISSUE_ALREADY_OPEN);
        }
        historyRecorder.record(lockedGroupBuy, GroupBuyEventType.ISSUE_OPENED, opener, issueType.getLabel(),
                issue.getId(), now);
        if (opener.type() != GroupBuyActorType.SELLER) {
            notifier.notifySeller(lockedGroupBuy, "ISSUE_OPENED");
        }
        if (opener.type() != GroupBuyActorType.CREATOR) {
            notifier.notifyCreator(lockedGroupBuy, "ISSUE_OPENED");
        }
        if (opener.type() != GroupBuyActorType.ADMIN) {
            notifier.notifyAdmin(lockedGroupBuy, "ISSUE_OPENED");
        }
        return issue;
    }
}

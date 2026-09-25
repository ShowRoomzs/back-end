package showroomz.api.admin.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDto;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyIssue;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRevisionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyActor;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.GroupBuyFactsLoader;
import showroomz.domain.groupbuy.service.GroupBuyHistoryRecorder;
import showroomz.domain.groupbuy.service.GroupBuyIssueService;
import showroomz.domain.groupbuy.service.GroupBuyNotifier;
import showroomz.domain.groupbuy.service.GroupBuyPostExposure;
import showroomz.domain.groupbuy.service.GroupBuyReadiness;
import showroomz.domain.groupbuy.service.GroupBuyTerminator;
import showroomz.domain.groupbuy.service.GroupBuyTerminator.Termination;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader.GroupBuySales;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;

/**
 * 어드민 공구 판정(32 설계 5~8절). <b>받는 것은 판정과 사유뿐</b>이다 — 조건·기간·게시물 본문을 고치는 API가 없다(0-2).
 *
 * <p>모든 실행은 공구 행을 {@code PESSIMISTIC_WRITE}로 잠그는 것으로 <b>시작</b>한다({@link AdminGroupBuyAccess} 참고).
 * 게시물을 건드리면 이어서 {@code group_buy_post}를 잠근다 — 스튜디오 게시물 쓰기와 같은 순서다(31 설계 2-7). 잠근 뒤
 * {@link AdminGroupBuyPermissionPolicy}의 <b>버튼 판정과 같은 메서드</b>로 다시 판정한다.
 *
 * <p>종결 네 경로(조기 마감·중단 요청 승인 · 직권 중단 집행 · 긴급)는 부수 효과를 {@link GroupBuyTerminator} 하나에 맡긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminGroupBuyCommandService {

    private static final DateTimeFormatter NOTICE_EXECUTE_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final DateTimeFormatter NOTICE_APPEAL_FORMAT = DateTimeFormatter.ofPattern("MM.dd");
    /** 3호 선택 시 FE 주의 문구 코드 — 「게시물 무단 변경」은 브랜드가 소명할 수 없는 사유다(C-2). C-2 개정 시 걷는다. */
    private static final String CLAUSE_CAUTION_POST_ALTERATION = "C2_POST_ALTERATION";

    private final AdminGroupBuyAccess access;
    private final AdminGroupBuyPermissionPolicy permissionPolicy;
    private final AdminSuspensionSchedule schedule;
    private final GroupBuyFactsLoader factsLoader;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository postRepository;
    private final GroupBuyPostRevisionRepository revisionRepository;
    private final GroupBuyChangeRequestRepository changeRequestRepository;
    private final GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    private final GroupBuyReadiness readiness;
    private final GroupBuyTerminator terminator;
    private final GroupBuyIssueService issueService;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final GroupBuyNotifier notifier;
    private final GroupBuySalesReader salesReader;
    private final GroupBuySettlementGateway settlementGateway;

    // ── 5-1 · 5-2 오픈 승인 · 반려 ────────────────────────────────────────────

    /**
     * 오픈 승인 — 게이트 ③. 게이트 ①이 이미 끝났으면 <b>이 요청이 READY를 만든다</b>. 응답에 전이 결과를 싣는다 —
     * B1의 「승인 → 준비완료」는 확정이 아니다. 지급 문구가 없다 — 오픈 승인의 결과는 공구가 열리는 것 하나다(§32-2).
     * 게시물 노출은 이 API가 하지 않는다 — 스케줄러 오픈이 한다.
     */
    public AdminGroupBuyDto.ActionResponse approveOpen(Long groupBuyId, Long operatorId) {
        Locked locked = lockWithPost(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        if (!permissionPolicy.canDecideOpen(locked.facts())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_OPEN_REVIEW_NOT_PENDING);
        }
        LocalDateTime now = LocalDateTime.now();
        GroupBuy groupBuy = locked.groupBuy();
        GroupBuyPost post = locked.post();
        post.approve(operatorId, now);
        historyRecorder.record(groupBuy, GroupBuyEventType.OPEN_APPROVED, admin, null, post.getPostId(), now);
        notifier.notifyBothParties(groupBuy, "OPEN_APPROVED");
        // 운영자가 마지막 게이트면 OPEN_APPROVED가 준비완료를 함께 말한다 — 별도 READY 이력을 남기지 않는다.
        readiness.promoteIfSatisfied(groupBuy, GroupBuyActorType.ADMIN, admin.id(), admin.displayName(), false, now);
        return result(groupBuy, post, null, null);
    }

    /**
     * 오픈 반려 — 설명은 사유와 무관하게 필수다(§32-2 「고칠 문장을 지목하지 않으면 재등록이 반복된다」). 공구는
     * PREPARING에서 멈춘다. 이력 detail은 사유 라벨만 — 설명 원문은 게시물 행에 있다.
     */
    public AdminGroupBuyDto.ActionResponse rejectOpen(Long groupBuyId, Long operatorId,
                                                      AdminGroupBuyDto.OpenRejectRequest request) {
        Locked locked = lockWithPost(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        if (!permissionPolicy.canDecideOpen(locked.facts())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_OPEN_REVIEW_NOT_PENDING);
        }
        String detail = requireText(request.detail(), ErrorCode.GROUP_BUY_REJECT_DETAIL_REQUIRED);

        LocalDateTime now = LocalDateTime.now();
        GroupBuy groupBuy = locked.groupBuy();
        GroupBuyPost post = locked.post();
        post.reject(request.reasonCode().name(), detail, operatorId, now);
        historyRecorder.record(groupBuy, GroupBuyEventType.OPEN_REJECTED, admin, request.reasonCode().getLabel(),
                post.getPostId(), now);
        notifier.notifyBothParties(groupBuy, "OPEN_REJECTED");
        return result(groupBuy, post, null, null);
    }

    // ── 5-3 · 5-4 게시물 숨김 · 해제 ──────────────────────────────────────────

    /**
     * 숨김 — 공구는 멈추지 않는다(§29-8 규칙 ①). 숨김 판본은 <b>잠금 안에서 읽은 최신 판본</b>을 박는다. 운영자가 본
     * 판본과 다르면 숨김은 여전히 유효하고 {@code revisionAdvanced}로 알린다(5-3).
     */
    public AdminGroupBuyDto.ActionResponse hidePost(Long groupBuyId, Long operatorId,
                                                    AdminGroupBuyDto.PostHideRequest request) {
        Locked locked = lockWithPost(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        if (locked.facts().isPostHidden()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_ALREADY_HIDDEN);
        }
        if (!permissionPolicy.canHidePost(locked.facts())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_NOT_HIDEABLE);
        }
        String detail = requireText(request.detail(), ErrorCode.GROUP_BUY_REJECT_DETAIL_REQUIRED);

        LocalDateTime now = LocalDateTime.now();
        GroupBuy groupBuy = locked.groupBuy();
        GroupBuyPost post = locked.post();
        Integer revisionNo = latestRevisionNo(post);
        post.hide(request.reasonCode().name(), detail, revisionNo, operatorId, now);
        GroupBuyPostExposure.sync(groupBuy, post, now);
        historyRecorder.record(groupBuy, GroupBuyEventType.POST_HIDDEN, admin, request.reasonCode().getLabel(),
                post.getPostId(), now);
        notifier.notifyBothParties(groupBuy, "POST_HIDDEN");
        boolean advanced = request.observedRevisionNo() != null
                && !request.observedRevisionNo().equals(revisionNo);
        return result(groupBuy, post, advanced, null);
    }

    /**
     * 숨김 해제 — <b>읽은 글 = 여는 글</b>을 잠금 + 판본 대조로 보장한다(5-4). 운영자가 판본 4를 읽는 사이 원래 문구
     * 판본 5로 되돌아갔으면 409다. 「고쳤는가」는 판정하지 않는다 — 숨김이 잘못이었다고 판단할 수 있어야 한다.
     */
    public AdminGroupBuyDto.ActionResponse unhidePost(Long groupBuyId, Long operatorId,
                                                      AdminGroupBuyDto.PostUnhideRequest request) {
        Locked locked = lockWithPost(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        if (!permissionPolicy.canUnhidePost(locked.facts())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_NOT_HIDDEN);
        }
        GroupBuyPost post = locked.post();
        Integer revisionNo = latestRevisionNo(post);
        if (!request.expectedRevisionNo().equals(revisionNo)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_CHANGED_SINCE_VIEW);
        }

        LocalDateTime now = LocalDateTime.now();
        GroupBuy groupBuy = locked.groupBuy();
        post.unhide(revisionNo, operatorId, now);
        GroupBuyPostExposure.sync(groupBuy, post, now);
        historyRecorder.record(groupBuy, GroupBuyEventType.POST_UNHIDDEN, admin, null, post.getPostId(), now);
        notifier.notifyBothParties(groupBuy, "POST_UNHIDDEN");
        return result(groupBuy, post, null, null);
    }

    // ── 6-2 ~ 6-5 직권 중단 ──────────────────────────────────────────────────

    /**
     * 사전 통지(M6) — IN_PROGRESS → SUSPENSION_SCHEDULED. 판매는 멈추지 않는다 — 게시물 투영·상품 resync가 없다.
     * <b>검토 중 요청이 있으면 막는다</b> — 통지 후에는 요청 승인이 출발할 상태가 없어 요청이 판정 불가로 박힌다(6-2).
     * 대기 중인 연장은 그대로 둔다.
     */
    public AdminGroupBuyDto.ActionResponse noticeSuspension(Long groupBuyId, Long operatorId,
                                                            AdminGroupBuyDto.SuspensionNoticeRequest request) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (groupBuy.getStatus() != GroupBuyStatus.IN_PROGRESS) {
            throw new BusinessException(facts.activeNotice().isPresent()
                    ? ErrorCode.GROUP_BUY_SUSPENSION_ALREADY_NOTICED : ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
        if (facts.pendingChangeRequest().isPresent()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_REQUEST_DECIDE_FIRST);
        }
        String body = requireText(request.noticeBody(), ErrorCode.GROUP_BUY_DECISION_REASON_REQUIRED);
        LocalDateTime now = LocalDateTime.now();
        if (!schedule.isValid(groupBuy, request.appealDeadlineAt(), request.executeScheduledAt(), now)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_SCHEDULE_INVALID);
        }

        if (groupBuyRepository.transition(groupBuyId, EnumSet.of(GroupBuyStatus.IN_PROGRESS),
                GroupBuyStatus.SUSPENSION_SCHEDULED) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
        groupBuy.applyStatus(GroupBuyStatus.SUSPENSION_SCHEDULED);

        GroupBuySales sales = salesReader.readSales(groupBuyId).orElse(null);
        GroupBuyAdminSuspension notice;
        try {
            notice = adminSuspensionRepository.saveAndFlush(GroupBuyAdminSuspension.notice(groupBuy, request.clause(),
                    body, request.executeScheduledAt(), request.appealDeadlineAt(), latestRevisionNo(facts.post()),
                    sales == null ? null : sales.orderCount(), sales == null ? null : sales.amount(),
                    operatorId, now));
        } catch (DataIntegrityViolationException e) {
            // active_group_buy_id UNIQUE — 진행 중 통지 1건.
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_ALREADY_NOTICED);
        }
        historyRecorder.record(groupBuy, GroupBuyEventType.SUSPENSION_NOTICED, admin,
                "%s · 집행 예정 %s · 소명 기한 %s".formatted(request.clause().getLabel(),
                        request.executeScheduledAt().format(NOTICE_EXECUTE_FORMAT),
                        request.appealDeadlineAt().format(NOTICE_APPEAL_FORMAT)),
                notice.getId(), now);
        notifier.notifyBothParties(groupBuy, "SUSPENSION_NOTICED");
        return result(groupBuy, facts.post(), null,
                request.clause() == SuspensionReasonClause.ART17_3_BREACH ? CLAUSE_CAUTION_POST_ALTERATION : null);
    }

    /**
     * 집행 — 소명 기한 경과 ∧ 집행 예정 일시 도달(6-3 ②). 자동 집행하지 않는다. 집행 사유를 필수로 받는다 — 소명을 낸
     * 브랜드는 그 소명이 왜 받아들여지지 않았는지를 들어야 한다(6-3 ①).
     */
    public AdminGroupBuyDto.ActionResponse executeSuspension(Long groupBuyId, Long operatorId,
                                                             AdminGroupBuyDto.SuspensionExecuteRequest request) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyActor admin = GroupBuyActor.admin(operatorId, access.requireSalesStoppingPermission(operatorId));
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        GroupBuyAdminSuspension notice = facts.activeNotice()
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_NOT_NOTICED));
        LocalDateTime now = LocalDateTime.now();
        if (!permissionPolicy.canExecuteSuspension(facts, now)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_EXECUTION_NOT_DUE);
        }
        String note = requireText(request.executionNote(), ErrorCode.GROUP_BUY_DECISION_REASON_REQUIRED);

        if (adminSuspensionRepository.execute(notice.getId(), note, operatorId, now) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_NOT_NOTICED);
        }
        notice.applyExecuted(note, operatorId, now);
        terminator.terminate(groupBuy,
                Termination.suspendedByNotice(notice.getId(), admin, notice.basisLabel(), now), now);
        return result(groupBuy, facts.post(), null, null);
    }

    /**
     * 철회(M7) — 공구는 원래 일정대로 IN_PROGRESS. {@code end_at} 불변. 소명 기한 전에도 받는다. 재통지는 새 행이고
     * 3영업일이 다시 흐른다.
     */
    public AdminGroupBuyDto.ActionResponse withdrawSuspension(Long groupBuyId, Long operatorId,
                                                              AdminGroupBuyDto.SuspensionWithdrawRequest request) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        GroupBuyAdminSuspension notice = facts.activeNotice()
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_NOT_NOTICED));
        if (!permissionPolicy.canWithdrawSuspension(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
        String detail = requireText(request.detail(), ErrorCode.GROUP_BUY_DECISION_REASON_REQUIRED);

        LocalDateTime now = LocalDateTime.now();
        if (adminSuspensionRepository.withdraw(notice.getId(), request.reasonCode(), detail, operatorId, now) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_NOT_NOTICED);
        }
        notice.applyWithdrawn(request.reasonCode(), detail, operatorId, now);
        if (groupBuyRepository.transition(groupBuyId, EnumSet.of(GroupBuyStatus.SUSPENSION_SCHEDULED),
                GroupBuyStatus.IN_PROGRESS) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
        groupBuy.applyStatus(GroupBuyStatus.IN_PROGRESS);
        historyRecorder.record(groupBuy, GroupBuyEventType.SUSPENSION_WITHDRAWN, admin,
                request.reasonCode().getLabel(), notice.getId(), now);
        notifier.notifyBothParties(groupBuy, "SUSPENSION_WITHDRAWN");
        return result(groupBuy, facts.post(), null, null);
    }

    /**
     * 긴급 직권 중단(M4 · 제17조③) — EXECUTED로 바로 생긴다. 진행 중 통지는 SUPERSEDED가 된다. 「긴급」은 이력 detail에
     * 서버가 박는다(§32-3). 재개 불가라 소명 창이 없고, 사후 이의는 이슈 스레드가 받는다(6-5).
     */
    public AdminGroupBuyDto.ActionResponse emergencySuspend(Long groupBuyId, Long operatorId,
                                                            AdminGroupBuyDto.EmergencySuspensionRequest request) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyActor admin = GroupBuyActor.admin(operatorId, access.requireSalesStoppingPermission(operatorId));
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (!permissionPolicy.canEmergencySuspend(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
        String body = requireText(request.body(), ErrorCode.GROUP_BUY_DECISION_REASON_REQUIRED);

        LocalDateTime now = LocalDateTime.now();
        GroupBuySales sales = salesReader.readSales(groupBuyId).orElse(null);
        GroupBuyAdminSuspension emergency = adminSuspensionRepository.saveAndFlush(GroupBuyAdminSuspension.emergency(
                groupBuy, request.emergencyReason(), body, latestRevisionNo(facts.post()),
                sales == null ? null : sales.orderCount(), sales == null ? null : sales.amount(), operatorId, now));
        terminator.terminate(groupBuy, Termination.suspendedEmergency(emergency.getId(), admin,
                "긴급 · " + request.emergencyReason().getLabel(), now), now);
        return result(groupBuy, facts.post(), null, null);
    }

    // ── 7 중단 · 조기 마감 요청 판정 ──────────────────────────────────────────

    /**
     * 요청 승인 — <b>경로의 요청 id를 판정한다</b>. 「지금 걸린 요청」을 판정하게 하면 읽지 않은 요청을 승인할 수 있다(7-1).
     * 사유는 요청자와 상대 모두에게 간다 — 상대에게는 처음 듣는 결론이다.
     */
    public AdminGroupBuyDto.ActionResponse approveRequest(Long groupBuyId, Long requestId, Long operatorId,
                                                          AdminGroupBuyDto.ChangeRequestDecisionRequest request) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        GroupBuyChangeRequest changeRequest = requirePendingRequest(facts, requestId);
        GroupBuyActor admin = changeRequest.getRequestType() == ChangeRequestType.SUSPEND
                ? GroupBuyActor.admin(operatorId, access.requireSalesStoppingPermission(operatorId))
                : admin(operatorId);
        String reason = requireText(request.decisionReason(), ErrorCode.GROUP_BUY_DECISION_REASON_REQUIRED);
        if (!permissionPolicy.isDecidable(groupBuy, changeRequest)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        decide(groupBuyId, changeRequest, ChangeRequestStatus.APPROVED, reason, operatorId, now);
        Termination termination = changeRequest.getRequestType() == ChangeRequestType.SUSPEND
                ? Termination.suspendedByRequest(changeRequest.getId(), admin, changeRequest.reasonLabel(), now)
                : Termination.earlyClosed(changeRequest.getId(), admin, changeRequest.reasonLabel(), now);
        terminator.terminate(groupBuy, termination, now);
        return result(groupBuy, facts.post(), null, null);
    }

    /**
     * 요청 반려 — 요청만 기각되고 공구는 일정대로. 통지는 <b>요청자 + 상대 모두</b> — 시작을 알렸으면 끝도 알려야 한다.
     * 재요청 조건은 사유 문장 안에 있다 — 필드로 받으면 서버가 판정해야 하는 것처럼 보인다(7-2).
     */
    public AdminGroupBuyDto.ActionResponse rejectRequest(Long groupBuyId, Long requestId, Long operatorId,
                                                         AdminGroupBuyDto.ChangeRequestDecisionRequest request) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        GroupBuyChangeRequest changeRequest = requirePendingRequest(facts, requestId);
        String reason = requireText(request.decisionReason(), ErrorCode.GROUP_BUY_DECISION_REASON_REQUIRED);

        LocalDateTime now = LocalDateTime.now();
        decide(groupBuyId, changeRequest, ChangeRequestStatus.REJECTED, reason, operatorId, now);
        GroupBuyEventType event = changeRequest.getRequestType() == ChangeRequestType.SUSPEND
                ? GroupBuyEventType.SUSPENSION_REJECTED : GroupBuyEventType.EARLY_CLOSE_REJECTED;
        historyRecorder.record(groupBuy, event, admin, reason, changeRequest.getId(), now);
        notifier.notifyBothParties(groupBuy, event.name());
        return result(groupBuy, facts.post(), null, null);
    }

    private GroupBuyChangeRequest requirePendingRequest(GroupBuyFacts facts, Long requestId) {
        return facts.changeRequests().stream()
                .filter(request -> request.getId().equals(requestId))
                .filter(GroupBuyChangeRequest::isPending)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_CHANGE_REQUEST_NOT_PENDING));
    }

    private void decide(Long groupBuyId, GroupBuyChangeRequest request, ChangeRequestStatus result, String reason,
                        Long operatorId, LocalDateTime now) {
        // 운영자 A 승인 · B 반려 · 스케줄러 만료가 겹치면 하나만 1행이다.
        if (changeRequestRepository.decide(groupBuyId, request.getId(), result, reason, operatorId, now) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_CHANGE_REQUEST_NOT_PENDING);
        }
        request.applyDecision(result, reason, operatorId, now);
    }

    // ── 8 이슈 · 정산 ────────────────────────────────────────────────────────

    /**
     * 이슈 스레드 개설 — 파트너와 같은 서비스를 {@code opener_type = ADMIN}으로 부른다. 허용 상태가 파트너보다 넓다 —
     * 종료 · 정산완료 · 중단(8-3). 정산 보류를 걸지 않는다.
     */
    public AdminGroupBuyDto.IssueOpenResponse openIssue(Long groupBuyId, Long operatorId,
                                                        AdminGroupBuyDto.IssueOpenRequest request) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyActor admin = admin(operatorId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (facts.openIssue() != null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ISSUE_ALREADY_OPEN);
        }
        if (!permissionPolicy.canOpenIssue(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
        String content = requireText(request.content(), ErrorCode.GROUP_BUY_DECISION_REASON_REQUIRED);
        GroupBuyIssue issue = issueService.open(groupBuy, admin, operatorId, request.issueType(), content,
                LocalDateTime.now());
        return new AdminGroupBuyDto.IssueOpenResponse(issue.getId(), issue.getThreadId());
    }

    /**
     * 정산 확인 — 정산 포트에 위임한다. 공구 테이블에 쓰지 않고 공구 상태도 바꾸지 않는다(8-2). 정산 모듈이 없으면
     * 판정이 항상 거짓이라 409다 — 착수 게이트.
     */
    public void confirmSettlement(Long groupBuyId, Long operatorId) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        admin(operatorId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (!permissionPolicy.canConfirmSettlement(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SETTLEMENT_NOT_READY);
        }
        settlementGateway.confirm(groupBuyId, operatorId);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    /** 공구 → 게시물 순으로 잠근다(31 설계 2-7). 게시물 판정은 모두 이 경로다. */
    private Locked lockWithPost(Long groupBuyId) {
        GroupBuy groupBuy = access.lock(groupBuyId);
        GroupBuyPost post = postRepository.findByGroupBuyIdForUpdate(groupBuyId).orElse(null);
        return new Locked(groupBuy, post, factsLoader.load(groupBuy));
    }

    private GroupBuyActor admin(Long operatorId) {
        return GroupBuyActor.admin(operatorId, access.operatorName(operatorId));
    }

    /** 게시물 최신 판본 — 없으면 null. 호출자가 게시물 행을 잠근 뒤에 읽는다. */
    private Integer latestRevisionNo(GroupBuyPost post) {
        if (post == null) {
            return null;
        }
        int last = revisionRepository.findLastRevisionNo(post.getPostId());
        return last == 0 ? null : last;
    }

    private static String requireText(String value, ErrorCode whenBlank) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(whenBlank);
        }
        return value.trim();
    }

    private static AdminGroupBuyDto.ActionResponse result(GroupBuy groupBuy, GroupBuyPost post,
                                                          Boolean revisionAdvanced, String clauseCaution) {
        GroupBuyStatus status = groupBuy.getStatus();
        GroupBuyPostStatus postStatus = GroupBuyPostStatus.of(post, groupBuy);
        return new AdminGroupBuyDto.ActionResponse(groupBuy.getId(), status, status.getLabel(),
                postStatus, postStatus.getLabel(),
                status == GroupBuyStatus.READY ? groupBuy.getStartAt() : null,
                revisionAdvanced, clauseCaution);
    }

    private record Locked(GroupBuy groupBuy, GroupBuyPost post, GroupBuyFacts facts) {
    }
}

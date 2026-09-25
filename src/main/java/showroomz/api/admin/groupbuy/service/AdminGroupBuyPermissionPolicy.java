package showroomz.api.admin.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse.NoticeUnavailableReason;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse.Permissions;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse.SettlementBlocker;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway.SettlementStage;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 어드민 버튼 판정(32 설계 4-10). <b>실행 API가 같은 메서드를 다시 호출해</b> 409로 막는다 — 버튼 판정과 집행 판정이
 * 한 메서드라 어긋날 수 없다. 시각이 여는 버튼은 서버 {@code now}로 판정한다(0-6).
 *
 * <p>정산 차단 사유({@link #settlementBlockers})도 여기서 판정한다 — 상세 카드(「왜 아직 정산이 안 되나」)와
 * 정산 확인 버튼이 같은 판정을 봐야 한다.
 */
@Component
@RequiredArgsConstructor
public class AdminGroupBuyPermissionPolicy {

    private final AdminSuspensionSchedule schedule;
    private final GroupBuySalesReader salesReader;
    private final GroupBuySettlementGateway settlementGateway;

    public Permissions evaluate(GroupBuyFacts facts, LocalDateTime now) {
        NoticeUnavailableReason noticeBlock = noticeUnavailableReason(facts, now);
        return new Permissions(
                canDecideOpen(facts),
                canDecideOpen(facts),
                canHidePost(facts),
                canUnhidePost(facts),
                noticeBlock == null,
                noticeBlock,
                canEmergencySuspend(facts),
                canExecuteSuspension(facts, now),
                canWithdrawSuspension(facts),
                canDecideRequest(facts),
                canDecideRequest(facts),
                canOpenIssue(facts),
                canConfirmSettlement(facts));
    }

    /** 오픈 승인·반려 — PREPARING ∧ 게시물 PENDING. */
    public boolean canDecideOpen(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.PREPARING
                && facts.post() != null && facts.post().isPendingReview();
    }

    /**
     * 숨김 — 승인 ∧ 숨김 아님 ∧ READY · IN_PROGRESS · SUSPENSION_SCHEDULED. <b>READY에서도 받는다</b> — 승인 후 시작 전에
     * 문제를 발견하면 숨긴 채로 열리게 할 수 있어야 한다(32 설계 5-3).
     */
    public boolean canHidePost(GroupBuyFacts facts) {
        GroupBuyPost post = facts.post();
        GroupBuyStatus status = facts.groupBuy().getStatus();
        return post != null && post.isApproved() && !post.isHidden()
                && (status == GroupBuyStatus.READY || status.isSelling());
    }

    public boolean canUnhidePost(GroupBuyFacts facts) {
        return facts.isPostHidden() && !facts.groupBuy().getStatus().isTerminal();
    }

    /** 통지 불가 사유 — null이면 통지할 수 있다. 사유를 보여줘야 운영자가 긴급 경로로 새지 않는다. */
    public NoticeUnavailableReason noticeUnavailableReason(GroupBuyFacts facts, LocalDateTime now) {
        if (facts.groupBuy().getStatus() != GroupBuyStatus.IN_PROGRESS) {
            return NoticeUnavailableReason.STATUS;
        }
        if (facts.pendingChangeRequest().isPresent()) {
            return NoticeUnavailableReason.REQUEST_PENDING;
        }
        if (!schedule.hasWindow(facts.groupBuy(), now)) {
            return NoticeUnavailableReason.NO_WINDOW_BEFORE_END;
        }
        return null;
    }

    /** 긴급 — 진행중 · 중단 예정. 준비완료는 받지 않는다(판매가 없어 「피해 급증」이 성립하지 않는다 · 6-5). */
    public boolean canEmergencySuspend(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus().isSelling();
    }

    /** 집행 — 소명 기한 경과 ∧ <b>집행 예정 일시 도달</b>. 통지한 일시보다 먼저 판매를 끊지 않는다(6-3 ②). */
    public boolean canExecuteSuspension(GroupBuyFacts facts, LocalDateTime now) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.SUSPENSION_SCHEDULED
                && facts.activeNotice().map(notice -> notice.isExecutionDue(now)).orElse(false);
    }

    /** 철회 — 소명 기한 전에도 받는다. 운영자가 스스로 시정을 확인했거나 오판을 깨달았을 수 있다(6-4). */
    public boolean canWithdrawSuspension(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.SUSPENSION_SCHEDULED && facts.activeNotice().isPresent();
    }

    public boolean canDecideRequest(GroupBuyFacts facts) {
        return facts.pendingChangeRequest().map(request -> isDecidable(facts.groupBuy(), request)).orElse(false);
    }

    /** 요청이 승인되면 출발할 수 있는 상태인가 — 중단은 READY·IN_PROGRESS, 조기 마감은 IN_PROGRESS(30 설계 3-1). */
    public boolean isDecidable(GroupBuy groupBuy, GroupBuyChangeRequest request) {
        GroupBuyStatus status = groupBuy.getStatus();
        return request.isPending() && (request.getRequestType() == ChangeRequestType.SUSPEND
                ? status == GroupBuyStatus.READY || status == GroupBuyStatus.IN_PROGRESS
                : status == GroupBuyStatus.IN_PROGRESS);
    }

    /** 이슈 — 종료 · 정산완료 · 중단 ∧ 열린 이슈 없음. 중단은 긴급 건의 사후 이의 창구다(6-5). */
    public boolean canOpenIssue(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus().isTerminal() && facts.openIssue() == null;
    }

    /** 정산 확인 — ENDED ∧ 차단 사유 없음 ∧ 정산 포트 단계 WAITING. 포트가 비면 false다(8-2 착수 게이트). */
    public boolean canConfirmSettlement(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.ENDED
                && settlementBlockers(facts).isEmpty()
                && settlementGateway.readStage(facts.groupBuy().getId())
                        .map(stage -> stage == SettlementStage.WAITING).orElse(false);
    }

    /**
     * 「왜 아직 정산이 안 되나」(32 설계 4-8 ②) — 종료(ENDED)에서만 판정한다. 코드 이름이 선행 조건 미충족과 보류를
     * 가른다. 판매 포트가 비면 {@code CLOSURE_UNKNOWN} — 미종결 0으로 읽으면 정산이 거짓으로 열린다.
     */
    public List<SettlementBlocker> settlementBlockers(GroupBuyFacts facts) {
        GroupBuy groupBuy = facts.groupBuy();
        if (groupBuy.getStatus() != GroupBuyStatus.ENDED) {
            return List.of();
        }
        List<SettlementBlocker> blockers = new ArrayList<>();
        Optional<GroupBuySalesReader.GroupBuyOrderClosure> closure = salesReader.readClosure(groupBuy.getId());
        if (closure.isEmpty()) {
            blockers.add(SettlementBlocker.CLOSURE_UNKNOWN);
        } else if (closure.get().unclosedCount() > 0) {
            blockers.add(SettlementBlocker.UNCLOSED_ORDERS);
        }
        if (facts.fulfillmentChecks().size() < 2) {
            blockers.add(SettlementBlocker.FULFILLMENT_PENDING);
        }
        if (GroupBuyCommandService.isSettlementOnHold(groupBuy, facts.fulfillmentChecks())) {
            blockers.add(SettlementBlocker.FULFILLMENT_DISPUTE);
        }
        return blockers;
    }
}

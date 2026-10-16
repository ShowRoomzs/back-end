package showroomz.api.admin.creator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.creator.dto.AdminCreatorDto.*;
import showroomz.api.admin.creator.repository.AdminCreatorQueryRepository;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.service.CreatorAdminHistoryRecorder;
import showroomz.domain.member.creator.service.CreatorMemberNotifier;
import showroomz.domain.member.creator.service.CreatorWithdrawalEligibility;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.service.MessageSanctionChannelGateway;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.CreatorSettlementSanctionHook;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 인플루언서 조치 4종(§51 · 53 설계서 3-9 · 3-10 · 4절) — 정지 · 해제 · 탈퇴 승인 · 반려. 전부 <b>행 잠금 → 상태 재확인 → 변경 + 이력 +
 * 카드/알림 훅</b>이다(브랜드 52 설계서 0-13 과 같은 관례). 응답은 갱신된 상세 전체다.
 *
 * <p>정지는 「새로 벌이는 일만 차단」이고 서버 집행은 {@code CreatorSanctionGuard}가 한다. 리워드 지급 정지는 사유가 정한다 — 운영자
 * 스위치가 아니다(§51-1). 탈퇴는 신청 → 승인이고 승인이 파기를 집행한다(0-10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminCreatorCommandService {

    private final CreatorRepository creators;
    private final CreatorSanctionRepository sanctions;
    private final CreatorWithdrawalRequestRepository withdrawalRequests;
    private final CreatorWithdrawalEligibility eligibility;
    private final CreatorAdminHistoryRecorder history;
    private final CreatorMemberNotifier notifier;
    private final CreatorSettlementSanctionHook settlementHook;
    private final OperatorChannelService operatorChannels;
    private final MessageSanctionChannelGateway sanctionCards;
    private final MessageThreadRepository threads;
    private final AdminCreatorQueryRepository queries;
    private final AdminCreatorQueryService queryService;
    private final AdminOperatorResolver operators;

    // ------------------------------------------------------------------ 정지 · 해제

    public Detail suspend(Long creatorId, Long operatorId, SuspendRequest request) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        Creator creator = lock(creatorId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        if (creator.getStatus() == CreatorStatus.SUSPENDED || sanctions.existsByCreatorIdAndReleasedAtIsNull(creatorId)) {
            throw new BusinessException(ErrorCode.CREATOR_ALREADY_SUSPENDED);
        }
        if (creator.getStatus() != CreatorStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CREATOR_STATE_CHANGED);
        }
        String detail = trimToNull(request.reasonDetail());
        if (request.reason().isDetailRequired() && detail == null) {
            throw new BusinessException(ErrorCode.CREATOR_SUSPEND_DETAIL_REQUIRED);
        }

        CreatorSanction sanction = sanctions.save(
                CreatorSanction.suspend(creatorId, request.reason(), detail, operatorId, operatorName, now));
        creator.markSuspended();
        if (sanction.isPayoutHeld()) {
            settlementHook.holdCreatorPayouts(creatorId, now);
        }
        history.byOperator(creatorId, CreatorAdminEventType.SUSPENDED, operatorId, operatorName, sanction.reasonText(),
                CreatorAdminHistoryRecorder.REF_SANCTION, sanction.getId(), now);
        MessageThread channel = operatorChannels.requireCreatorChannel(creator);
        sanction.recordSuspendCard(sanctionCards.postSuspendedCard(channel, MessageSanctionChannelGateway.SanctionKind.CREATOR,
                sanction.getId(), sanction.reasonText(), now, operatorId));
        String reasonLabel = request.reason().getLabel();
        AfterCommit.run(() -> notifier.suspended(creatorId, reasonLabel));
        log.info("인플루언서 정지 - creatorId: {}, reason: {}, payoutHeld: {}", creatorId, request.reason(), sanction.isPayoutHeld());
        return queryService.assemble(creator, operatorId, now);
    }

    public Detail release(Long creatorId, Long operatorId, ReleaseRequest request) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        Creator creator = lock(creatorId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        if (creator.getStatus() != CreatorStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.CREATOR_NOT_SUSPENDED);
        }
        SanctionReleaseReason reason = request == null || request.reason() == null
                ? SanctionReleaseReason.RESOLVED : request.reason();
        if (reason == SanctionReleaseReason.WITHDRAWN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴 종결은 시스템만 기록합니다.");
        }
        String note = request == null ? null : trimToNull(request.note());

        // 정지 행이 없는 정지(V193 백필 — 소비자 계정 정지를 이어받은 행)도 해제할 수 있어야 한다. 사유 · 카드가 없을 뿐이다.
        CreatorSanction sanction = sanctions.findByCreatorIdAndReleasedAtIsNull(creatorId).orElse(null);
        creator.markActive();
        String detail = note == null ? reason.getLabel() : reason.getLabel() + " · " + note;
        if (sanction != null) {
            sanction.release(reason, note, operatorId, operatorName, now);
            if (sanction.isPayoutHeld()) {
                settlementHook.releaseCreatorPayouts(creatorId, now.toLocalDate(), now);
            }
            MessageThread channel = operatorChannels.requireCreatorChannel(creator);
            sanction.recordReleaseCard(sanctionCards.postReleasedCard(channel,
                    MessageSanctionChannelGateway.SanctionKind.CREATOR, sanction.getId(), detail, now, operatorId));
        }
        history.byOperator(creatorId, CreatorAdminEventType.SUSPENSION_RELEASED, operatorId, operatorName, detail,
                CreatorAdminHistoryRecorder.REF_SANCTION, sanction == null ? null : sanction.getId(), now);
        AfterCommit.run(() -> notifier.released(creatorId));
        log.info("인플루언서 정지 해제 - creatorId: {}, reason: {}", creatorId, reason);
        return queryService.assemble(creator, operatorId, now);
    }

    // ------------------------------------------------------------------ 탈퇴 승인 · 반려

    /**
     * 탈퇴 승인(53 설계서 4-4) — 순서가 곧 규칙이다: 잠금 → 신청 상태 → <b>조건 재검증</b>(미충족이면 전체 롤백 · 409) → 신청 승인 →
     * 열린 정지 종결 → 연결 일괄 종료 → 즉시 파기 → 스튜디오 자격 해지(소비자 계정 유지) → 이력.
     */
    public Detail approveWithdrawal(Long creatorId, Long requestId, Long operatorId) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        Creator creator = lock(creatorId);
        CreatorWithdrawalRequest request = loadRequest(creatorId, requestId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(creatorId);
        if (!result.eligible()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_CONDITION_UNMET,
                    "탈퇴 조건이 충족되지 않았습니다 — " + result.summary() + ". 반려로 처리해 주세요.");
        }
        request.approve(operatorId, operatorName,
                (int) result.count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS),
                (int) result.count(CreatorWithdrawalCondition.UNFINISHED_CONTRACTS),
                (int) result.count(CreatorWithdrawalCondition.UNSETTLED), now);

        sanctions.findByCreatorIdAndReleasedAtIsNull(creatorId).ifPresent(sanction -> {
            sanction.release(SanctionReleaseReason.WITHDRAWN, null, null, null, now);
            history.bySystem(creatorId, CreatorAdminEventType.SUSPENSION_RELEASED, SanctionReleaseReason.WITHDRAWN.getLabel(),
                    CreatorAdminHistoryRecorder.REF_SANCTION, sanction.getId(), now);
        });

        int ended = 0;
        for (Connection connection : queries.findOpenPairConnections(creatorId)) {
            connection.markDisconnected();
            threads.findByConnectionAndKind(connection, ThreadKind.CONNECTION).ifPresent(MessageThread::dormant);
            ended++;
        }
        if (ended > 0) {
            history.bySystem(creatorId, CreatorAdminEventType.CONNECTIONS_ENDED, "탈퇴 승인 · 연결 " + ended + "건 일괄 종료",
                    null, null, now);
        }

        String businessEmail = creator.getBusinessEmail();
        creator.purgeOnWithdrawal(operatorId, now);
        // 스튜디오 자격만 해지 — 소비자 계정(users.status)은 그대로다(§51-5). 쇼룸 노출 · 스튜디오 로그인이 이 값과 creator.status 를 본다.
        if (creator.getUser().getRoleType() == RoleType.CREATOR) {
            creator.getUser().updateRoleType(RoleType.USER);
        }
        history.byOperator(creatorId, CreatorAdminEventType.WITHDRAWAL_APPROVED, operatorId, operatorName,
                request.getReasonText(), CreatorAdminHistoryRecorder.REF_WITHDRAWAL_REQUEST, requestId, now);
        AfterCommit.run(() -> notifier.withdrawalApproved(creatorId, businessEmail));
        log.info("인플루언서 탈퇴 승인 - creatorId: {}, requestId: {}, connectionsEnded: {}", creatorId, requestId, ended);
        return queryService.assemble(creator, operatorId, now);
    }

    /** 탈퇴 반려(M3) — 신청만 종결한다. 회원 상태는 원래 활성(또는 정지) 그대로다(§51-0 · M3 「활성으로 되돌아가고」는 D-34 오기). */
    public Detail rejectWithdrawal(Long creatorId, Long requestId, Long operatorId, RejectWithdrawalRequest body) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        Creator creator = lock(creatorId);
        CreatorWithdrawalRequest request = loadRequest(creatorId, requestId);
        String note = trimToNull(body.note());
        if (body.reason().isDetailRequired() && note == null) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_REJECT_DETAIL_REQUIRED);
        }
        request.reject(body.reason(), note, operatorId, operatorName, now);
        String detail = note == null ? body.reason().getLabel() : body.reason().getLabel() + " · " + note;
        history.byOperator(creatorId, CreatorAdminEventType.WITHDRAWAL_REJECTED, operatorId, operatorName, detail,
                CreatorAdminHistoryRecorder.REF_WITHDRAWAL_REQUEST, requestId, now);
        String reasonLabel = body.reason().getLabel();
        AfterCommit.run(() -> notifier.withdrawalRejected(creatorId, reasonLabel));
        return queryService.assemble(creator, operatorId, now);
    }

    // ------------------------------------------------------------------ 공통

    private Creator lock(Long creatorId) {
        return creators.findForUpdate(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    private CreatorWithdrawalRequest loadRequest(Long creatorId, Long requestId) {
        CreatorWithdrawalRequest request = withdrawalRequests.findForUpdate(requestId)
                .filter(r -> r.getCreatorId().equals(creatorId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_FOUND));
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_PENDING);
        }
        return request;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

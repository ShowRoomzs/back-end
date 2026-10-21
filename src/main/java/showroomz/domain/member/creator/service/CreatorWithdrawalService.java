package showroomz.domain.member.creator.service;

import showroomz.domain.member.creator.type.CreatorBlockedAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.member.common.service.MemberNotifier;
import showroomz.domain.member.common.service.MemberSanctionRules;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorMemberQueryRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.domain.member.creator.type.CreatorWithdrawalRejectReason;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;

/**
 * 인플루언서 탈퇴 — 신청 · 승인제의 <b>도메인 서비스</b>(§51-5 · 53 설계서 0-3 · 4-4). 신청 · 취소 · 반려 확인은 스튜디오(50 설계서 4절)가,
 * 승인 · 반려는 어드민이 부른다 — 조건 판정 · 열린 신청 1건 · 이력 기록이 같은 규칙에 묶이게 여기 둔다. 브랜드
 * {@code BrandWithdrawalService}와 같은 골격이다(정합 계획서 R1-6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorWithdrawalService {

    /** 승인 기한 안내 — 스튜디오 「영업일 5일 이내」(§51-5 · 무응답 자동 승인은 없다 · §55 B-7). */
    public static final int APPROVAL_DUE_BUSINESS_DAYS = 5;
    private static final int REASON_MAX = 500;

    private final CreatorRepository creators;
    private final CreatorWithdrawalRequestRepository requests;
    private final CreatorWithdrawalEligibility eligibility;
    private final CreatorAdminHistoryRecorder history;
    private final BusinessCalendar businessCalendar;
    private final CreatorSanctionService sanctionService;
    private final CreatorMemberQueryRepository queries;
    private final MessageThreadRepository threads;
    private final MemberNotifier notifier;
    private final CreatorSanctionGuard sanctionGuard;

    @Transactional
    public CreatorWithdrawalRequest request(Long creatorId, String reasonText, LocalDateTime now) {
        Creator creator = creators.findForUpdate(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        // 정지 중 탈퇴는 제재를 피하는 출구가 된다(ui-studio-13 S7 · 1017 설계서 8-2). 브랜드는 [근거 대기]라 지금처럼 허용한다.
        sanctionGuard.requireNotSuspended(creator, CreatorBlockedAction.WITHDRAWAL_REQUEST);
        String reason = reasonText == null ? "" : reasonText.strip();
        if (reason.isEmpty()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_REASON_REQUIRED);
        }
        if (reason.length() > REASON_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴 사유는 500자 이내로 입력해 주세요.");
        }
        if (requests.existsByCreatorIdAndStatus(creatorId, MemberWithdrawalStatus.PENDING)) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_ALREADY_PENDING);
        }
        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(creatorId);
        result.requireEligible(null);
        // 반려 안내를 닫지 않고 다시 신청했다 — 새 신청 자체가 반려를 읽었다는 뜻이다(50 설계서 4-3).
        requests.findFirstByCreatorIdOrderByRequestedAtDescIdDesc(creatorId)
                .filter(CreatorWithdrawalRequest::isUnacknowledgedRejection)
                .ifPresent(rejected -> rejected.acknowledgeResult(now));
        CreatorWithdrawalRequest saved = requests.save(CreatorWithdrawalRequest.request(creatorId, reason, now,
                businessCalendar.addBusinessDays(now.toLocalDate(), APPROVAL_DUE_BUSINESS_DAYS)));
        history.byCreator(creatorId, CreatorAdminEventType.WITHDRAWAL_REQUESTED, reason,
                CreatorAdminHistoryRecorder.REF_WITHDRAWAL_REQUEST, saved.getId(), now);
        return saved;
    }

    /** 운영자 처리 전까지 인플루언서가 직접 취소한다 — 대기 중 제약도 함께 풀린다(열린 신청 행의 유무로 판정하므로). */
    @Transactional
    public void cancel(Long creatorId, Long requestId, LocalDateTime now) {
        CreatorWithdrawalRequest request = lockPending(creatorId, requestId);
        request.cancel(now);
        history.byCreator(creatorId, CreatorAdminEventType.WITHDRAWAL_CANCELED, null,
                CreatorAdminHistoryRecorder.REF_WITHDRAWAL_REQUEST, requestId, now);
    }

    /** 스튜디오 S8 반려 안내 [확인](50 설계서 4-5) — 표시 플래그만. 이미 확인했으면 그대로. */
    @Transactional
    public void acknowledgeRejection(Long creatorId, Long requestId, LocalDateTime now) {
        CreatorWithdrawalRequest request = requests.findForUpdate(requestId)
                .filter(r -> r.getCreatorId().equals(creatorId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_FOUND));
        if (!request.isRejected()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_REJECTED);
        }
        request.acknowledgeResult(now);
    }

    /**
     * 승인(어드민 M2 · 53 설계서 4-4) — 순서가 곧 규칙이다: 잠금 → 신청 상태 → <b>조건 재검증</b>(미충족이면 전체 롤백 · 409) → 신청 승인 →
     * 열린 정지 종결 → 연결 일괄 종료 → 즉시 파기 → 스튜디오 자격 해지(소비자 계정 유지) → 이력. 알림 수신처는 파기 전에 읽는다.
     */
    @Transactional
    public Creator approve(Long creatorId, Long requestId, Long operatorId, String operatorName, LocalDateTime now) {
        Creator creator = sanctionService.lockCreator(creatorId);
        CreatorWithdrawalRequest request = lockPending(creatorId, requestId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(creatorId);
        result.requireEligible(". 반려로 처리해 주세요.");
        request.approve(operatorId, operatorName,
                (int) result.count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS),
                (int) result.count(CreatorWithdrawalCondition.UNFINISHED_CONTRACTS),
                (int) result.count(CreatorWithdrawalCondition.UNSETTLED), now);
        sanctionService.closeByWithdrawal(creatorId, now);

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
        AfterCommit.run(() -> notifier.withdrawalApproved(MemberType.CREATOR, creatorId, businessEmail));
        log.info("인플루언서 탈퇴 승인 - creatorId: {}, requestId: {}, connectionsEnded: {}", creatorId, requestId, ended);
        return creator;
    }

    /** 반려(어드민 M3) — 신청만 종결한다. 회원 상태는 원래 활성(또는 정지) 그대로다(§51-0). 인플루언서는 즉시 재신청할 수 있다(새 행). */
    @Transactional
    public Creator reject(Long creatorId, Long requestId, CreatorWithdrawalRejectReason reason, String note,
                          Long operatorId, String operatorName, LocalDateTime now) {
        String normalized = MemberSanctionRules.requireDetail(reason, note,
                ErrorCode.CREATOR_WITHDRAWAL_REJECT_DETAIL_REQUIRED);
        Creator creator = sanctionService.lockCreator(creatorId);
        CreatorWithdrawalRequest request = lockPending(creatorId, requestId);
        request.reject(reason, normalized, operatorId, operatorName, now);
        history.byOperator(creatorId, CreatorAdminEventType.WITHDRAWAL_REJECTED, operatorId, operatorName,
                MemberSanctionRules.reasonText(reason, normalized), CreatorAdminHistoryRecorder.REF_WITHDRAWAL_REQUEST,
                requestId, now);
        String reasonLabel = reason.getLabel();
        AfterCommit.run(() -> notifier.withdrawalRejected(MemberType.CREATOR, creatorId, reasonLabel));
        return creator;
    }

    private CreatorWithdrawalRequest lockPending(Long creatorId, Long requestId) {
        CreatorWithdrawalRequest request = requests.findForUpdate(requestId)
                .filter(r -> r.getCreatorId().equals(creatorId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_FOUND));
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_PENDING);
        }
        return request;
    }
}

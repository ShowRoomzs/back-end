package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.common.service.MemberNotifier;
import showroomz.domain.member.common.service.MemberSanctionRules;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.service.MessageSanctionChannelGateway.SanctionKind;
import showroomz.domain.member.common.service.MemberSanctionHooks;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.service.MessageSanctionChannelGateway;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.PayoutHoldTransitions;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;

/**
 * 인플루언서 계정 정지 · 해제(§51-1 ~ 51-4 · 53 설계서 3-9 · 4절) — 브랜드 {@code BrandSanctionService}와 같은 골격이다(정합 계획서 R1-6).
 * 인플루언서 행을 잠그고 한 트랜잭션에서 정지 행 · 회원 상태 · 리워드 지급 보류 · 이력 · 운영팀 채널 카드를 함께 바꾼다 — 카드도 조치와 같이
 * 롤백된다. 알림만 커밋 뒤다.
 *
 * <p>운영자 이름은 호출자(어드민 서비스)가 확인해 넘긴다 — 도메인이 api 의 운영자 해석기를 부르지 않는다. 리워드 지급 정지는 사유가 정한다
 * (운영자 스위치가 아니다 · §51-1). 서버 집행 가드는 {@link CreatorSanctionGuard}다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CreatorSanctionService {

    private final CreatorRepository creators;
    private final CreatorSanctionRepository sanctions;
    private final CreatorAdminHistoryRecorder history;
    private final PayoutHoldTransitions payoutHolds;
    private final OperatorChannelService operatorChannels;
    private final MessageSanctionChannelGateway sanctionCards;
    private final MemberNotifier notifier;
    private final MemberSanctionHooks hooks;
    private final showroomz.domain.member.common.service.SanctionTermPolicy termPolicy;

    public CreatorSanction suspend(Long creatorId, CreatorSuspensionReason reason, String reasonDetail, Long operatorId,
                                   String operatorName, LocalDateTime now) {
        return suspend(creatorId, reason, reasonDetail, null, operatorId, operatorName, now);
    }

    /** 정지 — 종료일(포함)이 있으면 다음 날 00:00 자동 해제 · 이의 신청 기한 = 통지 + N영업일(1021 기-02). */
    public CreatorSanction suspend(Long creatorId, CreatorSuspensionReason reason, String reasonDetail,
                                   java.time.LocalDate endsOn, Long operatorId, String operatorName, LocalDateTime now) {
        java.time.LocalDate term = termPolicy.requireEndsOn(endsOn, now);
        Creator creator = lockCreator(creatorId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        if (creator.getStatus() == CreatorStatus.SUSPENDED || sanctions.existsByCreatorIdAndReleasedAtIsNull(creatorId)) {
            throw new BusinessException(ErrorCode.CREATOR_ALREADY_SUSPENDED);
        }
        if (creator.getStatus() != CreatorStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CREATOR_STATE_CHANGED);
        }
        String detail = MemberSanctionRules.requireDetail(reason, reasonDetail, ErrorCode.CREATOR_SUSPEND_DETAIL_REQUIRED);

        CreatorSanction sanction = sanctions.save(
                CreatorSanction.suspend(creatorId, reason, detail, operatorId, operatorName, now));
        sanction.applyTerm(term, termPolicy.appealDeadline(now));
        creator.markSuspended();
        if (sanction.isPayoutHeld()) {
            payoutHolds.hold(SettlementPayee.CREATOR, creatorId, PayoutBlockReason.CREATOR_SUSPENDED, now);
        }
        history.byOperator(creatorId, CreatorAdminEventType.SUSPENDED, operatorId, operatorName, sanction.reasonText(),
                CreatorAdminHistoryRecorder.REF_SANCTION, sanction.getId(), now);
        MessageThread channel = operatorChannels.requireCreatorChannel(creator);
        sanction.recordSuspendCard(sanctionCards.postSuspendedCard(channel, SanctionKind.CREATOR, sanction.getId(),
                sanction.reasonText(), now, operatorId, new MessageCardPayload.Sanction(MemberType.CREATOR.name(),
                        reason.name(), reason.getLabel(), detail, sanction.isPayoutHeld(), sanction.getEndsOn(),
                        sanction.getAppealDeadlineAt())));
        hooks.onSuspended(MemberType.CREATOR, creatorId, now);
        String reasonLabel = reason.getLabel();
        AfterCommit.run(() -> notifier.suspended(MemberType.CREATOR, creatorId, reasonLabel));
        log.info("인플루언서 정지 - creatorId: {}, reason: {}, payoutHeld: {}", creatorId, reason, sanction.isPayoutHeld());
        return sanction;
    }

    /**
     * 해제 — 회원 상세에서만(§51-4). 정지 행이 없는 정지(V193 백필 — 소비자 계정 정지를 이어받은 행)도 풀 수 있어야 한다 — 그때는 상태만
     * 되돌리고 지급 · 카드는 건드릴 것이 없다.
     */
    public CreatorSanction release(Long creatorId, SanctionReleaseReason reason, String note, Long operatorId,
                                   String operatorName, LocalDateTime now) {
        return releaseInternal(creatorId, MemberSanctionRules.operatorReleaseReason(reason), note, operatorId,
                operatorName, now);
    }

    /** 정지 기간 종료 자동 해제(1021 기-02) — 시스템. 기간이 없거나 남았거나 이미 풀렸으면 null. */
    public CreatorSanction releaseByTerm(Long creatorId, LocalDateTime now) {
        CreatorSanction open = sanctions.findByCreatorIdAndReleasedAtIsNull(creatorId).orElse(null);
        if (open == null || !open.isTermEnded(KstDates.today(now))) {
            return null;
        }
        CreatorSanction released = releaseInternal(creatorId, SanctionReleaseReason.TERM_ENDED, null, null, null, now);
        if (released != null) {
            released.markAutoReleased();
        }
        return released;
    }

    /** @param operatorId null 이면 시스템 해제 — 이력 주체 SYSTEM · 카드 발신 시스템 운영자. */
    private CreatorSanction releaseInternal(Long creatorId, SanctionReleaseReason resolved, String note, Long operatorId,
                                            String operatorName, LocalDateTime now) {
        Creator creator = lockCreator(creatorId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        if (creator.getStatus() != CreatorStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.CREATOR_NOT_SUSPENDED);
        }
        String normalizedNote = MemberSanctionRules.trimToNull(note);

        CreatorSanction sanction = sanctions.findByCreatorIdAndReleasedAtIsNull(creatorId).orElse(null);
        creator.markActive();
        String text = MemberSanctionRules.releaseText(resolved, normalizedNote);
        if (sanction != null) {
            sanction.release(resolved, normalizedNote, operatorId, operatorName, now);
            if (sanction.isPayoutHeld()) {
                payoutHolds.release(SettlementPayee.CREATOR, creatorId, PayoutBlockReason.CREATOR_SUSPENDED,
                        PayoutHoldTransitions.DueRule.NEXT_BUSINESS_DAY, KstDates.today(now), now);
            }
            MessageThread channel = operatorChannels.requireCreatorChannel(creator);
            sanction.recordReleaseCard(sanctionCards.postReleasedCard(channel, SanctionKind.CREATOR, sanction.getId(),
                    text, now, operatorId == null ? OperatorChannelService.SYSTEM_OPERATOR_ID : operatorId,
                    new MessageCardPayload.Sanction(MemberType.CREATOR.name(), resolved.name(), resolved.getLabel(),
                            normalizedNote, null)));
        }
        hooks.onReleased(MemberType.CREATOR, creatorId, now);
        if (operatorId == null) {
            history.bySystem(creatorId, CreatorAdminEventType.SUSPENSION_RELEASED, text,
                    CreatorAdminHistoryRecorder.REF_SANCTION, sanction == null ? null : sanction.getId(), now);
        } else {
            history.byOperator(creatorId, CreatorAdminEventType.SUSPENSION_RELEASED, operatorId, operatorName, text,
                    CreatorAdminHistoryRecorder.REF_SANCTION, sanction == null ? null : sanction.getId(), now);
        }
        AfterCommit.run(() -> notifier.released(MemberType.CREATOR, creatorId));
        log.info("인플루언서 정지 해제 - creatorId: {}, reason: {}", creatorId, resolved);
        return sanction;
    }

    /**
     * 탈퇴 승인이 열린 정지를 종결한다(53 설계서 4-4 ④) — 호출자 트랜잭션 · 인플루언서 잠금 안. 해제 카드 · 알림은 없다(해지된 계정이다).
     * 지급 정지 중이었다면 탈퇴 조건 ③(미정산)이 이미 막았으므로 풀 지급도 없다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeByWithdrawal(Long creatorId, LocalDateTime now) {
        sanctions.findByCreatorIdAndReleasedAtIsNull(creatorId).ifPresent(sanction -> {
            sanction.release(SanctionReleaseReason.WITHDRAWN, null, null, null, now);
            history.bySystem(creatorId, CreatorAdminEventType.SUSPENSION_RELEASED, SanctionReleaseReason.WITHDRAWN.getLabel(),
                    CreatorAdminHistoryRecorder.REF_SANCTION, sanction.getId(), now);
        });
    }

    /** 행 잠금 — 정지 · 해제 · 탈퇴 승인을 한 줄로 세운다(브랜드의 마켓 잠금과 같은 관례 · 53 설계서 12 #4). */
    public Creator lockCreator(Long creatorId) {
        return creators.findForUpdate(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}

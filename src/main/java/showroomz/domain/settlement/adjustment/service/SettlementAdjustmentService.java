package showroomz.domain.settlement.adjustment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.message.entity.Message;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustmentProposal;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort.AdjustableSettlement;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentThreadPort;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentProposalRepository;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.SettlementNotifier;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * 정산 조정 협의(44 이슈 스레드 설계서 2절) — 요청 · 다른 금액 제안 · 동의 · 반대 + 배치 2종(D-1 통지 · 기한 만료). 파트너 · 스튜디오는
 * 당사자({@link SettlementParty} · id)만 바꿔 <b>같은 메서드</b>를 부른다 — 규칙이 두 벌이 되면 한쪽만 고쳐진다. 운영자는 부르지 않는다
 * (0-7 · 어드민 쓰기 엔드포인트 0개).
 *
 * <p>금액의 정본은 정산이다 — 상한 · 미리보기 · 보류 · 확정은 {@link SettlementAdjustmentPort}가 한다. 진입 시 협의 행을 잠그고, 종결은
 * 조건부 UPDATE 한 문장이 승자다(0-8). 동의 → 정산 확정 → 카드 → 알림이 한 트랜잭션이다 — 정산 쓰기가 실패하면 협의도 종결되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAdjustmentService {

    private static final EnumSet<ProposalStatus> RESPONDABLE = EnumSet.of(ProposalStatus.PENDING, ProposalStatus.REJECTED);

    private final SettlementAdjustmentRepository adjustmentRepository;
    private final SettlementAdjustmentProposalRepository proposalRepository;
    private final SettlementAdjustmentPort settlementPort;
    private final SettlementAdjustmentThreadPort threadPort;
    private final SettlementAdjustmentCards cards;
    private final SettlementNotifier notifier;
    private final SettlementProperties properties;
    private final BusinessCalendar calendar;

    public record RequestResult(Long adjustmentId, Long threadId, Long proposalId, LocalDateTime deadlineAt) {
    }

    // ------------------------------------------------------------------ 2-1 조정 요청

    @Transactional
    public RequestResult request(Long settlementId, SettlementParty party, Long partyId, long amount, String reason,
                                 LocalDateTime now) {
        AdjustableSettlement s = settlementPort.find(settlementId)
                .filter(found -> party == SettlementParty.SELLER ? partyId.equals(found.marketId())
                        : partyId.equals(found.creatorId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (adjustmentRepository.findBySettlementId(settlementId).isPresent()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_ALREADY_EXISTS);
        }
        if (!s.inReviewWindow()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED);
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_REASON_REQUIRED);
        }
        requireReasonLength(trimmed);
        if (amount < 0 || amount > s.maxRewardAmount() || amount == s.rewardAmount()) {
            throw outOfRange(s.maxRewardAmount());
        }

        Long threadId = threadPort.openAdjustmentThread(s.groupBuyId(), s.marketId(), s.creatorId());
        LocalDateTime deadlineAt = deadlineOf(now);
        SettlementAdjustment adjustment;
        try {
            adjustment = adjustmentRepository.saveAndFlush(SettlementAdjustment.builder()
                    .settlementId(settlementId)
                    .settlementNumber(s.settlementNumber())
                    .groupBuyId(s.groupBuyId())
                    .marketId(s.marketId())
                    .creatorId(s.creatorId())
                    .threadId(threadId)
                    .requesterType(party)
                    .originalRewardAmount(s.rewardAmount())
                    .maxRewardAmount(s.maxRewardAmount())
                    .openedAt(now)
                    .deadlineAt(deadlineAt)
                    .noticeDueAt(noticeDueOf(deadlineAt))
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_ALREADY_EXISTS);
        }
        SettlementAdjustmentProposal proposal = proposalRepository.saveAndFlush(SettlementAdjustmentProposal.pending(
                adjustment.getId(), 1, party, partyId, amount, trimmed, now));

        // 정산 쪽 조건부 UPDATE 가 승자다 — 자동 확정이 먼저 지나갔으면 협의 · 스레드 · 카드 전부 롤백된다.
        settlementPort.holdForAdjustment(settlementId, adjustment.getId(), now);

        cards.opened(adjustment, s);
        Message requestCard = cards.proposal(adjustment, s, proposal);
        proposalRepository.linkCard(proposal.getId(), requestCard.getId());
        AfterCommit.run(() -> notifier.adjustmentRequested(settlementId, party.counterpart().name()));
        return new RequestResult(adjustment.getId(), threadId, proposal.getId(), deadlineAt);
    }

    // ------------------------------------------------------------------ 2-2 다른 금액 제안

    @Transactional
    public void counter(Long adjustmentId, SettlementParty party, Long partyId, long amount, String reason,
                        LocalDateTime now) {
        SettlementAdjustment adjustment = lockForParty(adjustmentId, party, partyId);
        requireOpen(adjustment, now);
        SettlementAdjustmentProposal latest = latestOf(adjustmentId);
        AdjustmentPermissionPolicy.Permissions permissions = permissionsOf(adjustment, latest, party, now);
        if (!permissions.canCounter()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN);
        }
        if (amount < 0 || amount > adjustment.getMaxRewardAmount()) {
            throw outOfRange(adjustment.getMaxRewardAmount());
        }
        if (amount == latest.getRewardAmount()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_AMOUNT_UNCHANGED);
        }
        String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmed != null) {
            requireReasonLength(trimmed);
        }
        if (proposalRepository.respond(latest.getId(), RESPONDABLE, ProposalStatus.COUNTERED, now, party,
                partyId) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_STATE_CHANGED);
        }
        SettlementAdjustmentProposal next;
        try {
            next = proposalRepository.saveAndFlush(SettlementAdjustmentProposal.pending(adjustmentId,
                    latest.getSeq() + 1, party, partyId, amount, trimmed, now));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_STATE_CHANGED);
        }
        AdjustableSettlement s = settlementPort.find(adjustment.getSettlementId()).orElse(null);
        Message card = cards.proposal(adjustment, s, next);
        proposalRepository.linkCard(next.getId(), card.getId());
        settlementPort.recordEvent(adjustment.getSettlementId(), SettlementEventType.ADJUSTMENT_COUNTERED, party,
                partyId, "%s 다른 금액 제안 · 리워드 %,d원".formatted(party.getLabel(), amount));
        Long settlementId = adjustment.getSettlementId();
        AfterCommit.run(() -> notifier.adjustmentCountered(settlementId, party.counterpart().name()));
    }

    // ------------------------------------------------------------------ 2-3 동의

    @Transactional
    public void accept(Long adjustmentId, Long proposalId, SettlementParty party, Long partyId, LocalDateTime now) {
        SettlementAdjustment adjustment = lockForParty(adjustmentId, party, partyId);
        requireOpen(adjustment, now);
        SettlementAdjustmentProposal latest = latestOf(adjustmentId);
        if (!latest.getId().equals(proposalId)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_STATE_CHANGED);
        }
        if (!permissionsOf(adjustment, latest, party, now).canAccept()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN);
        }
        long amount = latest.getRewardAmount();
        if (proposalRepository.respond(latest.getId(), RESPONDABLE, ProposalStatus.ACCEPTED, now, party, partyId) != 1
                || adjustmentRepository.closeAsAgreed(adjustmentId, amount, now) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_STATE_CHANGED);
        }
        Long settlementId = adjustment.getSettlementId();
        settlementPort.confirmByAgreement(settlementId, adjustmentId, amount, now);

        AdjustableSettlement s = settlementPort.find(settlementId).orElse(null);
        cards.responded(adjustment, s, latest, true, party, partyId, now);
        cards.agreed(adjustment, s, amount, party, now);
        AfterCommit.run(() -> notifier.adjustmentAgreed(settlementId));
    }

    // ------------------------------------------------------------------ 2-4 반대

    @Transactional
    public void reject(Long adjustmentId, Long proposalId, SettlementParty party, Long partyId, LocalDateTime now) {
        SettlementAdjustment adjustment = lockForParty(adjustmentId, party, partyId);
        requireOpen(adjustment, now);
        SettlementAdjustmentProposal latest = latestOf(adjustmentId);
        if (!latest.getId().equals(proposalId)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_STATE_CHANGED);
        }
        if (!permissionsOf(adjustment, latest, party, now).canReject()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN);
        }
        if (proposalRepository.respond(latest.getId(), EnumSet.of(ProposalStatus.PENDING), ProposalStatus.REJECTED,
                now, party, partyId) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_STATE_CHANGED);
        }
        Long settlementId = adjustment.getSettlementId();
        AdjustableSettlement s = settlementPort.find(settlementId).orElse(null);
        cards.responded(adjustment, s, latest, false, party, partyId, now);
        settlementPort.recordEvent(settlementId, SettlementEventType.ADJUSTMENT_REJECTED, party, partyId,
                "%s 반대 · %d번째 제안".formatted(party.getLabel(), latest.getSeq()));
        SettlementParty proposer = latest.getProposerType();
        AfterCommit.run(() -> notifier.adjustmentRejected(settlementId, proposer.name()));
    }

    // ------------------------------------------------------------------ 6절 배치

    @Transactional(readOnly = true)
    public List<Long> findIdsToNotify(LocalDateTime now) {
        return adjustmentRepository.findIdsToNotify(now, PageRequest.of(0, properties.getAdjustment().getBatchSize()));
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToExpire(LocalDateTime now) {
        return adjustmentRepository.findIdsToExpire(now, PageRequest.of(0, properties.getAdjustment().getBatchSize()));
    }

    /** D-1 통지(6-1) — 표지 조건부 UPDATE 가 1행일 때만 카드 · 이력 · 알림. 두 번 돌아도 카드 1장. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean notifyDeadline(Long adjustmentId, LocalDateTime now) {
        SettlementAdjustment adjustment = adjustmentRepository.findById(adjustmentId).orElse(null);
        if (adjustment == null || adjustmentRepository.markNoticeSent(adjustmentId, now) != 1) {
            return false;
        }
        AdjustableSettlement s = settlementPort.find(adjustment.getSettlementId()).orElse(null);
        cards.deadlineNotice(adjustment, s);
        settlementPort.recordEvent(adjustment.getSettlementId(), SettlementEventType.ADJUSTMENT_DEADLINE_NOTICED, null,
                null, "합의 기한 %s · 합의가 없으면 원래 금액으로 확정".formatted(
                        adjustment.getDeadlineAt().toLocalDate()));
        Long settlementId = adjustment.getSettlementId();
        AfterCommit.run(() -> notifier.adjustmentDeadlineD1(settlementId));
        return true;
    }

    /**
     * 기한 만료(6-2) — 간주 규칙이 없다(§42-3): 반대든 무응답이든 원래 금액으로 확정. 조건부 UPDATE 가 0행이면 동의가 먼저였다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long adjustmentId, LocalDateTime now) {
        SettlementAdjustment adjustment = adjustmentRepository.findForUpdate(adjustmentId).orElse(null);
        if (adjustment == null || adjustmentRepository.closeAsExpired(adjustmentId, now) != 1) {
            return false;
        }
        proposalRepository.closeOpen(adjustmentId, now);
        Long settlementId = adjustment.getSettlementId();
        settlementPort.confirmByExpiry(settlementId, adjustmentId, now);
        AdjustableSettlement s = settlementPort.find(settlementId).orElse(null);
        cards.expired(adjustment, s, now);
        AfterCommit.run(() -> notifier.adjustmentExpired(settlementId));
        return true;
    }

    // ------------------------------------------------------------------ 공통

    /** 협의 기한 — 개설 + N영업일(기산일 불산입 · 다음 영업일부터) + 마감 시각 · 연장 없음(1-6). */
    public LocalDateTime deadlineOf(LocalDateTime openedAt) {
        SettlementProperties.Adjustment config = properties.getAdjustment();
        return calendar.addBusinessDays(openedAt.toLocalDate(), config.getDeadlineBusinessDays())
                .atTime(config.getDeadlineTime());
    }

    /** D-1 통지 예정 — 마감일의 N영업일 전 + 통지 시각(1-6). */
    public LocalDateTime noticeDueOf(LocalDateTime deadlineAt) {
        SettlementProperties.Adjustment config = properties.getAdjustment();
        LocalDate date = deadlineAt.toLocalDate();
        for (int i = 0; i < config.getNoticeBusinessDaysBefore(); i++) {
            date = calendar.previousBusinessDay(date);
        }
        return date.atTime(config.getNoticeTime());
    }

    private SettlementAdjustment lockForParty(Long adjustmentId, SettlementParty party, Long partyId) {
        SettlementAdjustment adjustment = adjustmentRepository.findForUpdate(adjustmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_FOUND));
        if (!adjustment.isParty(party, partyId)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_ACCESS_DENIED);
        }
        return adjustment;
    }

    private static void requireOpen(SettlementAdjustment adjustment, LocalDateTime now) {
        if (adjustment.getStatus() != AdjustmentStatus.OPEN) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_CLOSED);
        }
        if (adjustment.isDeadlinePassed(now)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_DEADLINE_PASSED);
        }
    }

    private SettlementAdjustmentProposal latestOf(Long adjustmentId) {
        List<SettlementAdjustmentProposal> proposals = proposalRepository.findByAdjustmentId(adjustmentId);
        if (proposals.isEmpty()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_STATE_CHANGED);
        }
        return proposals.get(proposals.size() - 1);
    }

    private static AdjustmentPermissionPolicy.Permissions permissionsOf(SettlementAdjustment adjustment,
                                                                       SettlementAdjustmentProposal latest,
                                                                       SettlementParty viewer, LocalDateTime now) {
        return AdjustmentPermissionPolicy.permissions(adjustment.getStatus(), adjustment.getDeadlineAt(),
                latest.getStatus(), latest.getProposerType(), viewer, now);
    }

    private void requireReasonLength(String reason) {
        if (reason.length() > properties.getAdjustment().getReasonMaxLength()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "조정 사유는 %d자까지 입력할 수 있습니다.".formatted(properties.getAdjustment().getReasonMaxLength()));
        }
    }

    private static BusinessException outOfRange(long max) {
        return new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE,
                "리워드 금액은 0원 ~ %,d원 안에서 원래 금액과 다르게 입력해 주세요.".formatted(max));
    }
}

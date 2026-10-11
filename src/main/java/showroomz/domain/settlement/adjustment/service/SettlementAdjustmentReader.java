package showroomz.domain.settlement.adjustment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustmentProposal;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentProposalRepository;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.AdjustmentTurn;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 조정 협의 읽기(44 이슈 스레드 설계서 1-7 역방향 포트) — 정산 상세 세 서피스의 「조정 내역」 · 스레드 고정 카드 · 어드민 20b 패널이 같은
 * 요약을 읽는다. 차례 · 권한은 {@link AdjustmentPermissionPolicy} 식이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementAdjustmentReader {

    private final SettlementAdjustmentRepository adjustmentRepository;
    private final SettlementAdjustmentProposalRepository proposalRepository;
    private final BusinessCalendar calendar;

    public record ProposalView(Long proposalId, int seq, SettlementParty proposerType, Long proposerId,
                               long rewardAmount, String reason, ProposalStatus status, LocalDateTime proposedAt,
                               LocalDateTime respondedAt, SettlementParty responderType, Long cardMessageId) {
    }

    /**
     * @param remainingBusinessDays D-N — 오늘 다음 날부터 마감일까지 영업일 · 마감 당일 · 경과는 0 · 종결 뒤 null
     */
    public record AdjustmentSummary(Long adjustmentId, Long settlementId, String settlementNumber, Long groupBuyId,
                                    Long marketId, Long creatorId, Long threadId, AdjustmentStatus status,
                                    SettlementParty requesterType, long originalRewardAmount, long maxRewardAmount,
                                    Long agreedRewardAmount, Long finalRewardAmount, LocalDateTime openedAt,
                                    LocalDateTime deadlineAt, LocalDateTime closedAt, Integer remainingBusinessDays,
                                    ProposalView latestProposal, List<ProposalView> proposals) {

        public boolean isParty(SettlementParty party, Long partyId) {
            return partyId != null && partyId.equals(party == SettlementParty.SELLER ? marketId : creatorId);
        }

        /** 이 뷰어의 최신 제안 · 상대의 최신 제안(스튜디오 「내 요청 · 상대 제안」). */
        public Optional<ProposalView> latestOf(SettlementParty party) {
            for (int i = proposals.size() - 1; i >= 0; i--) {
                if (proposals.get(i).proposerType() == party) {
                    return Optional.of(proposals.get(i));
                }
            }
            return Optional.empty();
        }
    }

    public Optional<AdjustmentSummary> findBySettlementId(Long settlementId) {
        return adjustmentRepository.findBySettlementId(settlementId).map(a -> summarize(List.of(a)).get(a.getId()));
    }

    /** 정산 id → 요약. */
    public Map<Long, AdjustmentSummary> findBySettlementIds(Collection<Long> settlementIds) {
        if (settlementIds.isEmpty()) {
            return Map.of();
        }
        return summarize(adjustmentRepository.findBySettlementIdIn(settlementIds)).values().stream()
                .collect(Collectors.toMap(AdjustmentSummary::settlementId, Function.identity()));
    }

    public Optional<AdjustmentSummary> findByThreadId(Long threadId) {
        return adjustmentRepository.findByThreadId(threadId).map(a -> summarize(List.of(a)).get(a.getId()));
    }

    /** 스레드 id → 요약 — 연결·소통 목록 줄의 「[이슈] 정산 조정 요청 · 내 응답 필요」. */
    public Map<Long, AdjustmentSummary> findByThreadIds(Collection<Long> threadIds) {
        if (threadIds.isEmpty()) {
            return Map.of();
        }
        return summarize(adjustmentRepository.findByThreadIdIn(threadIds)).values().stream()
                .collect(Collectors.toMap(AdjustmentSummary::threadId, Function.identity()));
    }

    public Optional<AdjustmentSummary> findById(Long adjustmentId) {
        return adjustmentRepository.findById(adjustmentId).map(a -> summarize(List.of(a)).get(a.getId()));
    }

    public static AdjustmentTurn turnFor(AdjustmentSummary summary, SettlementParty viewer) {
        ProposalView latest = summary.latestProposal();
        return AdjustmentPermissionPolicy.turnFor(summary.status(), latest == null ? null : latest.status(),
                latest == null ? null : latest.proposerType(), viewer);
    }

    public static AdjustmentPermissionPolicy.Permissions permissions(AdjustmentSummary summary, SettlementParty viewer,
                                                                    LocalDateTime now) {
        ProposalView latest = summary.latestProposal();
        return AdjustmentPermissionPolicy.permissions(summary.status(), summary.deadlineAt(),
                latest == null ? null : latest.status(), latest == null ? null : latest.proposerType(), viewer, now);
    }

    private Map<Long, AdjustmentSummary> summarize(List<SettlementAdjustment> adjustments) {
        Map<Long, AdjustmentSummary> result = new HashMap<>();
        if (adjustments.isEmpty()) {
            return result;
        }
        Map<Long, List<SettlementAdjustmentProposal>> proposals = proposalRepository
                .findByAdjustmentIdIn(adjustments.stream().map(SettlementAdjustment::getId).toList()).stream()
                .collect(Collectors.groupingBy(SettlementAdjustmentProposal::getAdjustmentId));
        LocalDate today = LocalDate.now();
        for (SettlementAdjustment a : adjustments) {
            List<ProposalView> views = proposals.getOrDefault(a.getId(), List.of()).stream()
                    .map(SettlementAdjustmentReader::view).toList();
            Integer remaining = a.getStatus() == AdjustmentStatus.OPEN
                    ? calendar.businessDaysBetween(today, a.getDeadlineAt().toLocalDate()) : null;
            result.put(a.getId(), new AdjustmentSummary(a.getId(), a.getSettlementId(), a.getSettlementNumber(),
                    a.getGroupBuyId(), a.getMarketId(), a.getCreatorId(), a.getThreadId(), a.getStatus(),
                    a.getRequesterType(), a.getOriginalRewardAmount(), a.getMaxRewardAmount(),
                    a.getAgreedRewardAmount(), a.getFinalRewardAmount(), a.getOpenedAt(), a.getDeadlineAt(),
                    a.getClosedAt(), remaining, views.isEmpty() ? null : views.get(views.size() - 1), views));
        }
        return result;
    }

    private static ProposalView view(SettlementAdjustmentProposal p) {
        return new ProposalView(p.getId(), p.getSeq(), p.getProposerType(), p.getProposerId(), p.getRewardAmount(),
                p.getReason(), p.getStatus(), p.getProposedAt(), p.getRespondedAt(), p.getResponderType(),
                p.getCardMessageId());
    }
}

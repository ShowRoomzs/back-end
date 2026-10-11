package showroomz.api.common.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementAdjustmentDto;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort.AdjustableSettlement;
import showroomz.domain.settlement.adjustment.service.AdjustmentPermissionPolicy;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.AdjustmentSummary;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.ProposalView;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentService;
import showroomz.domain.settlement.adjustment.type.AdjustmentTurn;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 정산 조정 협의 API(44 이슈 스레드 설계서 3절) — 파트너센터 · 쇼룸 스튜디오 컨트롤러가 당사자({@link SettlementParty} · id)만 바꿔
 * 부르는 공용 서비스. 쓰기는 {@link SettlementAdjustmentService}, 읽기는 {@link SettlementAdjustmentReader}에 위임하고 여기서는 응답을
 * 뷰어 기준으로 조립한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementAdjustmentApiService {

    private final SettlementAdjustmentService adjustmentService;
    private final SettlementAdjustmentReader reader;
    private final SettlementAdjustmentPort settlementPort;
    private final MessageThreadRepository threadRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final MarketRepository marketRepository;
    private final CreatorRepository creatorRepository;

    // ------------------------------------------------------------------ 3-2 미리보기

    @Transactional(readOnly = true)
    public SettlementAdjustmentDto.PreviewResponse preview(SettlementParty party, Long partyId, Long settlementId,
                                                           Long rewardAmount) {
        AdjustableSettlement s = ownedSettlement(party, partyId, settlementId);
        boolean hasAdjustment = reader.findBySettlementId(settlementId).isPresent();
        String reason = s.inReviewWindow() ? null : hasAdjustment ? "ALREADY_ADJUSTING" : "REVIEW_CLOSED";
        SettlementAdjustmentDto.Amounts original = amounts(settlementPort.preview(settlementId, s.rewardAmount()));
        if (rewardAmount == null) {
            return new SettlementAdjustmentDto.PreviewResponse(s.settlementId(), s.settlementNumber(), s.inReviewWindow(),
                    reason, s.reviewEndsAt(), s.rewardAmount(), s.maxRewardAmount(), null, null, original, null);
        }
        boolean inRange = rewardAmount >= 0 && rewardAmount <= s.maxRewardAmount();
        return new SettlementAdjustmentDto.PreviewResponse(s.settlementId(), s.settlementNumber(), s.inReviewWindow(),
                reason, s.reviewEndsAt(), s.rewardAmount(), s.maxRewardAmount(),
                new SettlementAdjustmentDto.Input(rewardAmount),
                inRange ? amounts(settlementPort.preview(settlementId, rewardAmount)) : null, original, inRange);
    }

    // ------------------------------------------------------------------ 3-3 요청

    public SettlementAdjustmentDto.RequestResponse request(SettlementParty party, Long partyId, Long settlementId,
                                                           SettlementAdjustmentDto.RequestBody body) {
        SettlementAdjustmentService.RequestResult result = adjustmentService.request(settlementId, party, partyId,
                body.rewardAmount(), body.reason(), LocalDateTime.now());
        return new SettlementAdjustmentDto.RequestResponse(result.adjustmentId(), result.threadId(),
                result.proposalId(), result.deadlineAt());
    }

    // ------------------------------------------------------------------ 3-4 고정 카드

    @Transactional(readOnly = true)
    public SettlementAdjustmentDto.AdjustmentView byThread(SettlementParty party, Long partyId, Long threadId) {
        MessageThread thread = threadRepository.findWithPartiesById(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THREAD_NOT_FOUND));
        requireThreadParty(thread, party, partyId);
        if (thread.getKind() != ThreadKind.SETTLEMENT_ADJUSTMENT) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_FOUND);
        }
        AdjustmentSummary summary = reader.findByThreadId(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_FOUND));
        return view(summary, party);
    }

    // ------------------------------------------------------------------ 3-5 응답 3종

    public SettlementAdjustmentDto.AdjustmentView counter(SettlementParty party, Long partyId, Long adjustmentId,
                                                          SettlementAdjustmentDto.CounterBody body) {
        adjustmentService.counter(adjustmentId, party, partyId, body.rewardAmount(), body.reason(), LocalDateTime.now());
        return viewOf(adjustmentId, party);
    }

    public SettlementAdjustmentDto.AdjustmentView accept(SettlementParty party, Long partyId, Long adjustmentId,
                                                         Long proposalId) {
        adjustmentService.accept(adjustmentId, proposalId, party, partyId, LocalDateTime.now());
        return viewOf(adjustmentId, party);
    }

    public SettlementAdjustmentDto.AdjustmentView reject(SettlementParty party, Long partyId, Long adjustmentId,
                                                         Long proposalId) {
        adjustmentService.reject(adjustmentId, proposalId, party, partyId, LocalDateTime.now());
        return viewOf(adjustmentId, party);
    }

    // ------------------------------------------------------------------ 조립

    private SettlementAdjustmentDto.AdjustmentView viewOf(Long adjustmentId, SettlementParty party) {
        return view(reader.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_FOUND)), party);
    }

    public SettlementAdjustmentDto.AdjustmentView view(AdjustmentSummary summary, SettlementParty viewer) {
        AdjustmentTurn turn = SettlementAdjustmentReader.turnFor(summary, viewer);
        AdjustmentPermissionPolicy.Permissions permissions = SettlementAdjustmentReader.permissions(summary, viewer,
                LocalDateTime.now());
        String brandName = marketRepository.findById(summary.marketId()).map(m -> m.getMarketName()).orElse(null);
        String showroomName = creatorRepository.findById(summary.creatorId()).map(c -> c.getShowroomName())
                .orElse(null);
        Optional<GroupBuy> groupBuy = groupBuyRepository.findDetailById(summary.groupBuyId());
        SettlementParty counterpart = viewer.counterpart();
        return new SettlementAdjustmentDto.AdjustmentView(summary.adjustmentId(), summary.threadId(),
                summary.status().name(), summary.status().getLabel(), turn.name(), turn.getLabel(),
                turn.getTone().name(),
                new SettlementAdjustmentDto.SettlementRef(summary.settlementId(), summary.settlementNumber()),
                new SettlementAdjustmentDto.GroupBuyRef(summary.groupBuyId(),
                        groupBuy.map(GroupBuy::getGroupBuyNumber).orElse(null),
                        groupBuy.map(g -> g.getContract().getTitle()).orElse(null)),
                new SettlementAdjustmentDto.Counterpart(counterpart.name(),
                        counterpart == SettlementParty.SELLER ? brandName : showroomName),
                summary.requesterType().name(), summary.originalRewardAmount(), summary.maxRewardAmount(),
                summary.latestProposal() == null ? null : proposal(summary, summary.latestProposal(), brandName,
                        showroomName),
                summary.agreedRewardAmount(), summary.finalRewardAmount(), summary.openedAt(), summary.deadlineAt(),
                summary.remainingBusinessDays(), summary.closedAt(),
                new SettlementAdjustmentDto.Permissions(permissions.canAccept(), permissions.canReject(),
                        permissions.canCounter(), permissions.canSend()),
                summary.proposals().stream().map(p -> proposal(summary, p, brandName, showroomName)).toList());
    }

    private static SettlementAdjustmentDto.ProposalView proposal(AdjustmentSummary summary, ProposalView p,
                                                                 String brandName, String showroomName) {
        return new SettlementAdjustmentDto.ProposalView(p.proposalId(), p.seq(), p.proposerType().name(),
                p.proposerType() == SettlementParty.SELLER ? brandName : showroomName, p.rewardAmount(),
                p.rewardAmount() - summary.originalRewardAmount(), p.reason(), p.status().name(), p.proposedAt(),
                p.respondedAt(), p.cardMessageId());
    }

    private AdjustableSettlement ownedSettlement(SettlementParty party, Long partyId, Long settlementId) {
        return settlementPort.find(settlementId)
                .filter(s -> partyId.equals(party == SettlementParty.SELLER ? s.marketId() : s.creatorId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    /** 스레드 접근 판정 — 연결의 당사자(연결·소통과 같은 규칙). */
    private static void requireThreadParty(MessageThread thread, SettlementParty party, Long partyId) {
        Connection connection = thread.getConnection();
        Long ownerId = party == SettlementParty.SELLER
                ? (connection.getMarket() == null ? null : connection.getMarket().getId())
                : (connection.getCreator() == null ? null : connection.getCreator().getId());
        if (ownerId == null || !ownerId.equals(partyId)) {
            throw new BusinessException(ErrorCode.THREAD_ACCESS_DENIED);
        }
    }

    private static SettlementAdjustmentDto.Amounts amounts(SettlementAdjustmentPort.AmountPreview p) {
        return new SettlementAdjustmentDto.Amounts(p.rewardAmount(), p.rewardVatAmount(), p.withholdingAmount(),
                p.creatorNetAmount(), p.brandPayoutAmount());
    }
}

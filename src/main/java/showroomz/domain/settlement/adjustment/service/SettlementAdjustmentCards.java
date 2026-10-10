package showroomz.domain.settlement.adjustment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageRefType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustmentProposal;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort.AdjustableSettlement;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 조정 협의 시스템 카드 8종 등록(44 이슈 스레드 설계서 5절) — 절차는 시스템 카드, 대화는 말풍선. 카드의 버튼 상태는 참조한 제안 행에서
 * 읽으므로 카드를 고쳐 쓰지 않는다.
 *
 * <p>보낸 사람 — 시스템 카드(개설 · D-1 · 결과)는 {@code ADMIN · 0}(운영팀 환영 메시지와 같은 규칙 · 사람이 아니다). 제안 · 응답 카드는
 * 그 행위자(브랜드 = 마켓 id · 인플루언서 = 크리에이터 id). 멱등키는 참조 객체에서 만든 고정값이라 배치를 다시 돌려도 카드가 두 장 붙지
 * 않는다(5-3).
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SettlementAdjustmentCards {

    private final MessageThreadRepository threadRepository;
    private final MessageThreadService messageThreadService;
    private final MarketRepository marketRepository;
    private final CreatorRepository creatorRepository;

    public Message opened(SettlementAdjustment a, AdjustableSettlement s) {
        return system(a, s, "stl-adj-%d-opened".formatted(a.getId()), MessageCardType.SETTLEMENT_ADJUSTMENT_OPENED,
                new Snapshot(a).deadline(a.getDeadlineAt()));
    }

    /** 요청(seq 1) · 다른 금액 제안(seq n) — 보낸 사람 = 제안자. */
    public Message proposal(SettlementAdjustment a, AdjustableSettlement s, SettlementAdjustmentProposal p) {
        MessageCardType type = p.getSeq() == 1
                ? MessageCardType.SETTLEMENT_ADJUSTMENT_REQUEST : MessageCardType.SETTLEMENT_ADJUSTMENT_COUNTER;
        return post(a, s, p.getProposerType().participantType(), p.getProposerId(),
                "stl-adj-%d-proposal-%d".formatted(a.getId(), p.getSeq()), type,
                MessageRefType.SETTLEMENT_ADJUSTMENT_PROPOSAL, p.getId(),
                new Snapshot(a).proposal(p).reason(p.getReason()).deadline(a.getDeadlineAt()));
    }

    /** 동의 · 반대 — 보낸 사람 = 응답한 쪽. 반대 카드에는 사유가 없다(§42-2). */
    public Message responded(SettlementAdjustment a, AdjustableSettlement s, SettlementAdjustmentProposal p,
                             boolean accepted, SettlementParty responder, Long responderId, LocalDateTime at) {
        return post(a, s, responder.participantType(), responderId,
                "stl-adj-%d-proposal-%d-%s".formatted(a.getId(), p.getSeq(), accepted ? "accepted" : "rejected"),
                accepted ? MessageCardType.SETTLEMENT_ADJUSTMENT_ACCEPTED : MessageCardType.SETTLEMENT_ADJUSTMENT_REJECTED,
                MessageRefType.SETTLEMENT_ADJUSTMENT_PROPOSAL, p.getId(),
                new Snapshot(a).proposal(p).responder(responder, at));
    }

    public Message deadlineNotice(SettlementAdjustment a, AdjustableSettlement s) {
        return system(a, s, "stl-adj-%d-d1".formatted(a.getId()), MessageCardType.SETTLEMENT_ADJUSTMENT_DEADLINE_NOTICE,
                new Snapshot(a).deadline(a.getDeadlineAt()));
    }

    public Message agreed(SettlementAdjustment a, AdjustableSettlement s, long agreedAmount, SettlementParty agreedBy,
                          LocalDateTime closedAt) {
        return system(a, s, "stl-adj-%d-closed".formatted(a.getId()), MessageCardType.SETTLEMENT_ADJUSTMENT_AGREED,
                new Snapshot(a).deadline(a.getDeadlineAt()).closed(agreedAmount, agreedBy, closedAt));
    }

    public Message expired(SettlementAdjustment a, AdjustableSettlement s, LocalDateTime closedAt) {
        return system(a, s, "stl-adj-%d-closed".formatted(a.getId()), MessageCardType.SETTLEMENT_ADJUSTMENT_EXPIRED,
                new Snapshot(a).deadline(a.getDeadlineAt()).closed(a.getOriginalRewardAmount(), null, closedAt));
    }

    private Message system(SettlementAdjustment a, AdjustableSettlement s, String clientMessageId,
                           MessageCardType cardType, Snapshot snapshot) {
        return post(a, s, ParticipantType.ADMIN, OperatorChannelService.SYSTEM_OPERATOR_ID, clientMessageId, cardType,
                MessageRefType.SETTLEMENT_ADJUSTMENT, a.getId(), snapshot);
    }

    private Message post(SettlementAdjustment a, AdjustableSettlement s, ParticipantType senderType, Long senderId,
                         String clientMessageId, MessageCardType cardType, MessageRefType refType, Long refId,
                         Snapshot snapshot) {
        MessageThread thread = threadRepository.findById(a.getThreadId())
                .orElseThrow(() -> new BusinessException(ErrorCode.THREAD_NOT_FOUND));
        MessageCardPayload payload = MessageCardPayload.settlementAdjustment(s == null ? null : s.contractId(),
                s == null ? null : s.contractNumber(), s == null ? null : s.groupBuyTitle(), snapshot.build());
        return messageThreadService.postSystemCard(thread, senderType, senderId, clientMessageId, cardType, refType,
                refId, payload).message();
    }

    /** 브랜드명 · 쇼룸명 — 양측이 서로 보는 값이라 스냅샷에 넣는다(운영자 이름과 다르다 · 5-2). */
    private String nameOf(SettlementAdjustment a, SettlementParty party) {
        if (party == null) {
            return null;
        }
        return party == SettlementParty.SELLER
                ? marketRepository.findById(a.getMarketId()).map(m -> m.getMarketName()).orElse(null)
                : creatorRepository.findById(a.getCreatorId()).map(c -> c.getShowroomName()).orElse(null);
    }

    /** 카드 종류마다 쓰는 칸만 채우는 작업본. */
    private final class Snapshot {

        private final SettlementAdjustment a;
        private Long proposalId;
        private Integer seq;
        private SettlementParty proposer;
        private Long rewardAmount;
        private String reason;
        private LocalDateTime deadlineAt;
        private SettlementParty responder;
        private LocalDateTime respondedAt;
        private Long finalRewardAmount;
        private SettlementParty agreedBy;
        private LocalDateTime closedAt;

        private Snapshot(SettlementAdjustment a) {
            this.a = a;
        }

        Snapshot proposal(SettlementAdjustmentProposal p) {
            this.proposalId = p.getId();
            this.seq = p.getSeq();
            this.proposer = p.getProposerType();
            this.rewardAmount = p.getRewardAmount();
            return this;
        }

        Snapshot reason(String reason) {
            this.reason = reason;
            return this;
        }

        Snapshot deadline(LocalDateTime deadlineAt) {
            this.deadlineAt = deadlineAt;
            return this;
        }

        Snapshot responder(SettlementParty responder, LocalDateTime respondedAt) {
            this.responder = responder;
            this.respondedAt = respondedAt;
            return this;
        }

        Snapshot closed(long finalRewardAmount, SettlementParty agreedBy, LocalDateTime closedAt) {
            this.finalRewardAmount = finalRewardAmount;
            this.agreedBy = agreedBy;
            this.closedAt = closedAt;
            return this;
        }

        MessageCardPayload.Adjustment build() {
            return new MessageCardPayload.Adjustment(a.getSettlementId(), a.getSettlementNumber(), a.getGroupBuyId(),
                    a.getId(), proposalId, seq, proposer == null ? null : proposer.name(), nameOf(a, proposer),
                    a.getOriginalRewardAmount(), rewardAmount, reason, deadlineAt,
                    responder == null ? null : responder.name(), nameOf(a, responder), respondedAt, finalRewardAmount,
                    agreedBy == null ? null : agreedBy.name(), nameOf(a, agreedBy), closedAt);
        }
    }
}

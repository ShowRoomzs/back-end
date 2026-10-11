package showroomz.domain.settlement.adjustment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;

import java.time.LocalDateTime;

/**
 * 제안 1건(44 이슈 스레드 설계서 1-2) — 금액이 든 글은 전부 제안이다. 첫 요청도 제안(seq 1)이고 카드 종류만 다르다.
 * 동의 · 반대 · 다른 금액 제안은 이 행의 상태 변화다 — 쓰기는 리포지토리 조건부 UPDATE 로만.
 */
@Entity
@Table(name = "settlement_adjustment_proposal",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_adjustment_proposal_seq",
                columnNames = {"adjustment_id", "seq"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementAdjustmentProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proposal_id")
    private Long id;

    @Column(name = "adjustment_id", nullable = false)
    private Long adjustmentId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposer_type", nullable = false, length = 16)
    private SettlementParty proposerType;

    @Column(name = "proposer_id", nullable = false)
    private Long proposerId;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProposalStatus status;

    @Column(name = "proposed_at", nullable = false)
    private LocalDateTime proposedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "responder_type", length = 16)
    private SettlementParty responderType;

    @Column(name = "responder_id")
    private Long responderId;

    @Column(name = "card_message_id")
    private Long cardMessageId;

    public static SettlementAdjustmentProposal pending(Long adjustmentId, int seq, SettlementParty proposerType,
                                                       Long proposerId, long rewardAmount, String reason,
                                                       LocalDateTime proposedAt) {
        SettlementAdjustmentProposal proposal = new SettlementAdjustmentProposal();
        proposal.adjustmentId = adjustmentId;
        proposal.seq = seq;
        proposal.proposerType = proposerType;
        proposal.proposerId = proposerId;
        proposal.rewardAmount = rewardAmount;
        proposal.reason = reason;
        proposal.status = ProposalStatus.PENDING;
        proposal.proposedAt = proposedAt;
        return proposal;
    }
}

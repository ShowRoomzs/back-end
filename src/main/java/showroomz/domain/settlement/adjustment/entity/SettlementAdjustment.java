package showroomz.domain.settlement.adjustment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;

import java.time.LocalDateTime;

/**
 * 정산 조정 협의 1건(44 이슈 스레드 설계서 1-1) — 정산 1건에 딱 1건(종결 후 같은 사안 재요청 불가 · §42-1).
 *
 * <p>금액의 정본은 정산이다(0-6) — 이 행은 개설 시점의 원래 리워드 · 상한을 박아 두고 제안과 비교만 한다. 상태 전이는 전부 리포지토리의
 * 조건부 UPDATE 다 — 낙관적 잠금을 두지 않는다(조건부 UPDATE 와 겹치면 두 번 실패한다).
 */
@Entity
@Table(name = "settlement_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementAdjustment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adjustment_id")
    private Long id;

    @Column(name = "settlement_id", nullable = false, unique = true)
    private Long settlementId;

    @Column(name = "settlement_number", nullable = false, length = 16)
    private String settlementNumber;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "thread_id", nullable = false, unique = true)
    private Long threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 16)
    private SettlementParty requesterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AdjustmentStatus status;

    @Column(name = "original_reward_amount", nullable = false)
    private long originalRewardAmount;

    @Column(name = "max_reward_amount", nullable = false)
    private long maxRewardAmount;

    @Column(name = "agreed_reward_amount")
    private Long agreedRewardAmount;

    @Column(name = "final_reward_amount")
    private Long finalRewardAmount;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Column(name = "notice_due_at", nullable = false)
    private LocalDateTime noticeDueAt;

    @Column(name = "notice_sent_at")
    private LocalDateTime noticeSentAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder
    private SettlementAdjustment(Long settlementId, String settlementNumber, Long groupBuyId, Long marketId,
                                 Long creatorId, Long threadId, SettlementParty requesterType,
                                 long originalRewardAmount, long maxRewardAmount, LocalDateTime openedAt,
                                 LocalDateTime deadlineAt, LocalDateTime noticeDueAt) {
        this.settlementId = settlementId;
        this.settlementNumber = settlementNumber;
        this.groupBuyId = groupBuyId;
        this.marketId = marketId;
        this.creatorId = creatorId;
        this.threadId = threadId;
        this.requesterType = requesterType;
        this.status = AdjustmentStatus.OPEN;
        this.originalRewardAmount = originalRewardAmount;
        this.maxRewardAmount = maxRewardAmount;
        this.openedAt = openedAt;
        this.deadlineAt = deadlineAt;
        this.noticeDueAt = noticeDueAt;
    }

    /** 당사자인가 — 브랜드는 마켓 id, 인플루언서는 크리에이터 id. */
    public boolean isParty(SettlementParty party, Long partyId) {
        return partyId != null && partyId.equals(party == SettlementParty.SELLER ? marketId : creatorId);
    }

    public Long partyIdOf(SettlementParty party) {
        return party == SettlementParty.SELLER ? marketId : creatorId;
    }

    public boolean isDeadlinePassed(LocalDateTime now) {
        return now.isAfter(deadlineAt);
    }
}

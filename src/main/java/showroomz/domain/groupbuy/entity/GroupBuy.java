package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 공구 본체 — 파트너·스튜디오·어드민 3서피스가 공유하는 하나의 객체(설계서 0-1).
 *
 * <p>계약 조건(공구명·상품·고정 지급비·콘텐츠 의무)은 복사하지 않고 {@link #contract}로 참조한다(설계서 0-3).
 * 참조 대상 계약은 CONCLUDED 이후 불변이다. 공구가 소유하는 값은 기간 하나이고, 그중에서도
 * 연장이 바꾸는 값은 {@link #endAt} 하나뿐이다.
 *
 * <p>상태 전이에 딸린 부수 필드는 이 클래스가 채우고, 전이 자체의 경합 차단은
 * 리포지토리의 조건부 UPDATE가 한다(설계서 3-4). 둘은 같은 트랜잭션 안에 있다.
 */
@Entity
@Table(name = "group_buy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_id")
    private Long id;

    /** GB-YYYYMMDD-NNN · 생성일(=체결일) 기준(설계서 1-10). */
    @Column(name = "group_buy_number", nullable = false, length = 30, unique = true)
    private String groupBuyNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, unique = true, updatable = false)
    private Contract contract;

    /** 계약에서 복사 — 목록 인덱스용 비정규화. 계약에서 불변이라 어긋날 위험이 없다(설계서 1-1). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false, updatable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private Creator creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GroupBuyStatus status;

    @Column(name = "start_at", nullable = false, updatable = false)
    private LocalDateTime startAt;

    /** 현재 종료 예정 — 연장 수락 시에만 바뀐다. 원래 종료 예정은 {@code contract.groupBuyEndAt}이다. */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /** 실제 종결 시각 — 기간 종료는 {@link #endAt}, 조기 마감·중단은 판정 시각이다. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_type", length = 24)
    private GroupBuyCloseType closeType;

    @Column(name = "closing_change_request_id")
    private Long closingChangeRequestId;

    @Column(name = "closing_admin_suspension_id")
    private Long closingAdminSuspensionId;

    /** 게이트 ① — 제25조 제재 판정의 증거(§29-4). 해제 경로가 없다(설계서 4-6). */
    @Column(name = "stock_confirmed_at")
    private LocalDateTime stockConfirmedAt;

    @Column(name = "stock_confirmed_by")
    private Long stockConfirmedBy;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "fulfillment_due_at")
    private LocalDateTime fulfillmentDueAt;

    @Column(name = "fulfillment_resolved_at")
    private LocalDateTime fulfillmentResolvedAt;

    @Column(name = "fulfillment_resolution_note", length = 1000)
    private String fulfillmentResolutionNote;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 체결 트랜잭션 안에서만 불린다(설계서 0-2). 기간은 계약에서 복사하고 이후 {@link #endAt}만 변한다. */
    public static GroupBuy createFrom(Contract contract, String groupBuyNumber) {
        return GroupBuy.builder()
                .groupBuyNumber(groupBuyNumber)
                .contract(contract)
                .market(contract.getMarket())
                .creator(contract.getCreator())
                .status(GroupBuyStatus.PREPARING)
                .startAt(contract.getGroupBuyStartAt())
                .endAt(contract.getGroupBuyEndAt())
                .build();
    }

    public boolean isOwnedBy(Long marketId) {
        return market != null && market.getId().equals(marketId);
    }

    public boolean isStockConfirmed() {
        return stockConfirmedAt != null;
    }

    /**
     * 총 공구 일수 — 시작·종료 「일자」 기준 양끝 포함. 계약 H4({@code Contract.periodDays})와 같은 계산이어야
     * 연장 상한 30일 판정이 계약 검증과 어긋나지 않는다(설계서 4-6).
     */
    public int totalDays() {
        return daysInclusive(startAt, endAt);
    }

    public static int daysInclusive(LocalDateTime from, LocalDateTime to) {
        return (int) ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1;
    }

    // ── 상태 전이에 딸린 부수 필드 ────────────────────────────────────────────

    public void applyStockConfirmed(Long sellerId, LocalDateTime now) {
        this.stockConfirmedAt = now;
        this.stockConfirmedBy = sellerId;
    }

    public void applyReady(LocalDateTime now) {
        this.status = GroupBuyStatus.READY;
        this.readyAt = now;
    }

    public void applyOpened(LocalDateTime now) {
        this.status = GroupBuyStatus.IN_PROGRESS;
        this.openedAt = now;
    }

    /**
     * 기간 완주 종료. {@code endedAt = endAt}이지 {@code now}가 아니다 — 스케줄러가 23:56에 돌아도
     * 공구는 23:55에 끝난 것이다(설계서 3-3).
     */
    public void applyCompleted(LocalDateTime fulfillmentDueAt) {
        this.status = GroupBuyStatus.ENDED;
        this.endedAt = this.endAt;
        this.closeType = GroupBuyCloseType.COMPLETED;
        this.fulfillmentDueAt = fulfillmentDueAt;
    }

    public void applySettled(LocalDateTime transferredAt) {
        this.status = GroupBuyStatus.SETTLED;
        this.settledAt = transferredAt;
    }

    public void applyFulfillmentResolved(String note, LocalDateTime now) {
        this.fulfillmentResolvedAt = now;
        this.fulfillmentResolutionNote = note;
    }
}

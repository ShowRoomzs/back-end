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

    /**
     * 이행 3자 스레드가 양측 동의로 종결된 시각(32 설계 1-1). 보류 해제({@link #fulfillmentResolvedAt})와 다른 사건이다 —
     * 합의는 연결·소통이, 해제는 정산 관리가 각자 다른 화면·시각에 한다.
     */
    @Column(name = "fulfillment_agreed_at")
    private LocalDateTime fulfillmentAgreedAt;

    /** 정산 보류가 풀린 시각 — 보류 파생식(30 설계 1-9)의 기준이다. 합의만으로는 풀리지 않는다. */
    @Column(name = "fulfillment_resolved_at")
    private LocalDateTime fulfillmentResolvedAt;

    /** 무엇으로 합의했나 — 당사자 합의이지 운영자 판정이 아니다(제20조④). */
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
     * 종결 — 종결 경로 5개(기간 종료 · 조기 마감 승인 · 중단 요청 승인 · 직권 중단 집행 · 긴급)가 모두 이 메서드를 탄다
     * (32 설계 8-1 {@code GroupBuyTerminator}). 상태 컬럼의 경합 차단은 리포지토리 조건부 UPDATE가 먼저 하고,
     * 이 메서드는 같은 트랜잭션에서 딸린 필드를 채운다.
     *
     * @param endedAt          기간 종료는 {@link #endAt} — 스케줄러가 23:56에 돌아도 공구는 23:55에 끝난 것이다.
     *                         판정 종결은 판정 시각이다
     * @param fulfillmentDueAt 종료(ENDED)만 이행 확인 기한이 있다 — 중단은 null
     */
    public void applyTerminated(GroupBuyStatus to, LocalDateTime endedAt, GroupBuyCloseType closeType,
                                Long closingChangeRequestId, Long closingAdminSuspensionId,
                                LocalDateTime fulfillmentDueAt) {
        this.status = to;
        this.endedAt = endedAt;
        this.closeType = closeType;
        this.closingChangeRequestId = closingChangeRequestId;
        this.closingAdminSuspensionId = closingAdminSuspensionId;
        this.fulfillmentDueAt = fulfillmentDueAt;
    }

    /** 직권 중단 통지(IN_PROGRESS → SUSPENSION_SCHEDULED)와 철회(역방향) — 기간·판매는 그대로다. */
    public void applyStatus(GroupBuyStatus to) {
        this.status = to;
    }

    /** 연장 수락 — 조건부 UPDATE({@code extendEndAt})가 통과한 뒤 같은 값을 엔티티에 반영한다. 시작일은 불변이다. */
    public void applyExtended(LocalDateTime afterEndAt) {
        this.endAt = afterEndAt;
    }

    public boolean isOwnedByCreator(Long creatorId) {
        return creator != null && creator.getId().equals(creatorId);
    }

    public void applySettled(LocalDateTime transferredAt) {
        this.status = GroupBuyStatus.SETTLED;
        this.settledAt = transferredAt;
    }

    /** 연결·소통 통보 — 양측 동의 종결(32 설계 8-4). 미이행 확인 행은 고치지 않는다(제20조② 불가역). */
    public void applyFulfillmentAgreed(String note, LocalDateTime agreedAt) {
        this.fulfillmentAgreedAt = agreedAt;
        this.fulfillmentResolutionNote = note;
    }

    /** 정산 관리 통보 — 보류 해제. 합의가 먼저여야 한다는 가드는 호출자가 건다(제20조⑤). */
    public void applyFulfillmentHoldReleased(LocalDateTime releasedAt) {
        this.fulfillmentResolvedAt = releasedAt;
    }
}

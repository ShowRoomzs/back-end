package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.service.ShipDuePolicy;
import showroomz.domain.order.type.DeliveredSource;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.SellerCancelReason;
import showroomz.domain.order.type.TrackingAlert;

import java.time.LocalDateTime;

/**
 * 주문의 배송 그룹 — 키는 <b>공구</b>다(결제 계획서 3-1). 배송비·마감일·발송 시점이 공구마다 다르고, 정산·판매 관리가
 * 「이 공구 배송비가 얼마였나」를 나중에 복원해야 한다.
 *
 * <p>{@code group_buy_id}는 기존 리뷰용 시드 행 백필(마켓별 한 그룹)에서만 NULL이고 신규 주문은 항상 채운다.
 * NULL은 UK 중복을 허용하므로 백필 행에는 {@code (order_id, group_buy_id)} UK가 의미 없다.
 * 쇼룸명·공구번호는 스냅샷이다 — 쇼룸명 변경·마켓 비활성 뒤에도 주문 상세가 그대로다.
 *
 * <p><b>이 테이블이 파트너센터 주문 관리의 「하위주문」이다</b>(34 설계서 0-1 · 1-1). 이행 상태·송장·시각 컬럼은
 * 전부 {@code OrderDeliveryGroupRepository}의 조건부 UPDATE 로만 바뀐다 — 엔티티에 전이 메서드를 두지 않는다.
 * 발송기한·구매확정 예정 같은 파생값은 저장하지 않고, 배치가 판정한 사실({@code trackingAlert})만 저장한다(설계서 0-5).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "order_delivery_group",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_delivery_group_order_group_buy",
                columnNames = {"order_id", "group_buy_id"})
)
public class OrderDeliveryGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id")
    private GroupBuy groupBuy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    /** 이 그룹의 판매가 합. */
    @Column(name = "product_total", nullable = false)
    private Integer productTotal;

    /** 실제 부과 배송비 — 무료배송이면 0. */
    @Column(name = "delivery_fee", nullable = false)
    private Integer deliveryFee;

    /**
     * 주문 시점 기본 배송비 — 무료배송이어도 원래 값(앱 클레임 설계서 1-4). 반품 배송비 차감 · 재발송비의 기준이다.
     * 주문 생성 때 1회 적고 이후 바꾸지 않는다 — 마켓 설정이 바뀌어도 불변.
     */
    @Column(name = "base_delivery_fee", nullable = false)
    private Integer baseDeliveryFee;

    @Column(name = "free_shipping_applied", nullable = false)
    private boolean freeShippingApplied;

    @Column(name = "market_name", length = 100)
    private String marketName;

    @Column(name = "group_buy_number", length = 30)
    private String groupBuyNumber;

    // ── 이행 상태(34 설계서 1-1) — 쓰기는 리포지토리 조건부 UPDATE 로만 ─────────

    /** {@code {order_number}-NN} — PAID 전이 때 발급. */
    @Column(name = "sub_order_number", length = 40)
    private String subOrderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 30)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.PENDING;

    /**
     * 발송기한 = 공구 마감 시각 + {@link #shipDueBusinessDays}영업일(1009 기획 수정본 1절 · {@code ShipDuePolicy}).
     * <b>공구가 종결되기 전에는 NULL</b>이다 — 진행 중 결제 건은 마감 전까지 기한이 없다. 종결 순간 확정되고 이후 불변(귀책 판정값).
     */
    @Column(name = "ship_due_at")
    private LocalDateTime shipDueAt;

    /** 이 주문에 적용된 발송 기한 N(영업일) — 주문 생성 때 마켓 설정의 스냅샷. 브랜드가 뒤에 바꿔도 그대로다. */
    @Column(name = "ship_due_business_days", nullable = false)
    private Integer shipDueBusinessDays;

    @Column(name = "prepare_started_at")
    private LocalDateTime prepareStartedAt;

    @Column(name = "prepare_started_by")
    private Long prepareStartedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", length = 30)
    private DeliveryCarrier carrier;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    /** 송장 등록 확정 = 배송중 전환 = 발송기한 판정값. 송장 수정으로 바뀌지 않는다(3-2). */
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    /** 집화 시각 — 추적 배치가 본 첫 이벤트. 도착 예정일의 기준이자 택배사별 소요일 집계의 원천. 송장 수정 시 NULL 로 돌아간다. */
    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_alert", length = 30)
    private TrackingAlert trackingAlert;

    @Column(name = "last_tracking_at")
    private LocalDateTime lastTrackingAt;

    /** 반송 사유는 저장하지 않는다 — API 가 코드·시각만 준다(§34-6). */
    @Column(name = "return_detected_at")
    private LocalDateTime returnDetectedAt;

    @Column(name = "return_completed_at")
    private LocalDateTime returnCompletedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * 구매확정 기산점 재설정 — ① 교환 재발송 도착 시각(35 설계서 1-10 · §35-7 · 「교환 완료일부터 7일 새로 시작」)
     * ② 정지가 풀릴 때 정지했던 시간만큼 뒤로 민 기산점(1009 기획 수정본 4절 · 「철회되면 남은 일수부터 재개」).
     * {@code deliveredAt}을 덮지 않는다 — 최초 배송완료 시각은 사실이다. 예정 = {@link #confirmBaseAt()} + N일.
     */
    @Column(name = "confirm_restart_at")
    private LocalDateTime confirmRestartAt;

    /**
     * 구매확정 타이머 정지 시각 — 반품·교환이 접수되는 순간 멈추고(요청 시점부터), 진행 중 클레임이 없어지면 정지한 시간만큼
     * 기산점을 밀고 NULL 로 돌아간다(1009 기획 수정본 4-2). 정지 중에는 배치가 확정하지 않는다.
     */
    @Column(name = "confirm_paused_at")
    private LocalDateTime confirmPausedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivered_source", length = 16)
    private DeliveredSource deliveredSource;

    @Column(name = "delivered_by")
    private Long deliveredBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_type", length = 30)
    private OrderCancelType cancelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason_code", length = 30)
    private SellerCancelReason cancelReasonCode;

    /** 소비자에게 그대로 전달되는 설명(약관 제18조②). */
    @Column(name = "cancel_reason_detail", length = 300)
    private String cancelReasonDetail;

    /**
     * 발송 기한 경과 자동 알림 횟수(1009 기획 수정본 8-4 · 결정 「독촉 → 자동 알림」) — 영업일마다 한 번 브랜드에 자동 알림이
     * 나가고 여기 센다. 발송은 알림 모듈이다. 3회 무응답이면 어드민 대행(송장 대행 · 직권 취소)이 열린다.
     */
    @Column(name = "overdue_notice_count", nullable = false)
    private int overdueNoticeCount;

    @Column(name = "last_overdue_notice_at")
    private LocalDateTime lastOverdueNoticeAt;

    /** 취소 당시 이행 상태 — 신규 탭 직권 취소 허용 미결(§34-13 #1)의 데이터 분리(설계서 0-7). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_cancel", length = 30)
    private FulfillmentStatus statusAtCancel;

    @Builder
    public OrderDeliveryGroup(Order order, GroupBuy groupBuy, Market market, Integer productTotal, Integer deliveryFee,
                              Integer baseDeliveryFee, boolean freeShippingApplied, String marketName,
                              String groupBuyNumber, Integer shipDueBusinessDays) {
        this.order = order;
        this.groupBuy = groupBuy;
        this.market = market;
        this.productTotal = productTotal;
        this.deliveryFee = deliveryFee;
        // 지정하지 않으면 부과액 — 유료배송 그룹은 둘이 같다.
        this.baseDeliveryFee = baseDeliveryFee != null ? baseDeliveryFee : deliveryFee;
        this.freeShippingApplied = freeShippingApplied;
        this.marketName = marketName;
        this.groupBuyNumber = groupBuyNumber;
        // 지정하지 않으면 마켓 설정에서 — 주문 시점 값으로 고정한다.
        this.shipDueBusinessDays = shipDueBusinessDays != null ? shipDueBusinessDays
                : ShipDuePolicy.businessDaysOf(market);
    }

    /**
     * 구매확정 N일의 기준 시각 — 교환 재발송이 도착했으면 그 시각, 아니면 배송완료 시각(35 설계서 3-6).
     * 배치의 WHERE({@code COALESCE(confirm_restart_at, delivered_at)})와 화면의 예정일 계산이 같은 값을 써야 한다.
     */
    public LocalDateTime confirmBaseAt() {
        return confirmRestartAt != null ? confirmRestartAt : deliveredAt;
    }

    /**
     * 구매확정까지 남은 일수(올림) — 정지 중이면 정지 시점 기준(멈춘 채 그대로), 아니면 지금 기준. 배송완료 전이면 null.
     * 파트너 클레임 상세 「구매확정 타이머 · 정지 · 남은 4일」 · 소비자 반품·교환 상세가 같은 값을 쓴다.
     */
    public Long confirmRemainingDays(int confirmDays, LocalDateTime now) {
        LocalDateTime base = confirmBaseAt();
        if (base == null) {
            return null;
        }
        LocalDateTime reference = confirmPausedAt != null ? confirmPausedAt : now;
        long minutes = java.time.Duration.between(reference, base.plusDays(confirmDays)).toMinutes();
        return Math.max(0, (long) Math.ceil(minutes / (24.0 * 60)));
    }

    public Long getGroupBuyId() {
        return groupBuy == null ? null : groupBuy.getId();
    }

    public Long getMarketId() {
        return market == null ? null : market.getId();
    }
}

package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.market.entity.Market;
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

    /** 발송기한 — PAID 전이 때 {@code paid_at + market.shipping_lead_days} 스냅샷. 이후 마켓 설정이 바뀌어도 불변(귀책 판정값). */
    @Column(name = "ship_due_at")
    private LocalDateTime shipDueAt;

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

    /** 취소 당시 이행 상태 — 신규 탭 직권 취소 허용 미결(§34-13 #1)의 데이터 분리(설계서 0-7). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_cancel", length = 30)
    private FulfillmentStatus statusAtCancel;

    @Builder
    public OrderDeliveryGroup(Order order, GroupBuy groupBuy, Market market, Integer productTotal, Integer deliveryFee,
                              boolean freeShippingApplied, String marketName, String groupBuyNumber) {
        this.order = order;
        this.groupBuy = groupBuy;
        this.market = market;
        this.productTotal = productTotal;
        this.deliveryFee = deliveryFee;
        this.freeShippingApplied = freeShippingApplied;
        this.marketName = marketName;
        this.groupBuyNumber = groupBuyNumber;
    }

    public Long getGroupBuyId() {
        return groupBuy == null ? null : groupBuy.getId();
    }

    public Long getMarketId() {
        return market == null ? null : market.getId();
    }
}

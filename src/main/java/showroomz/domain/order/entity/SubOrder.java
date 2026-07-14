package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.type.DeliveryStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 하위주문 — 공구별 배송·정산 단위.
 * 결제는 주문(Order) 한 번이지만, 통합 결제해도 공구(브랜드)별로 분리 저장한다
 * (결제 단위 ≠ 정산 단위 — 거래 플로우 §10).
 * <ul>
 *   <li>결제완료 → 브랜드 "준비 시작"(수동) → 상품준비중 → 송장 등록 → 배송중 → 배송완료</li>
 *   <li>구매확정 = 배송완료 + 7일 자동(반품 없을 시) — 정산 대상.
 *       기산점: 택배사 배송완료 처리일을 수령일로 추정</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "sub_order")
public class SubOrder extends BaseTimeEntity {

    /** 구매확정 자동 처리 기준일 — 배송완료 후 7일 */
    public static final int PURCHASE_CONFIRM_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sub_order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    /** 배송·정산 상대 브랜드 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PAID;

    /** 브랜드가 입력하는 송장번호 */
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** 구매확정 예정일 = 배송완료 + 7일 */
    @Column(name = "confirm_due_date")
    private LocalDate confirmDueDate;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Builder
    public SubOrder(Order order, GroupBuy groupBuy, Seller seller) {
        this.order = order;
        this.groupBuy = groupBuy;
        this.seller = seller;
        this.deliveryStatus = DeliveryStatus.PAID;
    }

    /** 브랜드 "준비 시작" — 이 전까지가 소비자 단순 취소 구간 */
    public void startPreparing() {
        this.deliveryStatus = DeliveryStatus.PREPARING;
    }

    /** 송장 등록·발송 → 배송중 */
    public void ship(String trackingNumber) {
        this.trackingNumber = trackingNumber;
        this.deliveryStatus = DeliveryStatus.SHIPPING;
    }

    /** 배송 도착 → 배송완료, 구매확정 예정일 산정 */
    public void completeDelivery() {
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
        this.confirmDueDate = this.deliveredAt.toLocalDate().plusDays(PURCHASE_CONFIRM_DAYS);
    }

    /** 배송완료 후 7일 자동(반품 없음) → 구매확정. 정산 대상 */
    public void confirmPurchase() {
        this.deliveryStatus = DeliveryStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /** 결제완료·준비 착수 전 — 소비자 단순 취소(PG 자동 취소) 가능 구간 */
    public boolean isSimpleCancelable() {
        return deliveryStatus == DeliveryStatus.PAID;
    }

    public boolean isConfirmed() {
        return deliveryStatus == DeliveryStatus.CONFIRMED;
    }
}

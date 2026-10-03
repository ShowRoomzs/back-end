package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 취소 요청 대상 항목 — 취소·반품·교환 단위는 SKU(주문 항목)다(§34-0 단위 3층).
 * 수량·환불 예정액은 요청 시점 스냅샷이다 — 수량 쪼개기 기획은 없다(항목 전량).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_cancel_request_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_cancel_request_item",
                columnNames = {"cancel_request_id", "order_product_id"}))
public class OrderCancelRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancel_request_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancel_request_id", nullable = false)
    private OrderCancelRequest cancelRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_product_id", nullable = false)
    private OrderProduct orderProduct;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 요청 시점 환불 예정액(단가×수량) — 상세 C11 「취소 요청분 별 행」. */
    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Builder
    public OrderCancelRequestItem(OrderCancelRequest cancelRequest, OrderProduct orderProduct, Integer quantity,
                                  Integer refundAmount) {
        this.cancelRequest = cancelRequest;
        this.orderProduct = orderProduct;
        this.quantity = quantity;
        this.refundAmount = refundAmount;
    }
}

package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.review.entity.Review;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 주문 상품 한 줄 — 상품명·옵션명·단가는 <b>스냅샷</b>이다. {@code price}는 공구가(판매가), {@code regularPrice}는 정가다.
 *
 * <p>{@code group_buy_id}는 배송 그룹에도 있지만 여기 비정규화해 둔다 — 판매 집계({@code OrderGroupBuySalesReader})가
 * 조인 없이 돈다. {@code cart_id}는 「어느 장바구니 행에서 왔나」다(FK 없음) — PAID 전이 때 그 행을 지우는 근거고,
 * 바로 구매는 NULL이다. 컬럼 삭제·개명은 하지 않는다 — V47 뷰 {@code market_inquiry_view}가 이 테이블을 조인한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_product")
public class OrderProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_group_id")
    private OrderDeliveryGroup deliveryGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id")
    private GroupBuy groupBuy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "option_name", length = 255)
    private String optionName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 판매가(공구가 + 옵션가) 스냅샷 — 주문 단가. */
    @Column(name = "price", nullable = false)
    private Integer price;

    /** 정가 스냅샷 — 화면의 취소선·할인율. */
    @Column(name = "regular_price")
    private Integer regularPrice;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderProductStatus status = OrderProductStatus.PENDING;

    @Column(name = "cart_id")
    private Long cartId;

    // ── 항목 취소 메타(34 설계서 1-6) — 상태 4종은 늘리지 않는다. 취소의 사실만 더한다 ──

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 그룹과 같은 3종 — 부분 취소(일부 항목만)는 항목에만 남는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_type", length = 30)
    private showroomz.domain.order.type.OrderCancelType cancelType;

    @OneToOne(mappedBy = "orderProduct", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Review review;

    @Builder
    public OrderProduct(Order order, OrderDeliveryGroup deliveryGroup, GroupBuy groupBuy, ProductVariant variant,
                        String productName, String optionName, Integer quantity, Integer price, Integer regularPrice,
                        String imageUrl, LocalDateTime orderDate, OrderProductStatus status, Long cartId) {
        this.order = order;
        this.deliveryGroup = deliveryGroup;
        this.groupBuy = groupBuy;
        this.variant = variant;
        this.productName = productName;
        this.optionName = optionName;
        this.quantity = quantity;
        this.price = price;
        this.regularPrice = regularPrice;
        this.imageUrl = imageUrl;
        this.orderDate = orderDate;
        this.status = status != null ? status : OrderProductStatus.PENDING;
        this.cartId = cartId;
    }

    public boolean isPurchaseConfirmed() {
        return status == OrderProductStatus.PURCHASE_CONFIRMED;
    }

    public boolean hasReview() {
        return review != null;
    }

    public Optional<Review> getReviewOptional() {
        return Optional.ofNullable(review);
    }

    public Long getGroupBuyId() {
        return groupBuy == null ? null : groupBuy.getId();
    }

    public Long getVariantId() {
        return variant == null ? null : variant.getVariantId();
    }
}

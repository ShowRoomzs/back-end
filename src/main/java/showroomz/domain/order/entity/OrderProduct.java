package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.review.entity.Review;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 주문 항목 — SKU 단위(취소·반품 단위).
 * 어느 공구 게시물(쇼룸·공구)을 통해 구매했는지와 담을 때의 공구가 스냅샷(price)을 보관한다.
 * 다른 옵션(SKU)은 별개 항목이라 각각 독립 취소·환불 가능.
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

    /** 소속 하위주문(공구별 배송·정산 단위) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_order_id")
    private SubOrder subOrder;

    /** 구매 경로 — 어느 공구 게시물(쇼룸+공구)을 통해 구매했는지 (공구가 적용 근거) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_post_id")
    private GroupBuyPost groupBuyPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "option_name", length = 255)
    private String optionName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderProductStatus status = OrderProductStatus.PENDING;

    @OneToOne(mappedBy = "orderProduct", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Review review;

    @Builder
    public OrderProduct(Order order, SubOrder subOrder, GroupBuyPost groupBuyPost,
                        ProductVariant variant, String productName, String optionName,
                        Integer quantity, Integer price, String imageUrl, LocalDateTime orderDate,
                        OrderProductStatus status) {
        this.order = order;
        this.subOrder = subOrder;
        this.groupBuyPost = groupBuyPost;
        this.variant = variant;
        this.productName = productName;
        this.optionName = optionName;
        this.quantity = quantity;
        this.price = price;
        this.imageUrl = imageUrl;
        this.orderDate = orderDate;
        this.status = status != null ? status : OrderProductStatus.PENDING;
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

}

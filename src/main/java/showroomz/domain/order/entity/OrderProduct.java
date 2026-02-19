package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.review.entity.Review;

import java.time.LocalDateTime;
import java.util.Optional;

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
    public OrderProduct(Order order, ProductVariant variant, String productName, String optionName,
                        Integer quantity, Integer price, String imageUrl, LocalDateTime orderDate,
                        OrderProductStatus status) {
        this.order = order;
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

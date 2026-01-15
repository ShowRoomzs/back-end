package showroomz.domain.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.category.entity.Category;
import showroomz.domain.market.entity.Market;
import showroomz.domain.product.type.ProductGender;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "seller_product_code", length = 100)
    private String sellerProductCode;

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl;

    @Column(name = "regular_price", nullable = false)
    private Integer regularPrice;

    @Column(name = "sale_price", nullable = false)
    private Integer salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private ProductGender gender;

    @Column(name = "purchase_price")
    private Integer purchasePrice;

    @Column(name = "is_display", nullable = false)
    private Boolean isDisplay = true;

    @Column(name = "is_out_of_stock_forced", nullable = false)
    private Boolean isOutOfStockForced = false;

    @Column(name = "is_recommended", nullable = false)
    private Boolean isRecommended = false;

    @Column(name = "product_notice", columnDefinition = "json")
    private String productNotice;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "tags", columnDefinition = "json")
    private String tags;

    @Column(name = "delivery_type", length = 100)
    private String deliveryType;

    @Column(name = "delivery_fee")
    private Integer deliveryFee;

    @Column(name = "delivery_free_threshold")
    private Integer deliveryFreeThreshold;

    @Column(name = "delivery_estimated_days")
    private Integer deliveryEstimatedDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 상품 번호 (SRZ-YYYYMMDD-XXX 형식)
    @Column(name = "product_number", unique = true, length = 50)
    private String productNumber;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> productImages = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductOptionGroup> optionGroups = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}


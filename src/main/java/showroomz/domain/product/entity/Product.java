package showroomz.domain.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.category.entity.Category;
import showroomz.domain.market.entity.Market;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.domain.product.type.ProductInspectionStatus;
import showroomz.domain.product.type.ProductRejectReasonType;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "display_status", nullable = false, length = 32)
    private ProductDisplayStatus displayStatus = ProductDisplayStatus.DISPLAY;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_display_status", length = 32)
    private ProductDisplayStatus previousDisplayStatus;

    @Column(name = "is_out_of_stock_forced", nullable = false)
    private Boolean isOutOfStockForced = false;

    @Column(name = "is_recommended", nullable = false)
    private Boolean isRecommended = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_status", nullable = false, length = 32)
    private ProductInspectionStatus inspectionStatus = ProductInspectionStatus.WAITING;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason_type", length = 64)
    private ProductRejectReasonType rejectReasonType;

    @Column(name = "reject_detail", length = 500)
    private String rejectDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "hide_reason_type", length = 64)
    private ProductHideReasonType hideReasonType;

    @Column(name = "hide_detail", length = 500)
    private String hideDetail;

    @Column(name = "product_notice", columnDefinition = "json")
    private String productNotice;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "modified_at")
    private Instant modifiedAt;

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
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (modifiedAt == null) {
            modifiedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedAt = Instant.now();
    }
}


package showroomz.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.domain.product.type.ProductProcessingHistoryType;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_processing_history")
public class ProductProcessingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "history_type", nullable = false, length = 32)
    private ProductProcessingHistoryType historyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_display_status", length = 32)
    private ProductDisplayStatus previousDisplayStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_display_status", length = 32)
    private ProductDisplayStatus newDisplayStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "hide_reason_type", length = 64)
    private ProductHideReasonType hideReasonType;

    @Column(name = "hide_detail", length = 500)
    private String hideDetail;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    /** 처리 운영자 seller_id (어드민 처리 시에만) */
    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProductProcessingHistory(
            Product product,
            ProductProcessingHistoryType historyType,
            ProductDisplayStatus previousDisplayStatus,
            ProductDisplayStatus newDisplayStatus,
            ProductHideReasonType hideReasonType,
            String hideDetail,
            Integer stockQuantity,
            Long processedBy
    ) {
        this.product = product;
        this.historyType = historyType;
        this.previousDisplayStatus = previousDisplayStatus;
        this.newDisplayStatus = newDisplayStatus;
        this.hideReasonType = hideReasonType;
        this.hideDetail = hideDetail;
        this.stockQuantity = stockQuantity;
        this.processedBy = processedBy;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

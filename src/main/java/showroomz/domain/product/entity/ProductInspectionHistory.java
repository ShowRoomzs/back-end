package showroomz.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.product.type.ProductInspectionStatus;
import showroomz.domain.product.type.ProductRejectReasonType;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_inspection_history")
public class ProductInspectionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private ProductInspectionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private ProductInspectionStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason_type", length = 64)
    private ProductRejectReasonType rejectReasonType;

    @Column(name = "reject_detail", length = 500)
    private String rejectDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProductInspectionHistory(
            Product product,
            ProductInspectionStatus previousStatus,
            ProductInspectionStatus newStatus,
            ProductRejectReasonType rejectReasonType,
            String rejectDetail
    ) {
        this.product = product;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.rejectReasonType = rejectReasonType;
        this.rejectDetail = rejectDetail;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

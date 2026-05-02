package showroomz.domain.productannouncement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.market.entity.Market;
import showroomz.domain.product.entity.Product;
import showroomz.domain.productannouncement.type.ExposureType;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_announcement")
public class ProductAnnouncement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "exposure_type", nullable = false, length = 20)
    private ExposureType exposureType;

    @Column(name = "is_display_period_set", nullable = false)
    private boolean displayPeriodSet;

    @Column(name = "display_start_date")
    private LocalDateTime displayStartDate;

    @Column(name = "display_end_date")
    private LocalDateTime displayEndDate;

    @Column(name = "is_popup", nullable = false)
    private boolean popup;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_status", nullable = false, length = 20)
    private ProductAnnouncementDisplayStatus displayStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @OneToMany(mappedBy = "announcement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAnnouncementTarget> targets = new ArrayList<>();

    @Builder
    public ProductAnnouncement(
            Market market,
            String category,
            String title,
            String content,
            ExposureType exposureType,
            boolean displayPeriodSet,
            LocalDateTime displayStartDate,
            LocalDateTime displayEndDate,
            boolean popup,
            ProductAnnouncementDisplayStatus displayStatus
    ) {
        this.market = market;
        this.category = category;
        this.title = title;
        this.content = content;
        this.exposureType = exposureType;
        this.displayPeriodSet = displayPeriodSet;
        this.displayStartDate = displayStartDate;
        this.displayEndDate = displayEndDate;
        this.popup = popup;
        this.displayStatus = displayStatus;
    }

    public void update(
            String category,
            String title,
            String content,
            ExposureType exposureType,
            boolean displayPeriodSet,
            LocalDateTime displayStartDate,
            LocalDateTime displayEndDate,
            boolean popup,
            ProductAnnouncementDisplayStatus displayStatus
    ) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.exposureType = exposureType;
        this.displayPeriodSet = displayPeriodSet;
        this.displayStartDate = displayStartDate;
        this.displayEndDate = displayEndDate;
        this.popup = popup;
        this.displayStatus = displayStatus;
    }

    public void replaceTargetsFromProducts(List<Product> products) {
        this.targets.clear();
        for (Product p : products) {
            this.targets.add(new ProductAnnouncementTarget(this, p));
        }
    }
}

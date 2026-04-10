package showroomz.domain.productannouncement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.product.entity.Product;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "product_announcement_target",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_announcement_target",
                columnNames = {"announcement_id", "product_id"}
        )
)
public class ProductAnnouncementTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private ProductAnnouncement announcement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    public ProductAnnouncementTarget(ProductAnnouncement announcement, Product product) {
        this.announcement = announcement;
        this.product = product;
    }
}

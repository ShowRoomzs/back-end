package showroomz.domain.post.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.market.entity.Market;
import showroomz.domain.product.entity.Product;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post")
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ElementCollection
    @CollectionTable(name = "post_images", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "image_url", length = 512)
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    // 위시리스트 카운트 컬럼
    @Column(name = "wishlist_count", nullable = false)
    private Long wishlistCount = 0L;

    @Column(name = "is_display", nullable = false)
    private Boolean isDisplay = true;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostProduct> postProducts = new ArrayList<>();

    public Post(Market market, String title, String content, List<String> imageUrls) {
        this.market = market;
        this.title = title;
        this.content = content;
        if (imageUrls != null) {
            this.imageUrls.addAll(imageUrls);
        }
        this.viewCount = 0L;
        this.wishlistCount = 0L;
        this.isDisplay = true;
    }

    public void update(String title, String content, List<String> imageUrls, Boolean isDisplay) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (imageUrls != null) {
            this.imageUrls.clear();
            this.imageUrls.addAll(imageUrls);
        }
        if (isDisplay != null) {
            this.isDisplay = isDisplay;
        }
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementWishlistCount() {
        this.wishlistCount++;
    }

    public void decrementWishlistCount() {
        if (this.wishlistCount > 0) {
            this.wishlistCount--;
        }
    }

    public void updateDisplayStatus(Boolean isDisplay) {
        this.isDisplay = isDisplay;
    }

    public void addProduct(Product product) {
        this.postProducts.add(new PostProduct(this, product));
    }

    public void clearProducts() {
        this.postProducts.clear();
    }
}

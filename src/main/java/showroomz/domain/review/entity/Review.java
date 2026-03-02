package showroomz.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderProduct;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "review")
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_product_id", nullable = false, unique = true)
    private OrderProduct orderProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_promotion_agreed", nullable = false)
    private Boolean isPromotionAgreed = false;

    @Column(name = "is_personal_info_agreed", nullable = false)
    private Boolean isPersonalInfoAgreed = false;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewLike> likes = new ArrayList<>();

    @Builder
    public Review(OrderProduct orderProduct, Users user, Integer rating, String content,
                  Boolean isPromotionAgreed, Boolean isPersonalInfoAgreed) {
        this.orderProduct = orderProduct;
        this.user = user;
        this.rating = rating;
        this.content = content;
        this.isPromotionAgreed = isPromotionAgreed != null ? isPromotionAgreed : false;
        this.isPersonalInfoAgreed = isPersonalInfoAgreed != null ? isPersonalInfoAgreed : false;
    }

    public void update(Integer rating, String content) {
        if (rating != null) {
            this.rating = rating;
        }
        if (content != null) {
            this.content = content;
        }
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void addImages(List<ReviewImage> images) {
        this.images.addAll(images);
    }

    public void replaceImages(List<ReviewImage> newImages) {
        this.images.clear();
        this.images.addAll(newImages);
    }

    public List<String> getImageUrlsOrdered() {
        return images.stream()
                .sorted(Comparator.comparingInt(ReviewImage::getSequence))
                .map(ReviewImage::getUrl)
                .toList();
    }
}

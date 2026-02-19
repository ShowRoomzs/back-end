package showroomz.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "review_image")
public class ReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "sequence", nullable = false)
    private Integer sequence = 0;

    @Builder
    public ReviewImage(Review review, String url, Integer sequence) {
        this.review = review;
        this.url = url;
        this.sequence = sequence != null ? sequence : 0;
    }
}

package showroomz.domain.post.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.market.entity.Market;

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

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "is_display", nullable = false)
    private Boolean isDisplay = true;

    public Post(Market market, String title, String content, String imageUrl) {
        this.market = market;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.viewCount = 0L;
        this.isDisplay = true;
    }

    public void update(String title, String content, String imageUrl, Boolean isDisplay) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (isDisplay != null) {
            this.isDisplay = isDisplay;
        }
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void updateDisplayStatus(Boolean isDisplay) {
        this.isDisplay = isDisplay;
    }
}

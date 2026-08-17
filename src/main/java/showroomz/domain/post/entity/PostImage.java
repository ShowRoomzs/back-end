package showroomz.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 게시물 사진 한 장 (§24-2).
 *
 * <p>예전 {@code @ElementCollection post_images}를 승격한 테이블이다. 컬렉션 테이블에는
 * <b>순서가 없었다</b> — §24-2가 "대표 사진 = 첫 번째", "셀 드래그로 순서 변경"을 요구하므로
 * 순서를 값으로 들고 있어야 한다.
 *
 * <p>{@code originalUrl}을 따로 두는 이유 — §24-6이 반려 통지 후 유예 기간 동안
 * <b>본인만 사진 원본을 내려받을 수 있게</b> 하라고 요구한다. 크롭본만 갖고 있으면 이 요구를
 * 만족할 수 없다. FE는 크롭 결과와 원본을 둘 다 올리고, 저장 시 두 URL을 함께 보낸다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "post_image",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_image_order", columnNames = {"post_id", "sort_order"})
        }
)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** 0 = 대표 사진. 셀 드래그 순서 변경의 결과가 이 값이다 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** 표시용 — 크롭이 반영된 이미지 */
    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    /** 원본 — 유예 기간 내려받기·재크롭용. 파기 시점까지 보존한다 (§24-6) */
    @Column(name = "original_url", nullable = false, length = 512)
    private String originalUrl;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "file_size")
    private Integer fileSize;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostImage(String imageUrl, String originalUrl, Integer width, Integer height, Integer fileSize) {
        this.imageUrl = imageUrl;
        this.originalUrl = originalUrl;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
    }

    /** 게시물에 붙으면서 순서를 부여받는다 — {@link Post#replaceImages(java.util.List)}만 호출한다 */
    void attachTo(Post post, int sortOrder) {
        this.post = post;
        this.sortOrder = sortOrder;
    }

    public boolean isRepresentative() {
        return this.sortOrder != null && this.sortOrder == 0;
    }
}

package showroomz.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 반려 유예 중 작성자의 사진 원본 내려받기 기록 (§54-6 「내려받은 기록을 삭제 경위에 남긴다」).
 *
 * <p>C8 삭제 경위의 「작성자 내려받기 1회(08.10 21:14)」가 이 행이고, 「내 사진을 돌려 달라」 문의의 답이 된다.
 * <b>URL 발급 기록</b>이지 실제 전송 성공 기록은 아니다 — S3 URL을 받고 안 받을 수 있다.
 *
 * <p>{@code postId}에 FK가 없다. 게시물이 보관 만료로 파기된 뒤에도 경위는 남아야 한다
 * ({@link PostNotificationLog}와 같은 규칙). 파기 배치도 이 테이블을 건드리지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post_original_download",
        indexes = {
                @Index(name = "idx_post_original_download_post", columnList = "post_id, downloaded_at")
        }
)
public class PostOriginalDownload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "download_id")
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    /** 어느 반려의 유예 중 내려받았는지 */
    @Column(name = "appeal_id", updatable = false)
    private Long appealId;

    @Column(name = "image_count", nullable = false, updatable = false)
    private Integer imageCount;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private LocalDateTime downloadedAt;

    public PostOriginalDownload(Long postId, Long creatorId, Long appealId, int imageCount, LocalDateTime downloadedAt) {
        this.postId = postId;
        this.creatorId = creatorId;
        this.appealId = appealId;
        this.imageCount = imageCount;
        this.downloadedAt = downloadedAt;
    }
}

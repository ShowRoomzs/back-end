package showroomz.domain.post.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 쇼룸 게시물의 <b>공통 뿌리</b> (§24).
 *
 * <p>제목이 없다. 일반 게시물은 제목을 받지 않고(§24-3), 공구 게시물의 제목은 확장 테이블
 * {@code group_buy_post}에서 {@code NOT NULL}로 만든다. 뿌리에 nullable로 두면
 * "일반인데 제목이 들어온" 데이터를 DB가 막지 못한다.
 *
 * <p>상품 연결도 없다. 상품이 붙는 것은 공구 게시물이고, 그때 {@code group_buy_post_product}로
 * 따로 만든다. 예전 구현이 한 엔티티에 "이미지 모드 / 상품 모드"를 욱여넣고
 * {@code if (hasImage && hasProducts) throw}로 방어하던 자리는 {@link PostType} 판별자로 승격됐다.
 *
 * <p>클래스 이름을 {@code Post} 그대로 두는 이유 — 쇼룸 관리(§22-4)의 인기 콘텐츠·팔로잉 정렬이
 * 이 타입을 직접 참조한다. 테이블은 재생성하되 이름은 유지해 그쪽이 함께 무너지지 않게 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post",
        indexes = {
                @Index(name = "idx_post_creator_status_published", columnList = "creator_id, status, published_at"),
                @Index(name = "idx_post_purge", columnList = "purge_at")
        }
)
public class Post extends BaseTimeEntity {

    /** §24-2 게시물당 최대 장수. DB 제약으로 표현할 수 없어 서비스에서 막는다 */
    public static final int MAX_IMAGE_COUNT = 20;

    /** §24-3 본문 최대 길이 */
    public static final int MAX_CONTENT_LENGTH = 2000;

    /** §24-2 허용 비율 하한 — 4:5(세로) */
    public static final BigDecimal MIN_ASPECT_RATIO = new BigDecimal("0.8000");

    /** §24-2 허용 비율 상한 — 1.91:1(가로) */
    public static final BigDecimal MAX_ASPECT_RATIO = new BigDecimal("1.9100");

    /** aspect_ratio DECIMAL(6,4) */
    private static final int ASPECT_RATIO_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private Creator creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20, updatable = false)
    private PostType postType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status;

    /** 본문 — 선택이다. 사진만 있는 게시물을 허용한다 (§24-3) */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 첫 사진 기준 가로/세로 비율 — 나머지 사진 전부에 같은 값이 적용된다 (§24-2 게시물 단위 통일).
     *
     * <p>소비자 피드가 <b>고정 높이 카드로 구현되면 안 되기 때문에</b> 서버가 이 값을 내려준다.
     * 크롭은 FE가 하고 서버는 결과가 허용 범위 안인지 검증만 한다.
     */
    @Column(name = "aspect_ratio", precision = 6, scale = 4)
    private BigDecimal aspectRatio;

    /** 노출 — 기획 용어를 그대로 쓴다(구 view_count). 인사이트는 카운터가 아니라 원천 로그로 계산한다 */
    @Column(name = "impression_count", nullable = false)
    private Long impressionCount = 0L;

    /** 좋아요 — 구 wishlist_count */
    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    /** 작성중 → 게시중 전환 시각. <b>재게시해도 갱신하지 않는다</b> — 게시일은 처음 세상에 나온 때다 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delete_reason", length = 32)
    private PostDeleteReason deleteReason;

    /** 비공개 보관 만료 — 이후 파기 배치가 행·이미지·S3 객체를 물리 삭제한다 (§24-6) */
    @Column(name = "purge_at")
    private LocalDateTime purgeAt;

    /**
     * 사진 — {@code sort_order} 0번이 대표 사진이다 (§24-2).
     *
     * <p>순서 재배열은 부분 UPDATE가 아니라 <b>전체 교체</b>로 처리한다. {@code (post_id, sort_order)}
     * 유니크 때문에 (1,2,3) → (2,1,3) 같은 중간 상태에서 충돌이 나는데, 장수가 20 이하라
     * 지우고 다시 넣는 비용이 무시할 만하다.
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PostImage> images = new ArrayList<>();

    private Post(Creator creator, PostType postType, PostStatus status, String content, BigDecimal aspectRatio) {
        this.creator = creator;
        this.postType = postType;
        this.status = status;
        this.content = content;
        this.aspectRatio = normalizeRatio(aspectRatio);
        this.impressionCount = 0L;
        this.likeCount = 0L;
    }

    /** 임시저장으로 시작한다 — 사진 1장 또는 본문 1자만 있으면 성립한다 (§24-3) */
    public static Post draft(Creator creator, String content, BigDecimal aspectRatio) {
        return new Post(creator, PostType.GENERAL, PostStatus.DRAFT, content, aspectRatio);
    }

    /** 곧바로 게시한다 — 사진이 최소 1장 있어야 한다 (§24-3) */
    public static Post published(Creator creator, String content, BigDecimal aspectRatio, LocalDateTime now) {
        Post post = new Post(creator, PostType.GENERAL, PostStatus.PUBLISHED, content, aspectRatio);
        post.publishedAt = now;
        return post;
    }

    /** 본문·비율 수정. 사진 교체는 {@link #replaceImages(List)}가 따로 맡는다 */
    public void updateContent(String content, BigDecimal aspectRatio) {
        this.content = content;
        this.aspectRatio = normalizeRatio(aspectRatio);
    }

    /**
     * 사진 전체 교체 — 순서 재배열·추가·삭제가 모두 이 한 경로로 들어온다.
     * 넘긴 목록의 인덱스가 곧 {@code sort_order}이고 0번이 대표 사진이 된다.
     */
    public void replaceImages(List<PostImage> newImages) {
        this.images.clear();
        if (newImages == null) {
            return;
        }
        for (int i = 0; i < newImages.size(); i++) {
            PostImage image = newImages.get(i);
            image.attachTo(this, i);
            this.images.add(image);
        }
    }

    /** 작성중 → 게시중. 게시 시각은 처음 한 번만 찍는다 */
    public void publish(LocalDateTime now) {
        this.status = PostStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    /** 운영자 조치 — 노출 중지 (§24-5) */
    public void suspend() {
        this.status = PostStatus.SUSPENDED;
    }

    /** 이의 신청 접수 — 이 상태에서만 삭제가 금지된다 (§24-5) */
    public void startReview() {
        this.status = PostStatus.UNDER_REVIEW;
    }

    /**
     * 이의 신청 승인 → 재게시.
     *
     * <p>좋아요·인사이트 복원 로직이 <b>따로 없다.</b> 중지 기간에도 카운터를 깎지 않고 로그를
     * 지우지 않으므로 상태만 되돌리면 그대로 복원된다 (§24-5).
     */
    public void republish() {
        this.status = PostStatus.PUBLISHED;
    }

    /**
     * 영구 삭제 — 인플루언서 기준의 삭제다 (§24-6).
     *
     * <p>행을 지우지 않는다. 목록·쇼룸·인사이트에서 사라지고 본인은 복구할 수 없되, 서버는
     * {@code purgeAt}까지 비공개로 보관하고 그동안은 운영자 콘솔에서만 조회된다.
     */
    public void softDelete(PostDeleteReason reason, LocalDateTime now, LocalDateTime purgeAt) {
        this.status = PostStatus.DELETED;
        this.deleteReason = reason;
        this.deletedAt = now;
        this.purgeAt = purgeAt;
    }

    public void increaseImpressionCount() {
        this.impressionCount++;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public boolean isOwnedBy(Long creatorId) {
        return creatorId != null && this.creator.getId().equals(creatorId);
    }

    public boolean isDeleted() {
        return this.status == PostStatus.DELETED;
    }

    public boolean isVisibleToConsumer() {
        return this.status.isVisibleToConsumer();
    }

    /** 대표 사진 — 첫 장이다. 쇼룸 격자 썸네일이자 게시물 비율의 기준 (§24-2) */
    public PostImage getRepresentativeImage() {
        return this.images.isEmpty() ? null : this.images.get(0);
    }

    public int getImageCount() {
        return this.images.size();
    }

    /** 저장 전 비율을 컬럼 스케일(DECIMAL(6,4))에 맞춘다 — 범위 검증은 서비스가 앞에서 끝낸다 */
    private static BigDecimal normalizeRatio(BigDecimal ratio) {
        return ratio == null ? null : ratio.setScale(ASPECT_RATIO_SCALE, RoundingMode.HALF_UP);
    }
}

package showroomz.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import showroomz.domain.post.type.PostAppealStatus;

import java.time.LocalDateTime;

/**
 * 이의 신청 1건 (§24-5).
 *
 * <p>게시물당 1회다. 이 규칙을 서비스 검증이 아니라 <b>유니크 제약</b>으로 강제한다 —
 * 서비스만 두면 동시 요청에서 뚫린다.
 *
 * <p>액션이 `이의 신청` 하나로 통일된 이유 — 반려 시 영구 삭제이므로 "고쳐서 다시"라는 경로가
 * 성립하지 않는다. 그래서 소명·재검토 요청 같은 중간 상태가 이 엔티티에 없다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post_appeal",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_appeal_post", columnNames = {"post_id"})
        }
)
public class PostAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appeal_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suspension_id", nullable = false, updatable = false)
    private PostSuspension suspension;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @Column(name = "content", nullable = false, length = 1000, updatable = false)
    private String content;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostAppealStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    /** 반려 시 — 원본 내려받기 유예 만료 시각 (§24-6) */
    @Column(name = "grace_until")
    private LocalDateTime graceUntil;

    public PostAppeal(PostSuspension suspension, Post post, String content, LocalDateTime submittedAt) {
        this.suspension = suspension;
        this.post = post;
        this.content = content;
        this.submittedAt = submittedAt;
        this.status = PostAppealStatus.PENDING;
    }

    /** 승인 — 게시물은 재게시되고 좋아요·인사이트는 그대로 복원된다 (§24-5) */
    public void approve(Long reviewerId, String comment, LocalDateTime now) {
        this.status = PostAppealStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewComment = comment;
        this.reviewedAt = now;
    }

    /**
     * 반려 — 영구 삭제로 이어진다.
     *
     * <p>{@code graceUntil}까지는 본인이 사진 원본을 내려받을 수 있다. 통지 직후 원본까지 지우면
     * 인플루언서가 자기 콘텐츠 원본을 잃고, 그 상황이 분쟁으로 이어진다 (§24-6).
     */
    public void reject(Long reviewerId, String comment, LocalDateTime now, LocalDateTime graceUntil) {
        this.status = PostAppealStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewComment = comment;
        this.reviewedAt = now;
        this.graceUntil = graceUntil;
    }

    public boolean isPending() {
        return this.status == PostAppealStatus.PENDING;
    }

    /** 원본 내려받기 유예 안 — 반려된 건에만 값이 있다 */
    public boolean isWithinGracePeriod(LocalDateTime now) {
        return this.graceUntil != null && !now.isAfter(this.graceUntil);
    }
}

package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.post.entity.Post;

import java.time.LocalDateTime;

/**
 * 공구 게시물 — 공통 뿌리 {@code post} + 1:1 확장(설계서 1-8). 본문은 뿌리의 {@code post.content}다.
 *
 * <p>게시물 8종 상태는 저장하지 않는다({@code GroupBuyPostStatus.of}). 여기에는 게시물이 스스로 가진 사실
 * — 작성·제출·심사·숨김 — 만 있다. 쓰기(작성·제출·심사·숨김)는 스튜디오·어드민 설계 소관이다.
 *
 * <p>숨김을 {@code post.status = SUSPENDED}로 표현하지 않는다 — 일반 게시물의 SUSPENDED는 이의 신청
 * 기한이 지나면 영구 삭제로 넘어간다. 공구 게시물 원문은 분쟁 근거로 보관해야 한다.
 */
@Entity
@Table(name = "group_buy_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyPost extends BaseTimeEntity {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false, unique = true, updatable = false)
    private GroupBuy groupBuy;

    /** 앱 검증 40자(§31-2 · 근거 대기) — 컬럼은 100자로 여유를 둔다. */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private GroupBuyPostReviewStatus reviewStatus;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reject_reason_code", length = 64)
    private String rejectReasonCode;

    @Column(name = "reject_reason_detail", length = 1000)
    private String rejectReasonDetail;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_reason_code", length = 64)
    private String hiddenReasonCode;

    @Column(name = "hidden_reason_detail", length = 1000)
    private String hiddenReasonDetail;

    @Column(name = "unhidden_at")
    private LocalDateTime unhiddenAt;

    @Column(name = "unhidden_by")
    private Long unhiddenBy;

    /** 숨김 중인지 — 해제 시각이 숨김 시각보다 앞서거나 없으면 숨김이 살아 있다. */
    public boolean isHidden() {
        return hiddenAt != null && (unhiddenAt == null || unhiddenAt.isBefore(hiddenAt));
    }

    public boolean isApproved() {
        return reviewStatus == GroupBuyPostReviewStatus.APPROVED;
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    public String getContent() {
        return post == null ? null : post.getContent();
    }
}

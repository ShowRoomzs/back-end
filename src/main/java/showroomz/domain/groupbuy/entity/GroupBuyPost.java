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

    /** 숨김 당시 판본 — 숨긴 뒤 고쳐져도 숨긴 이유가 된 문장을 리비전에서 되짚는다(32 설계 0-5). */
    @Column(name = "hidden_revision_no")
    private Integer hiddenRevisionNo;

    @Column(name = "unhidden_at")
    private LocalDateTime unhiddenAt;

    @Column(name = "unhidden_by")
    private Long unhiddenBy;

    /** 해제 판단에 쓴 판본 — 「읽은 글 = 여는 글」의 기록(32 설계 5-4). */
    @Column(name = "unhidden_revision_no")
    private Integer unhiddenRevisionNo;

    /** 승인 후 마지막 수정 — 임시저장·제출은 찍지 않는다. 수정 내용은 리비전이 가진다(31 설계 2-5 · 2-6). */
    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt;

    /**
     * 최초 임시저장 또는 곧바로 제출 — 심사 상태는 DRAFT로 시작한다. 빈 제목은 {@code ''}로 둔다
     * (컬럼이 NOT NULL이고, 필수 검증은 제출 시점의 일이다 — 31 설계 2-3).
     */
    public static GroupBuyPost draft(Post post, GroupBuy groupBuy, String title) {
        return GroupBuyPost.builder()
                .post(post)
                .groupBuy(groupBuy)
                .title(title)
                .reviewStatus(GroupBuyPostReviewStatus.DRAFT)
                .build();
    }

    /**
     * 임시저장 · 제출 · 승인 후 수정이 공통으로 쓰는 본문 교체. <b>심사 상태는 건드리지 않는다</b> —
     * 반려 게시물을 임시저장해도 반려 그대로여야 반려 사유 카드(B3)가 고치는 동안 화면에 남는다.
     */
    public void rewrite(String title, String content) {
        this.title = title;
        this.post.updateContent(content, null);
    }

    /**
     * 등록하고 검토 요청 — 게이트 ②. 반려 필드는 <b>지우지 않는다</b> — 재심사하는 운영자가 이전 지적을 봐야 한다.
     * 새 판정이 나면 어드민 API가 덮어쓴다(31 설계 2-4).
     */
    public void submit(LocalDateTime now) {
        this.reviewStatus = GroupBuyPostReviewStatus.PENDING;
        this.submittedAt = now;
    }

    /** 승인 후 수정 — 즉시 반영 · 재승인 없음(인플 제14조⑤). 심사 상태는 APPROVED 그대로다. */
    public void markEdited(LocalDateTime now) {
        this.lastEditedAt = now;
    }

    // ── 어드민 판정(32 설계 5절) — 호출자가 group_buy → group_buy_post 순으로 잠근 뒤 부른다 ───────────

    /** 오픈 승인 — 게이트 ③. <b>반려 필드는 보존한다</b>(재심사 맥락 · 31 설계 2-4). */
    public void approve(Long operatorId, LocalDateTime now) {
        this.reviewStatus = GroupBuyPostReviewStatus.APPROVED;
        this.reviewedAt = now;
        this.reviewedBy = operatorId;
    }

    /** 오픈 반려 — 공구는 PREPARING에서 멈춘다(§29-3). 사유·설명은 덮어쓴다. */
    public void reject(String reasonCode, String reasonDetail, Long operatorId, LocalDateTime now) {
        this.reviewStatus = GroupBuyPostReviewStatus.REJECTED;
        this.rejectReasonCode = reasonCode;
        this.rejectReasonDetail = reasonDetail;
        this.reviewedAt = now;
        this.reviewedBy = operatorId;
    }

    /** 숨김 — 공구는 멈추지 않는다(§29-8 규칙 ①). 컬럼은 현재·마지막 숨김 1건만 담는다. */
    public void hide(String reasonCode, String reasonDetail, Integer revisionNo, Long operatorId, LocalDateTime now) {
        this.hiddenAt = now;
        this.hiddenBy = operatorId;
        this.hiddenReasonCode = reasonCode;
        this.hiddenReasonDetail = reasonDetail;
        this.hiddenRevisionNo = revisionNo;
        this.unhiddenAt = null;
        this.unhiddenBy = null;
        this.unhiddenRevisionNo = null;
    }

    /** 숨김 해제 — 운영자만(§29-8 규칙 ③). 「고쳤는가」는 서버가 판정하지 않는다 — 무엇을 보고 풀었는지만 박는다. */
    public void unhide(Integer revisionNo, Long operatorId, LocalDateTime now) {
        this.unhiddenAt = now;
        this.unhiddenBy = operatorId;
        this.unhiddenRevisionNo = revisionNo;
    }

    public boolean isPendingReview() {
        return reviewStatus == GroupBuyPostReviewStatus.PENDING;
    }

    /** 작성·제출 가능한 심사 상태 — 작성중 · 반려(31 설계 0-2). */
    public boolean isWritable() {
        return reviewStatus == GroupBuyPostReviewStatus.DRAFT || reviewStatus == GroupBuyPostReviewStatus.REJECTED;
    }

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

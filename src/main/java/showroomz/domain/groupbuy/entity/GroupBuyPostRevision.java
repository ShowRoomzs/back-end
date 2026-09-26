package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;

import java.time.LocalDateTime;

/**
 * 공구 게시물 리비전 — <b>승인받은 원문을 지키는 테이블</b>(31 설계 2-6). append-only.
 *
 * <p>승인 후 자유 수정(인플 제14조⑤)이 확정되면서 {@code post.content}·{@code group_buy_post.title}은 마지막 값만
 * 남는다. 운영자가 승인한 원문은 여기서만 읽을 수 있다 — 책임 귀속(승인 후 바뀐 부분) · 숨김 해제 판단 ·
 * 직권 중단 사유 판정의 근거다.
 *
 * <p>「승인된 판」을 따로 표시하지 않는다 — PENDING 동안 수정이 불가하므로 승인 시점의 원문은
 * {@code reviewed_at} 이전 마지막 SUBMITTED로 유일하게 정해진다.
 */
@Entity
@Table(name = "group_buy_post_revision",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_buy_post_revision_no",
                columnNames = {"post_id", "revision_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyPostRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revision_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private GroupBuyPost groupBuyPost;

    /** 게시물 안에서 1부터. 호출자가 게시물 행을 잠근 뒤 매긴다. */
    @Column(name = "revision_no", nullable = false, updatable = false)
    private Integer revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private GroupBuyPostRevisionKind kind;

    @Column(name = "title", nullable = false, length = 100, updatable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 크리에이터 id */
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    public static GroupBuyPostRevision snapshot(GroupBuyPost post, int revisionNo, GroupBuyPostRevisionKind kind,
                                                Long creatorId, LocalDateTime now) {
        return GroupBuyPostRevision.builder()
                .groupBuyPost(post)
                .revisionNo(revisionNo)
                .kind(kind)
                .title(post.getTitle())
                .content(post.getContent())
                .createdAt(now)
                .createdBy(creatorId)
                .build();
    }
}

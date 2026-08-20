package showroomz.domain.post.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostReportStatus;

import java.time.LocalDateTime;

/**
 * 게시물 신고 — 소비자가 운영자에게 올리는 접수 (C4 게시물 헤더 ⋯ · C4 하단 고지 "게시물 신고").
 *
 * <p>지금까지 운영자 조치({@link PostSuspension})의 <b>진입은 수동</b>이었다. 소비자가 신고할
 * 창구가 없어서 운영자가 게시물을 직접 찾아 내려야 했다. 이 테이블이 그 앞단을 채운다.
 *
 * <p>{@code (post_id, user_id)} 유니크로 <b>사람당 게시물당 1회</b>다. 같은 사람이 여러 번 눌러
 * 대기열을 부풀리면 신고 건수가 "몇 명이 문제라고 봤는가"를 뜻하지 않게 되고, 그러면 운영자가
 * 건수로 우선순위를 매길 수 없다. 서비스 검증만 두면 동시 요청에서 뚫리므로 DB로 막는다
 * ({@code post_appeal}의 게시물당 1회와 같은 방식이다).
 *
 * <p>신고자는 <b>지우지 않는다.</b> 허위 신고가 반복되면 계정 단위로 조치해야 하고, 그 판단에는
 * 누가 넣었는지가 남아 있어야 한다. 다만 이 값은 어드민 응답에도 실리지 않는다 — 신고 대상에게
 * 신고자가 드러날 경로를 만들지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post_report",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_report", columnNames = {"post_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_post_report_status_time", columnList = "status, reported_at"),
                @Index(name = "idx_post_report_post", columnList = "post_id, status")
        }
)
public class PostReport {

    /** 상세 사유 최대 길이 — {@code post_suspension.reason_detail}과 같은 폭이다 */
    public static final int MAX_DETAIL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private Users reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40, updatable = false)
    private PostReportReason reasonCode;

    @Column(name = "reason_detail", length = MAX_DETAIL_LENGTH, updatable = false)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostReportStatus status;

    /**
     * 접수 시각.
     *
     * <p>{@code @CreatedDate}가 아니라 서비스가 넘긴 값을 그대로 쓴다 — 한 번의 조치로 여러 신고를
     * 닫을 때 접수·처리 시각이 같은 시계에서 나와야 이력이 어긋나지 않는다.
     */
    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    /** 처리자(운영자) — 처리 시점에 고정한다. 조치와 같은 규칙이다(§24-5) */
    @Column(name = "handled_by")
    private Long handledBy;

    public PostReport(Post post, Users reporter, PostReportReason reasonCode,
                      String reasonDetail, LocalDateTime reportedAt) {
        this.post = post;
        this.reporter = reporter;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.status = PostReportStatus.PENDING;
        this.reportedAt = reportedAt;
    }

    public boolean isPending() {
        return this.status == PostReportStatus.PENDING;
    }

    /** 조치로 이어짐 — 노출 중지가 걸릴 때 그 게시물의 대기 신고가 한꺼번에 넘어온다 */
    public void accept(Long operatorId, LocalDateTime now) {
        markHandled(PostReportStatus.ACCEPTED, operatorId, now);
    }

    /** 검토 후 조치하지 않음 */
    public void dismiss(Long operatorId, LocalDateTime now) {
        markHandled(PostReportStatus.DISMISSED, operatorId, now);
    }

    private void markHandled(PostReportStatus resolved, Long operatorId, LocalDateTime now) {
        this.status = resolved;
        this.handledBy = operatorId;
        this.handledAt = now;
    }
}

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;

import java.time.LocalDateTime;

/**
 * 운영자 노출 중지 조치 1건 (§24-5).
 *
 * <p>{@code post}에 컬럼으로 붙이지 않고 테이블로 뺀 이유 — <b>재게시 후 재조치가 가능</b>하다.
 * 게시물당 1행으로 두면 첫 조치 이력이 덮이는데, §24-5는 사유·근거 규정·조치 시각·처리자·기한을
 * 화면에 남기라고 요구한다. {@code resolution IS NULL}인 행이 현재 진행 중인 조치다.
 *
 * <p>처리자와 기한을 <b>시점 고정</b>으로 들고 있는 것도 같은 이유다. 조치 당시의 운영자와
 * 그때 계산된 기한이 나중에 정책이 바뀌어도 흔들리면 안 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post_suspension",
        indexes = {
                @Index(name = "idx_post_suspension_post", columnList = "post_id, suspended_at"),
                @Index(name = "idx_post_suspension_deadline", columnList = "appeal_deadline, resolution")
        }
)
public class PostSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suspension_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40, updatable = false)
    private PostSuspensionReason reasonCode;

    @Column(name = "reason_detail", length = 500, updatable = false)
    private String reasonDetail;

    /** 근거 규정 조항 — 운영정책 문서의 조번호를 그대로 적는다 (§24-5 "근거 규정"을 화면에 남긴다) */
    @Column(name = "policy_ref", length = 200, updatable = false)
    private String policyRef;

    /** 처리자(운영자) — 조치 시점의 담당자를 고정한다 */
    @Column(name = "suspended_by", nullable = false, updatable = false)
    private Long suspendedBy;

    @Column(name = "suspended_at", nullable = false, updatable = false)
    private LocalDateTime suspendedAt;

    /** 이의 신청 기한 — 이 시각이 지나면 미신청으로 간주해 영구 삭제된다 (§24-5) */
    @Column(name = "appeal_deadline", nullable = false, updatable = false)
    private LocalDateTime appealDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 20)
    private SuspensionResolution resolution;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public PostSuspension(Post post, PostSuspensionReason reasonCode, String reasonDetail, String policyRef,
                          Long suspendedBy, LocalDateTime suspendedAt, LocalDateTime appealDeadline) {
        this.post = post;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.policyRef = policyRef;
        this.suspendedBy = suspendedBy;
        this.suspendedAt = suspendedAt;
        this.appealDeadline = appealDeadline;
    }

    /** 조치를 닫는다 — 어떻게 끝났는지는 {@link SuspensionResolution}이 말한다 */
    public void resolve(SuspensionResolution resolution, LocalDateTime resolvedAt) {
        this.resolution = resolution;
        this.resolvedAt = resolvedAt;
    }

    public boolean isOpen() {
        return this.resolution == null;
    }

    /** 이의 신청 가능 여부 — 진행 중인 조치이면서 기한 안이어야 한다 (§24-5) */
    public boolean isAppealable(LocalDateTime now) {
        return isOpen() && !now.isAfter(this.appealDeadline);
    }

    /** 화면 고지 문구용 — 사유 상세가 있으면 그것을, 없으면 코드 라벨을 쓴다 */
    public String describeReason() {
        return (reasonDetail == null || reasonDetail.isBlank()) ? reasonCode.getLabel() : reasonDetail;
    }
}

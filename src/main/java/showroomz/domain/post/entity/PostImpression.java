package showroomz.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;

/**
 * 게시물 노출 원천 로그 (§24-7).
 *
 * <p>누적 카운터({@code post.impression_count})만 있으면 기간 필터(최근 30일)·연령 분포·행동 귀속을
 * <b>영원히</b> 만들 수 없다. 카운터는 조회 성능을 위해 유지하되 인사이트 3단은 이 로그에서 계산한다.
 *
 * <p>{@code viewerKey} 규칙을 {@code showroom_visit.visitorKey}와 <b>같게</b> 맞추는 것이 핵심이다 —
 * §24-7의 라스트 터치 귀속이 이 키의 일치로만 성립한다. 중복 노출도 쇼룸 방문과 같은 30분 세션
 * 규칙으로 <b>적재 시점</b>에 거른다. 그래야 §22-4와 지표 정의가 어긋나지 않는다.
 *
 * <p>{@code creatorId}는 집계 조인을 없애려고 비정규화해 둔 값이고, 그래서 FK를 걸지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post_impression",
        indexes = {
                @Index(name = "idx_post_impression_post_time", columnList = "post_id, viewed_at"),
                @Index(name = "idx_post_impression_attribution", columnList = "viewer_key, viewed_at")
        }
)
public class PostImpression {

    /** 중복 노출 판정 세션 길이 — 쇼룸 방문(§22-4)과 같은 30분이다 */
    public static final int SESSION_MINUTES = 30;

    /** §24-7 귀속 창 — 게시물을 본 뒤 24시간 이내의 행동만 이 게시물의 몫으로 센다 */
    public static final int ATTRIBUTION_WINDOW_HOURS = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "impression_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    /** 집계 조인 제거용 비정규화 — FK를 걸지 않는다 */
    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    /** 로그인 노출만 채워진다 — 연령/성별 집계의 표본이다. 비로그인은 "미확인"으로 분류된다 (§24-7) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "viewer_key", nullable = false, length = 64, updatable = false)
    private String viewerKey;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private LocalDateTime viewedAt;

    public PostImpression(Post post, Long creatorId, Users user, String viewerKey, LocalDateTime viewedAt) {
        this.post = post;
        this.creatorId = creatorId;
        this.user = user;
        this.viewerKey = viewerKey;
        this.viewedAt = viewedAt;
    }
}

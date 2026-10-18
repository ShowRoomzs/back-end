package showroomz.domain.member.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.UserAdminActorType;
import showroomz.domain.member.user.type.UserAdminEventType;

import java.time.LocalDateTime;

/**
 * 소비자 처리 이력 1행 — 이 모듈이 일으킨 사건만 저장한다(51-6 설계서 0-11). 행위자 이름은 이벤트 시점의 스냅샷이다 — 두 번째 운영자가
 * 처리해도 과거 행이 바뀌지 않는다. 옛 {@code PATCH /status} 전이를 옮긴 행은 행위자가 없다(V204).
 */
@Entity
@Table(name = "user_admin_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAdminHistory {

    public static final int DETAIL_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private UserAdminEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private UserAdminActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 64)
    private String actorName;

    @Column(name = "detail", length = DETAIL_MAX)
    private String detail;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static UserAdminHistory of(Long userId, UserAdminEventType eventType, UserAdminActorType actorType,
                                      Long actorId, String actorName, String detail, String refType, Long refId,
                                      LocalDateTime now) {
        UserAdminHistory history = new UserAdminHistory();
        history.userId = userId;
        history.eventType = eventType;
        history.actorType = actorType;
        history.actorId = actorId;
        history.actorName = actorName;
        history.detail = detail == null || detail.length() <= DETAIL_MAX ? detail : detail.substring(0, DETAIL_MAX);
        history.refType = refType;
        history.refId = refId;
        history.createdAt = now;
        return history;
    }
}

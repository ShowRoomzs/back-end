package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorAdminEventType;

import java.time.LocalDateTime;

/**
 * 인플루언서 처리 이력 1행 — 이 모듈이 일으킨 사건만 저장한다(53 설계서 0-7). 행위자 이름은 이벤트 시점의 스냅샷이다 — 두 번째 운영자가
 * 처리해도 과거 행이 바뀌지 않는다. <b>파기 대상이 아니다</b>(§51-5).
 */
@Entity
@Table(name = "creator_admin_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorAdminHistory {

    public static final int DETAIL_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private CreatorAdminEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private CreatorAdminActorType actorType;

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

    public static CreatorAdminHistory of(Long creatorId, CreatorAdminEventType eventType, CreatorAdminActorType actorType,
                                         Long actorId, String actorName, String detail, String refType, Long refId,
                                         LocalDateTime now) {
        CreatorAdminHistory history = new CreatorAdminHistory();
        history.creatorId = creatorId;
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

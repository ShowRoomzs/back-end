package showroomz.domain.member.creator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.creator.type.FollowEventType;

import java.time.LocalDateTime;

/**
 * 팔로우 · 언팔로우 이력(49 스튜디오 성과 설계서 1-6).
 *
 * <p>{@link CreatorFollow}는 현재 관계만 들고 언팔로우하면 행을 지운다 — 「주문 당시 팔로워였나」 · 「공구 기간 팔로워 증감」은 이력이
 * 있어야 셀 수 있다. 팔로우 서비스가 관계를 바꾸는 <b>같은 트랜잭션</b>에서 한 행씩 쓴다.
 *
 * <p>언팔로우 수는 어디에도 노출하지 않는다(§22-4) — 이 테이블은 집계 입력이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "creator_follow_event", indexes = {
        @Index(name = "idx_cfe_creator_time", columnList = "creator_id, occurred_at"),
        @Index(name = "idx_cfe_user_creator_time", columnList = "user_id, creator_id, occurred_at")
})
public class CreatorFollowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 10)
    private FollowEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "attributed_post_id")
    private Long attributedPostId;

    private CreatorFollowEvent(Long creatorId, Long userId, FollowEventType eventType, LocalDateTime occurredAt,
                               Long attributedPostId) {
        this.creatorId = creatorId;
        this.userId = userId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.attributedPostId = attributedPostId;
    }

    public static CreatorFollowEvent follow(Long creatorId, Long userId, LocalDateTime at, Long attributedPostId) {
        return new CreatorFollowEvent(creatorId, userId, FollowEventType.FOLLOW, at, attributedPostId);
    }

    public static CreatorFollowEvent unfollow(Long creatorId, Long userId, LocalDateTime at) {
        return new CreatorFollowEvent(creatorId, userId, FollowEventType.UNFOLLOW, at, null);
    }
}

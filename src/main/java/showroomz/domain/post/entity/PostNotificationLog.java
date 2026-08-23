package showroomz.domain.post.entity;

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
import showroomz.domain.post.type.PostNotificationEvent;

import java.time.LocalDateTime;

/**
 * 게시물 통지 이력 (§24-6 "삭제 사실·사유·심사 이력은 알림 이력에 <b>영구 보존</b>").
 *
 * <p>{@code postId}에 <b>FK를 걸지 않는 유일한 테이블</b>이다. 게시물은 보관 기간이 끝나면 파기되는데
 * FK가 있으면 파기 배치가 이력을 같이 지우거나 막힌다. 영구 보존과 파기가 한 그래프 안에 있을 수 없다.
 *
 * <p>{@code payload}에 통지 당시 문구를 그대로 굳히는 것도 같은 이유다. 사유·근거 규정·기한을
 * 나중에 게시물에서 다시 읽어 재구성하려 하면, 게시물이 사라진 뒤에는 재구성할 수 없다.
 *
 * <p>{@code delivered}는 <b>한 명이라도 실제로 받았는지</b>다. 팔로워 알림은 수신자가 수만 명이라
 * 한 사람씩 성공 여부를 남기지 않는다 — 그러려면 게시물 하나에 수만 행이 생긴다.
 * FCM 미설정 환경에서는 언제나 false로 남아, 무엇이 나가지 않았는지 이 컬럼으로 읽힌다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post_notification_log",
        indexes = {
                @Index(name = "idx_post_notification_creator", columnList = "creator_id, sent_at")
        }
)
public class PostNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    /** 게시물이 파기돼도 남는다 — FK 없음 */
    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private PostNotificationEvent eventType;

    /** 통지 당시 문구를 굳힌 JSON 문자열 */
    @Column(name = "payload", columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    /** 발송 인프라 도입 전에는 항상 false — 이력은 남았지만 실제로 나가지는 않았다는 뜻이다 */
    @Column(name = "delivered", nullable = false)
    private Boolean delivered = false;

    public PostNotificationLog(Long postId, Long creatorId, PostNotificationEvent eventType,
                               String payload, LocalDateTime sentAt) {
        this.postId = postId;
        this.creatorId = creatorId;
        this.eventType = eventType;
        this.payload = payload;
        this.sentAt = sentAt;
        this.delivered = false;
    }

    /** 발송 어댑터가 실제 전달에 성공했을 때만 호출한다 */
    public void markDelivered() {
        this.delivered = true;
    }
}

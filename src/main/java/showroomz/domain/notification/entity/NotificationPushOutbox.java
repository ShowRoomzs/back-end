package showroomz.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 늦게 보내는 소비자 푸시(1017 설계서 1-6) — 광고성 알림만 탄다. 야간(21~08시)이면 다음 08:00, 하루 묶음이면 묶음 시각에 보낸다.
 * 알림함 행 하나에 최대 하나(묶음이 갱신되면 문구만 갈아 끼운다). 거래 알림은 커밋 직후 바로 보내 이 표를 거치지 않는다.
 */
@Entity
@Table(name = "notification_push_outbox", indexes = @Index(name = "idx_outbox_due", columnList = "sent_at, send_after"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPushOutbox {

    private static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "send_after", nullable = false)
    private LocalDateTime sendAfter;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** PushMessage.data — 앱 딥링크(type · targetType · targetId). */
    @Column(name = "data_json", nullable = false, length = 1000)
    private String dataJson;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public static NotificationPushOutbox of(Long notificationId, Long userId, LocalDateTime sendAfter, String title,
                                            String body, String imageUrl, String dataJson) {
        NotificationPushOutbox outbox = new NotificationPushOutbox();
        outbox.notificationId = notificationId;
        outbox.userId = userId;
        outbox.sendAfter = sendAfter;
        outbox.title = title;
        outbox.body = body;
        outbox.imageUrl = imageUrl;
        outbox.dataJson = dataJson;
        return outbox;
    }

    /** 묶음 갱신 — 아직 안 보냈을 때만 문구를 바꾼다(보낸 뒤 온 건은 다음 묶음이다). */
    public void refresh(String title, String body) {
        if (sentAt == null) {
            this.title = title;
            this.body = body;
        }
    }

    public void markSent(LocalDateTime now) {
        this.sentAt = now;
        this.attempt++;
    }

    /** 실패 — 3회까지 다시 시도하고, 그 뒤는 보낸 것으로 닫는다(알림함 행은 남아 있다). */
    public void markFailed(String error, LocalDateTime now) {
        this.attempt++;
        this.lastError = error == null ? null : error.length() > 500 ? error.substring(0, 500) : error;
        if (attempt >= MAX_ATTEMPTS) {
            this.sentAt = now;
        }
    }

    public boolean isSent() {
        return sentAt != null;
    }
}

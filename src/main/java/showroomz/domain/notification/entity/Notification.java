package showroomz.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.notification.type.NotificationEventType;
import showroomz.domain.notification.type.NotificationReceiverType;

/**
 * 인앱 알림 — 각 객체 상태 전이의 부수 효과로 생성(무거운 객체 아님).
 * 수신자는 다형 참조(수신자 유형 + ID). 게시물 등록(#14)은 팔로워 다수 팬아웃.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification")
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "receiver_type", nullable = false, length = 20)
    private NotificationReceiverType receiverType;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private NotificationEventType eventType;

    /** 관련 객체 다형 참조 — 연결·계약·주문·게시물 등 */
    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Builder
    public Notification(NotificationReceiverType receiverType, Long receiverId,
                        NotificationEventType eventType, String targetType, Long targetId, String content) {
        this.receiverType = receiverType;
        this.receiverId = receiverId;
        this.eventType = eventType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.content = content;
        this.read = false;
    }

    public void markAsRead() {
        this.read = true;
    }
}

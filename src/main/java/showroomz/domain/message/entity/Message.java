package showroomz.domain.message.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.message.type.ParticipantType;

/**
 * §13-10 재전송 멱등 — CLIENT_MESSAGE_ID는 FE가 발급한 UUID다. 서버는
 * UNIQUE(thread_id, client_message_id) 충돌 시 신규 저장 대신 기존 행을 그대로 반환한다.
 */
@Entity
@Table(name = "message",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_message_thread_client_id",
                columnNames = {"thread_id", "client_message_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Message extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private MessageThread thread;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private ParticipantType senderType;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "client_message_id", nullable = false, length = 64)
    private String clientMessageId;

    public static Message create(MessageThread thread, ParticipantType senderType, Long senderId,
                                  String clientMessageId, String content) {
        return Message.builder()
                .thread(thread)
                .senderType(senderType)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .content(content)
                .build();
    }
}

package showroomz.domain.message.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageRefType;
import showroomz.domain.message.type.MessageType;
import showroomz.domain.message.type.ParticipantType;

/**
 * §13-10 재전송 멱등 — CLIENT_MESSAGE_ID는 FE가 발급한 UUID다. 서버는
 * UNIQUE(thread_id, client_message_id) 충돌 시 신규 저장 대신 기존 행을 그대로 반환한다.
 *
 * <p>시스템 카드도 이 테이블의 행이다(36 설계 0-3) — 대화의 시간순 흐름 · 안 읽은 수 · 미리보기를 그대로 탄다.
 * 카드의 보낸 사람은 그 카드를 생기게 한 주체(요청자 · 처리 운영자)이고, {@code content}에는 카드 제목이 들어간다 —
 * 카드를 그리지 않는 화면에서도 말풍선으로 제목이 보인다.
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MessageType messageType = MessageType.TEXT;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", length = 40)
    private MessageCardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 32)
    private MessageRefType refType;

    @Column(name = "ref_id")
    private Long refId;

    /** 카드 생성 시점 스냅샷(JSON 문자열) — {@link MessageCardPayload}로 읽고 쓴다. */
    @Column(name = "card_payload", columnDefinition = "TEXT")
    private String cardPayload;

    /** 정해진 문구의 자동 전송 말풍선 — 어드민 응답에만 내린다(36 설계 0-4). */
    @Builder.Default
    @Column(name = "auto_notice", nullable = false)
    private boolean autoNotice = false;

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

    /** 정해진 문구의 자동 안내 — 사람의 말풍선과 같은 모양으로 나가고 표시만 남는다. */
    public static Message createAutoNotice(MessageThread thread, ParticipantType senderType, Long senderId,
                                           String clientMessageId, String content) {
        return Message.builder()
                .thread(thread)
                .senderType(senderType)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .content(content)
                .autoNotice(true)
                .build();
    }

    public static Message createCard(MessageThread thread, ParticipantType senderType, Long senderId,
                                     String clientMessageId, MessageCardType cardType,
                                     MessageRefType refType, Long refId, String cardPayload) {
        return Message.builder()
                .thread(thread)
                .senderType(senderType)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .messageType(MessageType.SYSTEM)
                .cardType(cardType)
                .refType(refType)
                .refId(refId)
                .cardPayload(cardPayload)
                .content(cardType.getTitle())
                .build();
    }

    public boolean isCard() {
        return this.messageType == MessageType.SYSTEM;
    }
}

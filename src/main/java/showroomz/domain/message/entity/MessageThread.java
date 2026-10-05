package showroomz.domain.message.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.message.type.ThreadStatus;

import java.time.LocalDateTime;

/**
 * CONNECTION에 종속된다 — 스레드의 참여자(누구와 누구의 대화인지)는 전부 CONNECTION에서
 * 파생되므로 이 엔티티는 CONNECTION_ID만 참조한다(§1-3). PAIR/OPERATOR_MARKET/OPERATOR_CREATOR
 * 어느 타입이든 스레드 쪽 로직은 완전히 동일하다.
 *
 * <p>쌍당 1개는 {@link ThreadKind#CONNECTION}에만 걸린다(운영 DB 생성 컬럼 {@code connection_thread_key} UNIQUE ·
 * 엔티티 미매핑). 공구 3자 스레드는 같은 PAIR 연결에 붙는 추가 스레드라 접근 판정 · 목록 · 안 읽은 수가 그대로 동작한다.
 */
@Entity
@Table(name = "message_thread")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MessageThread extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "thread_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "thread_kind", nullable = false, length = 32)
    private ThreadKind kind = ThreadKind.CONNECTION;

    /** 공구 3자 스레드의 공구 id — CONNECTION이면 null. */
    @Column(name = "subject_id")
    private Long subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ThreadStatus status;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 255)
    private String lastMessagePreview;

    /** 어드민 목록의 「운영팀: 」 접두 판정(36 설계 1-2) — 미리보기 문자열에는 접두를 박지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_message_sender_type", length = 20)
    private ParticipantType lastMessageSenderType;

    public static MessageThread openFor(Connection connection) {
        return MessageThread.builder()
                .connection(connection)
                .kind(ThreadKind.CONNECTION)
                .status(ThreadStatus.OPEN)
                .build();
    }

    /** 공구 3자 스레드 — 참여자는 그 쌍의 PAIR 연결에서 온다. 생성과 동시에 OPEN이다. */
    public static MessageThread openForGroupBuy(Connection pairConnection, ThreadKind kind, Long groupBuyId) {
        if (!kind.isGroupBuy()) {
            throw new IllegalArgumentException("공구 스레드 종류가 아니다: " + kind);
        }
        return MessageThread.builder()
                .connection(pairConnection)
                .kind(kind)
                .subjectId(groupBuyId)
                .status(ThreadStatus.OPEN)
                .build();
    }

    /** 연결이 CONNECTED로 바뀔 때(최초 수락 또는 재연결) 호출 — 기존 스레드가 있으면 다시 연다(§1-3). */
    public void open() {
        this.status = ThreadStatus.OPEN;
    }

    public void dormant() {
        this.status = ThreadStatus.DORMANT;
    }

    public boolean isOpen() {
        return this.status == ThreadStatus.OPEN;
    }

    /** 첨부만 전송된 경우 등을 대비해 목록 미리보기는 표시용 텍스트를 그대로 받는다(P3에서 첨부 케이스 문구를 결정). */
    public void recordLastMessage(String preview, LocalDateTime sentAt, ParticipantType senderType) {
        this.lastMessagePreview = preview;
        this.lastMessageAt = sentAt;
        this.lastMessageSenderType = senderType;
    }
}

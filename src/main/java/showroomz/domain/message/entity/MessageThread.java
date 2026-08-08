package showroomz.domain.message.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.message.type.ThreadStatus;

import java.time.LocalDateTime;

/**
 * CONNECTION에 1:1로 종속된다 — 스레드의 정체성(누구와 누구의 대화인지)은 전부 CONNECTION에서
 * 파생되므로 이 엔티티는 CONNECTION_ID만 참조한다(§1-3). PAIR/OPERATOR_MARKET/OPERATOR_CREATOR
 * 어느 타입이든 스레드 쪽 로직은 완전히 동일하다.
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false, unique = true)
    private Connection connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ThreadStatus status;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 255)
    private String lastMessagePreview;

    public static MessageThread openFor(Connection connection) {
        return MessageThread.builder()
                .connection(connection)
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
    public void recordLastMessage(String preview, LocalDateTime sentAt) {
        this.lastMessagePreview = preview;
        this.lastMessageAt = sentAt;
    }
}

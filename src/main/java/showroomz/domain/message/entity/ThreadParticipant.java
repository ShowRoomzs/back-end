package showroomz.domain.message.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.message.type.ParticipantType;

import java.time.LocalDateTime;

/**
 * 스레드별 참가자의 읽음 위치. PAIR 스레드의 ADMIN 열람(§13-4 모니터링 열람)은 이 테이블에
 * 행을 두지 않는다 — 운영자는 그 스레드의 당사자가 아니라 안 읽은 수 추적 대상이 아니다.
 * OPERATOR_MARKET/OPERATOR_CREATOR 스레드에서는 ADMIN도 SELLER/CREATOR와 동일하게
 * 정상적으로 읽음 위치를 추적한다.
 */
@Entity
@Table(name = "thread_participant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_thread_participant",
                columnNames = {"thread_id", "participant_type", "participant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ThreadParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private MessageThread thread;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 20)
    private ParticipantType participantType;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    public static ThreadParticipant create(MessageThread thread, ParticipantType type, Long participantId) {
        return ThreadParticipant.builder()
                .thread(thread)
                .participantType(type)
                .participantId(participantId)
                .build();
    }

    public void markRead(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
        this.lastReadAt = LocalDateTime.now();
    }
}

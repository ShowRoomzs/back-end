package showroomz.domain.notification.service;

import lombok.Builder;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림 한 건의 내용 — 발생 지점이 값만 채워 {@link NotificationPublisher}에 넘긴다(엔티티를 들고 다니지 않는다 · 커밋 뒤에 쓴다).
 *
 * @param digest            {@code dedupKey}가 겹치면 건너뛰지 않고 대표 행에 합친다(하루 묶음 — 「새 게시물 3개」)
 * @param digestTitleFormat 묶음에 합쳐진 뒤의 제목 — {@code %d} 자리에 건수가 들어간다
 * @param skipPush          알림함 행만 쓴다 — 발생 지점이 자기 문구 · 데이터로 이미 푸시를 보낸다(소비자 계정 정지 · 해제)
 */
@Builder
public record NotificationCommand(
        NotificationRecipientType recipientType,
        Long recipientId,
        NotificationType type,
        NotificationActorType actorType,
        String actorName,
        String title,
        String body,
        String emphasis,
        NotificationTargetType targetType,
        Long targetId,
        String targetSub,
        NotificationImageKind imageKind,
        String imageRef,
        LocalDateTime todoDueAt,
        String dedupKey,
        boolean digest,
        String digestTitleFormat,
        boolean skipPush) {

    public static NotificationCommandBuilder toSeller(Long marketId, NotificationType type) {
        return NotificationCommand.builder().recipientType(NotificationRecipientType.SELLER).recipientId(marketId).type(type);
    }

    public static NotificationCommandBuilder toCreator(Long creatorId, NotificationType type) {
        return NotificationCommand.builder().recipientType(NotificationRecipientType.CREATOR).recipientId(creatorId).type(type);
    }

    public static NotificationCommandBuilder toUser(Long userId, NotificationType type) {
        return NotificationCommand.builder().recipientType(NotificationRecipientType.USER).recipientId(userId).type(type);
    }
}

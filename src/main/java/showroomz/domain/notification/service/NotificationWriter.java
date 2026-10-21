package showroomz.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.entity.NotificationPushOutbox;
import showroomz.domain.notification.repository.NotificationPushOutboxRepository;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;

import java.time.LocalDateTime;

/**
 * 알림함 행 쓰기 + 소비자 푸시 연결(1017 설계서 1-6).
 *
 * <ul>
 *   <li>광고성 · 수신 미동의 → 행을 만들지 않는다</li>
 *   <li>{@code dedupKey}가 이미 있으면 건너뛴다 — 묶음이면 대표 행에 합친다(「새 게시물 3개」)</li>
 *   <li>소비자 거래 알림 → 바로 푸시 · 광고성 → 지연 푸시 표(야간이면 다음 08:00 · 묶음이면 묶음 시각)</li>
 *   <li>파트너 · 스튜디오 → 푸시 없음(인앱 알림함만)</li>
 * </ul>
 */
@Slf4j
@Component
public class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final NotificationPushOutboxRepository outboxRepository;
    private final NotificationConsentReader consentReader;
    private final NotificationPushDispatcher pushDispatcher;
    private final NotificationSchedule schedule;
    private final TransactionTemplate newTransaction;

    public NotificationWriter(NotificationRepository notificationRepository,
                              NotificationPushOutboxRepository outboxRepository,
                              NotificationConsentReader consentReader, NotificationPushDispatcher pushDispatcher,
                              NotificationSchedule schedule, PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.consentReader = consentReader;
        this.pushDispatcher = pushDispatcher;
        this.schedule = schedule;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 커밋 뒤 콜백에서 불린다 — 어떤 예외도 호출자(이미 커밋된 요청)로 올리지 않는다. */
    public void writeSafely(NotificationCommand command) {
        try {
            Notification written = newTransaction.execute(status -> write(command, LocalDateTime.now()));
            if (written != null && written.getRecipientType() == NotificationRecipientType.USER
                    && !written.isAdvertising() && !command.skipPush()) {
                pushDispatcher.sendNow(written);
            }
        } catch (RuntimeException e) {
            log.warn("알림 기록 실패 - type: {}, recipient: {} {}", command.type(), command.recipientType(),
                    command.recipientId(), e);
        }
    }

    /** @return 새로 쓴 행 — 건너뛰었거나 묶음에 합쳤으면 null */
    Notification write(NotificationCommand c, LocalDateTime now) {
        boolean user = c.recipientType() == NotificationRecipientType.USER;
        if (user && !consentReader.allows(c.recipientId(), c.type())) {
            return null;
        }
        if (c.dedupKey() != null) {
            Notification existing = notificationRepository.findByDedupKey(c.dedupKey()).orElse(null);
            if (existing != null) {
                if (c.digest()) {
                    int count = existing.getDigestCount() + 1;
                    existing.absorbDigest(c.digestTitleFormat() == null ? c.title()
                            : c.digestTitleFormat().formatted(count), c.emphasis(), now);
                    outboxRepository.findByNotificationId(existing.getId())
                            .ifPresent(outbox -> outbox.refresh(pushDispatcher.title(existing), existing.getBody()));
                }
                return null;
            }
        }
        Notification saved = notificationRepository.save(Notification.builder()
                .recipientType(c.recipientType()).recipientId(c.recipientId()).type(c.type())
                .actorType(c.actorType()).actorName(c.actorName())
                .title(c.title()).body(c.body()).emphasis(c.emphasis())
                .targetType(c.targetType()).targetId(c.targetId()).targetSub(c.targetSub())
                .imageKind(c.imageKind()).imageRef(c.imageRef()).todoDueAt(c.todoDueAt())
                .dedupKey(c.dedupKey()).createdAt(now)
                .build());
        if (user && saved.isAdvertising()) {
            LocalDateTime sendAfter = c.digest() ? schedule.digestSlot(now) : schedule.nextAllowed(now);
            outboxRepository.save(NotificationPushOutbox.of(saved.getId(), saved.getRecipientId(), sendAfter,
                    pushDispatcher.title(saved), pushDispatcher.body(saved), null, pushDispatcher.dataJson(saved)));
        }
        return saved;
    }
}

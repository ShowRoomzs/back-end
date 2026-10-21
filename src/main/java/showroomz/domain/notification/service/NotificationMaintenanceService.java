package showroomz.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.notification.entity.NotificationPushOutbox;
import showroomz.domain.notification.repository.NotificationPushOutboxRepository;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.global.config.properties.NotificationProperties;

import java.time.LocalDateTime;
import java.util.List;

/** 지연 푸시 발송 · 보관 기간 정리(1017 설계서 1-6) — 스케줄러가 부르고 테스트는 직접 부른다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationMaintenanceService {

    private final NotificationPushOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPushDispatcher pushDispatcher;
    private final NotificationSchedule schedule;
    private final NotificationProperties properties;

    /**
     * 보낼 때가 된 광고성 푸시를 보낸다. 야간이면 보내지 않는다 — 발송 시각을 잡을 때 이미 피했지만, 배치가 밀려 야간에 돌 수도 있다.
     *
     * @return 보낸 건수
     */
    @Transactional
    public int sendDue(LocalDateTime now) {
        if (schedule.isQuiet(now)) {
            return 0;
        }
        List<NotificationPushOutbox> due = outboxRepository.findDue(now,
                PageRequest.of(0, properties.getOutboxBatchSize()));
        int sent = 0;
        for (NotificationPushOutbox outbox : due) {
            boolean ok = pushDispatcher.send(outbox.getUserId(), outbox.getTitle(), outbox.getBody(),
                    outbox.getImageUrl(), outbox.getDataJson());
            if (ok) {
                outbox.markSent(now);
                sent++;
            } else {
                outbox.markFailed("push failed", now);
            }
        }
        return sent;
    }

    /** 보관 기간이 지난 알림함 행 · 보낸 지 7일 지난 지연 푸시를 지운다. */
    @Transactional
    public int purge(LocalDateTime now) {
        int removed = 0;
        for (NotificationRecipientType type : NotificationRecipientType.values()) {
            removed += notificationRepository.deleteExpired(type, now.minusDays(type.getRetentionDays()));
        }
        outboxRepository.deleteSentBefore(now.minusDays(7));
        if (removed > 0) {
            log.info("알림 보관 정리 - {}건", removed);
        }
        return removed;
    }
}

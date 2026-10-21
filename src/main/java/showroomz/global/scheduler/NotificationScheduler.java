package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.notification.service.NotificationDeadlineNoticeService;
import showroomz.domain.notification.service.NotificationMaintenanceService;

import java.time.LocalDateTime;

/**
 * 알림 배치(1017 설계서 1-6) — 지연 푸시(1분) · 보관 정리(매일 04:00) · D-1 안내(매일 10:00 — 공구 마감 임박 · 구매확정 · 서명 기한).
 * 기존 스케줄러와 같은 전제 — 단일 인스턴스 · 분산 락 없음. D-1 안내는 중복 키로 하루 한 번만 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private final NotificationMaintenanceService maintenanceService;
    private final NotificationDeadlineNoticeService deadlineNoticeService;

    @Scheduled(cron = "30 * * * * *", zone = "Asia/Seoul")
    public void sendDuePushes() {
        try {
            maintenanceService.sendDue(LocalDateTime.now());
        } catch (Exception e) {
            log.error("지연 푸시 발송 실패", e);
        }
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void purge() {
        try {
            maintenanceService.purge(LocalDateTime.now());
        } catch (Exception e) {
            log.error("알림 보관 정리 실패", e);
        }
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void dailyNotices() {
        LocalDateTime now = LocalDateTime.now();
        run("공구 마감 임박", () -> deadlineNoticeService.noticeGroupBuyEnding(now));
        run("구매확정 D-1", () -> deadlineNoticeService.noticePurchaseConfirm(now));
        run("서명 기한 D-1", () -> deadlineNoticeService.noticeSignatureDeadline(now));
    }

    private void run(String job, java.util.function.IntSupplier step) {
        try {
            int count = step.getAsInt();
            if (count > 0) {
                log.info("{} 알림 - {}건", job, count);
            }
        } catch (Exception e) {
            log.error("{} 알림 실패", job, e);
        }
    }
}

package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.api.common.attachment.service.MessageThreadRetentionPurgeService;

import java.time.LocalDateTime;

/** 소통 스레드 보존 기간 파기 배치(수정요청 C) — 매일 04:40. 연결 해제 · 탈퇴 + 1년이 지난 쌍 스레드의 메시지 · 첨부를 지운다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "message", name = "retention-purge-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class MessageThreadRetentionPurgeScheduler {

    private static final int MAX_PER_RUN = 100;

    private final MessageThreadRetentionPurgeService purgeService;

    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int purged = 0;
        for (Long id : purgeService.findIdsToPurge(now, MAX_PER_RUN)) {
            try {
                if (purgeService.purge(id, now)) {
                    purged++;
                }
            } catch (Exception e) {
                log.error("소통 스레드 파기 실패 - threadId: {}", id, e);
            }
        }
        if (purged > 0) {
            log.info("소통 스레드 보존 기간 파기 - {}건", purged);
        }
    }
}

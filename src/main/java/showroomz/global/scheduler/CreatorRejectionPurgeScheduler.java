package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.service.CreatorRejectionPurgeService;

import java.time.LocalDateTime;

/** 인플루언서 입점 반려 정보 파기 배치(수정요청 B-8) — 매일 04:10. 반려 처리 + 14일이 지난 신청의 개인정보를 지운다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "member.creator-application", name = "purge-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class CreatorRejectionPurgeScheduler {

    private static final int MAX_PER_RUN = 500;

    private final CreatorRejectionPurgeService purgeService;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int purged = 0;
        for (Long id : purgeService.findIdsToPurge(now, MAX_PER_RUN)) {
            try {
                if (purgeService.purge(id, now)) {
                    purged++;
                }
            } catch (Exception e) {
                log.error("입점 반려 정보 파기 실패 - applicationId: {}", id, e);
            }
        }
        if (purged > 0) {
            log.info("입점 반려 정보 파기 - {}건", purged);
        }
    }
}

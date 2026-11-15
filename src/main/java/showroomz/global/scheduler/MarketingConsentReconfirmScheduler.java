package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.member.user.service.MarketingConsentReconfirmService;

import java.time.LocalDateTime;

/** 마케팅 수신 동의 2년 재확인 통지 배치(수정요청 C) — 매일 11:00(야간 광고성 통지 제한을 피한다). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "member", name = "marketing-reconfirm-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class MarketingConsentReconfirmScheduler {

    private static final int MAX_PER_RUN = 1000;

    private final MarketingConsentReconfirmService reconfirmService;

    @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int noticed = 0;
        for (Long id : reconfirmService.findIdsToNotice(now, MAX_PER_RUN)) {
            try {
                if (reconfirmService.notice(id, now)) {
                    noticed++;
                }
            } catch (Exception e) {
                log.error("마케팅 수신 동의 재확인 통지 실패 - userId: {}", id, e);
            }
        }
        if (noticed > 0) {
            log.info("마케팅 수신 동의 2년 재확인 통지 - {}건", noticed);
        }
    }
}

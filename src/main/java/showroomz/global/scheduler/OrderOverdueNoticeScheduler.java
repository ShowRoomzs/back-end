package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.service.OrderOverdueNoticeService;

import java.time.LocalDateTime;

/**
 * 처리 지연 자동 알림 배치(1009 기획 수정본 8-4) — 영업일 10시 · 15시에 돌고, 같은 건은 하루 한 번만 센다(두 번째 회차는 첫 회차를
 * 놓친 건을 줍는다). 주말 · 공휴일에는 돌지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order", name = "overdue-notice-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class OrderOverdueNoticeScheduler {

    private static final int MAX_PER_RUN = 500;

    private final OrderOverdueNoticeService noticeService;

    @Scheduled(cron = "0 0 10,15 * * *", zone = "Asia/Seoul")
    public void tick() {
        run(LocalDateTime.now());
    }

    /** 한 회차 — 실행 시각을 받는다(테스트가 회차 시각을 고정한다). */
    public void run(LocalDateTime now) {
        if (!noticeService.isNoticeDay(now)) {
            return;
        }
        int ship = 0;
        for (Long id : noticeService.findShipOverdue(now, MAX_PER_RUN)) {
            try {
                if (noticeService.noticeShipOverdue(id, now)) {
                    ship++;
                }
            } catch (Exception e) {
                log.error("발송 기한 경과 알림 기록 실패 - deliveryGroupId: {}", id, e);
            }
        }
        int inspect = 0;
        for (Long id : noticeService.findInspectOverdue(now, MAX_PER_RUN)) {
            try {
                if (noticeService.noticeInspectOverdue(id, now)) {
                    inspect++;
                }
            } catch (Exception e) {
                log.error("검수 기한 경과 알림 기록 실패 - claimId: {}", id, e);
            }
        }
        if (ship + inspect > 0) {
            log.info("처리 지연 자동 알림 - 발송 {}건 · 검수 {}건", ship, inspect);
        }
    }
}

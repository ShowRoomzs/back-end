package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.service.PaymentHealthService;

import java.time.LocalDateTime;

/** 운영 지표 5종(결제 계획서 4-8) — 10분 주기로 임계값을 넘으면 Sentry warning. 어드민 화면(9-1 ⑧)이 생기기 전의 눈이다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order", name = "health-check-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentHealthScheduler {

    private final PaymentHealthService healthService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void check() {
        try {
            healthService.check(LocalDateTime.now());
        } catch (Exception e) {
            log.error("결제 운영 지표 점검 실패", e);
        }
    }
}

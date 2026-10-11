package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.SettlementPayoutService;
import showroomz.global.config.properties.SettlementProperties;

import java.time.LocalDateTime;

/**
 * 지급 결과 조회(44 어드민 설계서 3-3 · 포트원 설계서 5-4) — 30분마다 REQUESTED 행의 결과를 PG 에 묻는다. 포트원 모드에서만 돈다
 * ({@code SIMULATED} 는 REQUESTED 를 내지 않는다). 지급 시각 틱에서는 「지급 미실행」(9-2)도 함께 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "payout-result-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementPayoutResultScheduler {

    private final SettlementProperties properties;
    private final SettlementPayoutService payoutService;

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    public void tick() {
        run(LocalDateTime.now());
    }

    /** 1회분 — 기준 시각을 받는다(테스트가 지급 시각 틱을 고정한다). */
    public void run(LocalDateTime now) {
        if (!properties.getPayout().getMode().isPortOne()) {
            return;
        }
        payoutService.pollResults(now);
    }
}

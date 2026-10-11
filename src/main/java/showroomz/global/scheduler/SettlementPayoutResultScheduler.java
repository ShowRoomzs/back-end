package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.SettlementProperties;

/**
 * 지급 결과 조회 배치(44 어드민 설계서 3-3) — PG 모드에서 결과를 미룬(REQUESTED) 행을 PG 에 조회해 닫는다. 30분 주기.
 *
 * <p><b>골격</b>이다 — {@code SIMULATED}는 REQUESTED 를 내지 않고, PG 지급대행 어댑터(13절 A-1)가 아직 없어 조회 API 도 없다.
 * 어댑터가 붙으면 {@code SettlementPayoutTransitions.applyResults}로 같은 식으로 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "payout-result-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementPayoutResultScheduler {

    private final SettlementProperties properties;

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    public void tick() {
        if (properties.getPayout().getMode() != SettlementProperties.PayoutMode.PG) {
            return;
        }
        log.warn("정산 지급 결과 조회 — PG 지급대행 어댑터 미구현(런칭 게이트)");
    }
}

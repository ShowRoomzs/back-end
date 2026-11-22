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
 * 지급 결과 조회(44 어드민 설계서 3-3 · 토스 설계서 5-4 · 60 이니시스 전환 계획서 D-8) — 30분마다 결과를 PG 에 묻는다. PG 모드에서만 돈다
 * ({@code SIMULATED} 는 올리는 즉시 지급 완료다).
 *
 * <ul>
 *   <li>이니시스 — 웹훅이 없어 <b>이 폴링이 1차</b>다. 지급일 ≤ 오늘인 열린 묶음을 날짜별로 {@code payresult} 에 묻는다. 처리 지연 판정은 지급일 기준.</li>
 *   <li>토스 — 웹훅({@code payout.changed})이 1차이고 이 폴링은 백스톱(유실 · 서명 실패 · IN_PROGRESS 장기화)이다. 처리 지연 판정은 지시 기준.</li>
 * </ul>
 * 지급 시각 틱에서는 「PG 처리 지연」도 함께 본다.
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
        if (!properties.getPayout().getMode().isExternal()) {
            return;
        }
        payoutService.pollResults(now);
    }
}

package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.service.OrderClaimService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 자동 입고(1021 결정 노트 기-17 · 설계서 4-3) — 회수 택배가 도착(추적상)했는데 N영업일이 지나도 브랜드가 입고 확인을 하지 않으면
 * 시스템이 입고 처리한다. 검수 기한은 그때 발급되고, 이후 검수 기한 경과 자동 처리가 이어받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.claim", name = "auto-receive-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class ClaimAutoReceiveScheduler {

    private static final int MAX_PER_RUN = 500;

    private final OrderClaimService claimService;

    @Scheduled(cron = "0 10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> claimIds = claimService.findIdsToAutoReceive(now, MAX_PER_RUN);
        int received = 0;
        for (Long claimId : claimIds) {
            try {
                if (claimService.autoReceive(claimId, now)) {
                    received++;
                }
            } catch (Exception e) {
                // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
                log.error("자동 입고 실패 - claimId: {}", claimId, e);
            }
        }
        if (received > 0) {
            log.info("자동 입고 완료 - {}건", received);
        }
    }
}

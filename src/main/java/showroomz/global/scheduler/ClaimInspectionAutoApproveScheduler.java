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
 * 반품 검수 기한 경과 자동 승인(대표 결정 2026-11-10) — 반품을 돌려받은 날(회수 배송완료) + 2영업일 안에 브랜드가 판정하지 않으면
 * 시스템이 승인하고, 판정 종료와 함께 PG 가 즉시 환불한다(승인 후 1영업일 · 총 3영업일). 자동 입고(도착 + 3영업일)를 대신한다.
 * 교환은 자동 승인하지 않는다 — 기한이 지나면 처리 지연 「검수 지연」으로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.claim", name = "inspect-auto-approve-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class ClaimInspectionAutoApproveScheduler {

    private static final int MAX_PER_RUN = 500;

    private final OrderClaimService claimService;

    @Scheduled(cron = "0 10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> claimIds = claimService.findReturnIdsToAutoApprove(now, MAX_PER_RUN);
        int approved = 0;
        for (Long claimId : claimIds) {
            try {
                if (claimService.autoApproveReturn(claimId, now)) {
                    approved++;
                }
            } catch (Exception e) {
                // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
                log.error("반품 검수 자동 승인 실패 - claimId: {}", claimId, e);
            }
        }
        if (approved > 0) {
            log.info("반품 검수 자동 승인 완료 - {}건", approved);
        }
    }
}

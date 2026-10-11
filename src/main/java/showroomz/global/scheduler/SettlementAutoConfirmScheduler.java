package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.SettlementConfirmService;

import java.time.LocalDateTime;

/**
 * 정산 자동 확정 배치(44 어드민 설계서 3-2) — 확인 마감이 지났고 조정 요청이 없는 정산을 확정한다. 「마감 다음 날 00:00」을 10분
 * 주기로 집행하되 확정 시각은 규칙값(마감 다음 날 00:00)이라 배치가 늦어도 지급 예정일이 밀리지 않는다.
 *
 * <p>조정 요청과 겹치면 정산 조건부 UPDATE 가 승자를 고른다 — 행마다 별도 트랜잭션 · 한 건 실패는 로그 후 다음 건.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "auto-confirm-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementAutoConfirmScheduler {

    private static final int MAX_PER_RUN = 200;

    private final SettlementConfirmService confirmService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int confirmed = 0;
        for (Long settlementId : confirmService.findIdsToAutoConfirm(now, MAX_PER_RUN)) {
            try {
                if (confirmService.autoConfirm(settlementId, now)) {
                    confirmed++;
                }
            } catch (Exception e) {
                log.error("정산 자동 확정 실패 - settlementId: {}", settlementId, e);
            }
        }
        if (confirmed > 0) {
            log.info("정산 자동 확정 완료 - {}건", confirmed);
        }
    }
}

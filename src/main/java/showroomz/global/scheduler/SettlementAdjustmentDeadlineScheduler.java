package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentService;

import java.time.LocalDateTime;

/**
 * 정산 조정 합의 기한 배치(44 이슈 스레드 설계서 6절) — D-1 통지 · 기한 만료를 10분 주기로 집행한다.
 *
 * <p>만료 확정 시각은 규칙값(마감일 다음 날 00:00)이라 배치가 늦어도 지급 예정일이 밀리지 않는다(6-3). 동의와 겹치면 협의 행의
 * 조건부 UPDATE 가 승자를 고른다 — 행마다 별도 트랜잭션 · 한 건 실패는 로그 후 다음 건. 한 번에 {@code batch-size}건.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement.adjustment", name = "scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementAdjustmentDeadlineScheduler {

    private final SettlementAdjustmentService adjustmentService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        run(LocalDateTime.now());
    }

    /** 테스트가 시각을 넣어 부른다. 통지를 먼저 — 배치가 꺼졌다 켜지면 밀린 통지를 한 번에 처리한다(6-1 · 카드 멱등키가 중복을 막는다). */
    public void run(LocalDateTime now) {
        int notified = 0;
        for (Long adjustmentId : adjustmentService.findIdsToNotify(now)) {
            try {
                if (adjustmentService.notifyDeadline(adjustmentId, now)) {
                    notified++;
                }
            } catch (Exception e) {
                log.error("정산 조정 D-1 통지 실패 - adjustmentId: {}", adjustmentId, e);
            }
        }
        int expired = 0;
        for (Long adjustmentId : adjustmentService.findIdsToExpire(now)) {
            try {
                if (adjustmentService.expire(adjustmentId, now)) {
                    expired++;
                }
            } catch (Exception e) {
                log.error("정산 조정 기한 만료 실패 - adjustmentId: {}", adjustmentId, e);
            }
        }
        if (notified > 0 || expired > 0) {
            log.info("정산 조정 기한 배치 완료 - D-1 통지 {}건 · 만료 {}건", notified, expired);
        }
    }
}

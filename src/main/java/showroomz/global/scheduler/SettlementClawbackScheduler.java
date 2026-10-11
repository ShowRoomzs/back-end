package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.SettlementClawbackService;

import java.time.LocalDateTime;

/**
 * 차감 미회수 판정(44 어드민 설계서 3-6) — 매일 01:00. 차감 예정 중 대상 측이 다음 정산을 가질 수 없는 것(인플루언서 탈퇴 · 마켓
 * 탈퇴)을 미회수로 바꾼다. 회수 방법은 [자문대기-법률 J]라 표시만 한다(07a 차감 탭 · GNB 배지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "clawback-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementClawbackScheduler {

    private final SettlementClawbackService clawbackService;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void tick() {
        try {
            int marked = clawbackService.markUnrecoverable(LocalDateTime.now());
            if (marked > 0) {
                log.info("정산 차감 미회수 판정 - {}건", marked);
            }
        } catch (Exception e) {
            log.error("정산 차감 미회수 판정 실패", e);
        }
    }
}

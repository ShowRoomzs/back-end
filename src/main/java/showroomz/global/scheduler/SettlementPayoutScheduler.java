package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.SettlementPayoutService;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 지급 배치(44 어드민 설계서 3-3) — 영업일 {@code settlement.payout-hour}시에 예정일이 된 수취자 행을 지시한다. 비영업일에는
 * 건너뛴다(예정일 자체가 영업일 계산이라 다음 영업일에 나간다). 앞단에서 주민등록번호가 등록된 보류 건을 다시 판정한다(5-4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "payout-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementPayoutScheduler {

    private static final int MAX_PER_RUN = 200;

    private final SettlementPayoutService payoutService;
    private final BusinessCalendar businessCalendar;

    @Scheduled(cron = "0 0 ${settlement.payout-hour:10} * * *", zone = "Asia/Seoul")
    public void tick() {
        run(LocalDateTime.now());
    }

    /** 1회분 — 기준 시각을 받는다(테스트가 토요일 · 월요일을 고정한다). */
    public void run(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (!businessCalendar.isBusinessDay(today)) {
            return;
        }
        int released = payoutService.releaseBlocked(today);
        if (released > 0) {
            log.info("정산 지급 보류 해제 - {}건", released);
        }
        int distributed = 0;
        for (Long settlementId : payoutService.findSettlementIdsDue(today, MAX_PER_RUN)) {
            try {
                payoutService.distribute(settlementId, today, now);
                distributed++;
            } catch (Exception e) {
                log.error("정산 지급 실패 - settlementId: {}", settlementId, e);
            }
        }
        if (distributed > 0) {
            log.info("정산 지급 지시 완료 - {}건", distributed);
        }
        // 포트원 모드 — 올라간 정산건을 PG 와 대조하고 운영자에게 「콘솔에서 일괄 지급 실행」을 알린다(포트원 설계서 5-3). 시뮬레이터는 0건.
        payoutService.notifyReadyForExecution(today);
    }
}

package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.SettlementPayoutService;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 지급 배치(44 어드민 설계서 3-3 · 토스 설계서 5-1 · 60 이니시스 전환 계획서 D-2) — 영업일 {@code settlement.payout-hour}시. 비영업일에는
 * 건너뛴다. 앞단에서 보류 건(계좌 확인 · 서브몰 사유 · AML · 주민번호 · 세금계산서)을 다시 판정한다.
 *
 * <ul>
 *   <li>묶음 지급(시뮬레이터 · 이니시스) — 지급일이 「오늘 + 리드 영업일」(이니시스는 다음 영업일 D · 시뮬레이터는 오늘)이 되는 행을 묶음에 붙여
 *   합계로 올린다. 잔액이 모자라면 알림만 하고 전부 지시한다 — 14:00 마감 전 틱이 다시 보고 정리한다(D-9).</li>
 *   <li>정산별 지급(토스) — 예정일이 된 행을 잔액 안에서 지시하고 끝에 PG 와 대조한다(5-1 ① · 5-3).</li>
 * </ul>
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
        LocalDate today = KstDates.today(now);
        if (!businessCalendar.isBusinessDay(today)) {
            return;
        }
        int released = payoutService.releaseBlocked(today);
        if (released > 0) {
            log.info("정산 지급 보류 해제 - {}건", released);
        }
        int distributed = payoutService.distributeDue(today, now, MAX_PER_RUN);
        if (distributed > 0) {
            log.info("정산 지급 지시 완료 - {}건", distributed);
        }
        payoutService.reconcileRequested(today);
    }
}

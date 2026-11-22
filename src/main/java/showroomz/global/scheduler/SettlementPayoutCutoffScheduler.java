package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.SettlementPayoutService;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;

/**
 * 정산 지급 마감 전 틱(60_KG이니시스_지급대행_전환_계획서.md D-2′) — 영업일 {@code settlement.payout-cutoff-hour}시(기본 14시 · 이니시스 익일지급
 * 수정 마감 15시 전). ① 10:00 뒤 보류가 풀린 행 · 대기 중인 재분배 행을 지시(「해제 다음 영업일 지급」 약속) ② 확정 합계 ≠ 현재 합계인 묶음을 다시
 * 올리는 수렴(즉시 전송은 최적화이고 정합의 보증은 이 틱이다 · D-1 ④) ③ 잔액 재검사(아직 모자라면 큰 묶음부터 남기고 정리 · D-9).
 * 마감이 없는 모드(시뮬레이터 · 토스)는 서비스가 바로 돌아간다. 비영업일에는 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "payout-cutoff-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementPayoutCutoffScheduler {

    private static final int MAX_PER_RUN = 200;

    private final SettlementPayoutService payoutService;
    private final BusinessCalendar businessCalendar;

    @Scheduled(cron = "0 0 ${settlement.payout-cutoff-hour:14} * * *", zone = "Asia/Seoul")
    public void tick() {
        run(LocalDateTime.now());
    }

    /** 1회분 — 기준 시각을 받는다(테스트가 시각을 고정한다). */
    public void run(LocalDateTime now) {
        if (!businessCalendar.isBusinessDay(KstDates.today(now))) {
            return;
        }
        payoutService.cutoffTick(now, MAX_PER_RUN);
    }
}

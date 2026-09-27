package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.service.PaymentReconciliationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 일일 대사(결제 계획서 4-8) — 매일 05:00 KST, 전날 00:00~24:00 구간. <b>운영 오픈 전 필수</b> — 요청이 서버에 닿지 않은
 * 결제를 다시 보는 장치는 이것뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order", name = "reconciliation-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PaymentReconciliationService reconciliationService;

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void reconcileYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        LocalDateTime from = yesterday.atStartOfDay();
        try {
            reconciliationService.reconcile(from, from.plusDays(1), LocalDateTime.now());
        } catch (Exception e) {
            log.error("일일 대사 실패 - {}", yesterday, e);
        }
    }
}

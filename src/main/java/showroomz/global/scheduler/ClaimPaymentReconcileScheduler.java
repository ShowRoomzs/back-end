package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.api.app.claim.service.ClaimPaymentService;

import java.time.LocalDateTime;

/**
 * 클레임 결제 정리 배치(앱 클레임 설계서 6절) — 5분 주기. 앱이 콜백을 못 보낸 결제를 수렴시키고, 결제 없이 남은 교환 요청
 * 초안을 지워 선점 재고를 풀고, 끝나지 않은 결제 취소를 다시 시도한다.
 *
 * <p>초안이 재고를 묶는 상한이 {@code order.claim.payment-pending-minutes}(30분) + 이 주기다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.claim", name = "payment-reconcile-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class ClaimPaymentReconcileScheduler {

    private final ClaimPaymentService claimPaymentService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    public void tick() {
        try {
            claimPaymentService.reconcile(LocalDateTime.now());
        } catch (RuntimeException e) {
            log.error("클레임 결제 정리 배치 실패", e);
        }
    }
}

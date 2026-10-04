package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.service.OrderExpirationService;

import java.time.LocalDateTime;

/**
 * 결제 대기 만료 · 취소 수렴 배치(결제 계획서 4-4) — 1분 주기, {@link GroupBuyLifecycleScheduler}와 같은 골격
 * (단일 인스턴스 · 행마다 별도 트랜잭션 · 1회 상한 200건 · 예외는 로그 후 다음 회차).
 *
 * <p>모든 단계가 조건부 UPDATE 라 인스턴스가 둘이 돼도 이중 전이는 없고, 이중 <b>외부 호출</b>만 선점(CANCEL_REQUESTED)이 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order", name = "expiration-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationScheduler {

    private static final int MAX_PER_RUN = 200;

    private final OrderExpirationService expirationService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int expired = 0;
        for (Long orderId : expirationService.findIdsToExpire(now, MAX_PER_RUN)) {
            try {
                if (expirationService.expire(orderId, now)) {
                    expired++;
                }
            } catch (Exception e) {
                log.error("주문 만료 처리 실패 - orderId: {}", orderId, e);
            }
        }
        if (expired > 0) {
            log.info("주문 만료 처리 완료 - {}건", expired);
        }

        for (String paymentId : expirationService.findPaymentIdsToConvergeCancel(now, MAX_PER_RUN)) {
            try {
                expirationService.convergeCancel(paymentId, now);
            } catch (Exception e) {
                log.error("취소 수렴 처리 실패 - paymentId: {}", paymentId, e);
            }
        }
    }
}

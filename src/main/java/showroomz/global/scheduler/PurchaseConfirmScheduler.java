package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매확정 배치(34 설계서 3-4) — 배송완료 + 7일 자동(약관 제19조①). 돈의 시점을 바꾸는 전이라
 * 이해당사자가 만지지 않는다 — 자동 아니면 운영자다(§34-0).
 *
 * <p>반송중은 DELIVERED 가 아니므로 타이머 취소가 구조적으로 성립한다 — 별도 플래그가 없다.
 * 매시 주기면 최대 1시간 지연이 생기지만, 7일 기한에서 1시간은 소비자에게 불리하지 않은 방향(확정이 늦어진다)이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order", name = "purchase-confirm-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class PurchaseConfirmScheduler {

    private static final int MAX_PER_RUN = 500;

    private final OrderFulfillmentService fulfillmentService;
    private final OrderProperties orderProperties;

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusDays(orderProperties.getPurchaseConfirmDays());
        List<Long> ids = fulfillmentService.findIdsToConfirm(threshold, MAX_PER_RUN);
        int confirmed = 0;
        for (Long id : ids) {
            try {
                if (fulfillmentService.confirmPurchase(id, now, threshold)) {
                    confirmed++;
                }
            } catch (Exception e) {
                // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
                log.error("구매확정 처리 실패 - deliveryGroupId: {}", id, e);
            }
        }
        if (confirmed > 0) {
            log.info("구매확정 처리 완료 - {}건", confirmed);
        }
    }
}

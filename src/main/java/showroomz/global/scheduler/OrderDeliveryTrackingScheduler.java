package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 배송 추적 감시 배치(34 설계서 3-3) — 배송중·반송중 그룹을 폴링해 배송완료 자동 전환 · 반송 감지 ·
 * 배송 이상 2단(집화 확인 필요 24h · 추적 정지 7d)을 판정한다.
 *
 * <p>{@code delivery.tracker.enabled=false}(기본)면 기동하지 않는다 — 스텁 기간에는 배송중에 쌓인다.
 * <b>연동 활성화가 런칭 게이트다</b>(설계서 P6). 포트 호출은 트랜잭션 밖이고 반영은 건별 트랜잭션이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "delivery.tracker", name = "enabled", havingValue = "true")
public class OrderDeliveryTrackingScheduler {

    private final OrderFulfillmentService fulfillmentService;
    private final DeliveryTrackerPort tracker;
    private final DeliveryTrackerProperties properties;

    @Scheduled(fixedDelayString = "${delivery.tracker.poll-interval-ms:1800000}",
            initialDelayString = "${delivery.tracker.initial-delay-ms:60000}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderDeliveryGroup> targets = fulfillmentService.findTrackingTargets(properties.getBatchSize());
        for (OrderDeliveryGroup target : targets) {
            try {
                Optional<TrackSnapshot> snapshot = tracker.track(target.getCarrier(), target.getTrackingNumber());
                fulfillmentService.applyTracking(target, snapshot, now,
                        properties.getPickupAlertHours(), properties.getStallAlertDays());
            } catch (Exception e) {
                // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
                log.error("배송 추적 반영 실패 - deliveryGroupId: {}", target.getId(), e);
            }
        }
    }
}

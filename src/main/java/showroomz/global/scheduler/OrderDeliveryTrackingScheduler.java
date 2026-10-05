package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerBlockedException;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 배송 추적 감시 배치(34 설계서 3-3 · 택배 추적 설계서 4절) — 배송중·반송중 그룹을 폴링해 배송완료 자동 전환 · 반송 감지 ·
 * 배송 이상 2단(집화 확인 필요 24h · 추적 정지 7d)을 판정한다.
 *
 * <p>{@code delivery.tracker.enabled=false}(기본)면 기동하지 않는다 — 스텁 기간에는 배송중에 쌓인다.
 * <b>연동 활성화가 런칭 게이트다</b>(설계서 P6). 포트 호출은 트랜잭션 밖이고 반영은 건별 트랜잭션이다.
 *
 * <p>기동은 cron 이다 — 「하루 정확히 10회」가 연동 업체의 송장당 일 조회 한도와 맞물려 있어, 회차 소요 시간만큼
 * 밀리는 {@code fixedDelay}로는 횟수를 보장하지 못한다. 한 회차는 대상 <b>전량</b>을 id 커서로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "delivery.tracker", name = "enabled", havingValue = "true")
public class OrderDeliveryTrackingScheduler {

    private final OrderFulfillmentService fulfillmentService;
    private final DeliveryTrackerPort tracker;
    private final DeliveryTrackerProperties properties;

    @Scheduled(cron = "${delivery.tracker.poll-cron:0 0 0,6-22/2 * * *}", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        long afterId = 0L;
        while (true) {
            List<OrderDeliveryGroup> targets = fulfillmentService.findTrackingTargets(afterId, properties.getBatchSize());
            if (targets.isEmpty()) {
                return;
            }
            for (OrderDeliveryGroup target : targets) {
                try {
                    Optional<TrackSnapshot> snapshot = tracker.track(target.getCarrier(), target.getTrackingNumber());
                    fulfillmentService.applyTracking(target, snapshot, now,
                            properties.getPickupAlertHours(), properties.getStallAlertDays());
                } catch (DeliveryTrackerBlockedException e) {
                    // 키 단위 차단 — 남은 건을 불러도 전부 실패한다. 다음 회차가 처음부터 다시 돈다.
                    log.error("배송 추적 회차 중단 - 배송완료 자동 전환이 멈춘 상태입니다. deliveryGroupId: {}, cause: {}",
                            target.getId(), e.getMessage());
                    return;
                } catch (Exception e) {
                    // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
                    log.error("배송 추적 반영 실패 - deliveryGroupId: {}", target.getId(), e);
                }
                if (!pause()) {
                    return;
                }
            }
            afterId = targets.get(targets.size() - 1).getId();
        }
    }

    /** 건 사이 간격 — 인터럽트(종료)면 false 로 회차를 접는다. */
    private boolean pause() {
        if (properties.getCallGapMs() <= 0) {
            return true;
        }
        try {
            Thread.sleep(properties.getCallGapMs());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

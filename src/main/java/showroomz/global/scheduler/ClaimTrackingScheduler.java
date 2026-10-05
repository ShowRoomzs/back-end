package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerBlockedException;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 클레임 추적 감시 배치(35 설계서 3-5) — 회수 송장(소비자 → 브랜드)과 재발송 송장(브랜드 → 소비자)을 폴링한다.
 * 회수가 도착하면 「입고 확인 전」으로, 재발송이 도착하면 종결(교환 완료 / 거절 종결)로 옮기고, 스캔 이력을 저장한다 —
 * 앱의 회수 조회 · 재발송 배송 조회가 그것을 읽는다(화면이 택배 API 를 부르지 않는다).
 *
 * <p>가동 조건 · 주기 · 건 사이 간격은 주문 추적 배치와 같은 설정을 쓴다. {@code delivery.tracker.enabled=false}면
 * 기동하지 않는다 — 그 동안 회수 도착은 브랜드의 입고 확인이, 재발송 도착은 운영자 직권이 받는다.
 * 이 송장들도 연동 업체의 조회 한도를 쓴다(주문 송장과 합산).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "delivery.tracker", name = "enabled", havingValue = "true")
public class ClaimTrackingScheduler {

    private final OrderClaimService claimService;
    private final DeliveryTrackerPort tracker;
    private final DeliveryTrackerProperties properties;

    @Scheduled(cron = "${delivery.tracker.poll-cron:0 0 0,6-22/2 * * *}", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        if (pollCollections(now)) {
            pollReshipments(now);
        }
    }

    /** @return 회차를 이어가도 되는가 — 키 단위 차단이나 종료면 false */
    private boolean pollCollections(LocalDateTime now) {
        long afterId = 0L;
        while (true) {
            List<OrderClaimCollection> targets = claimService.findCollectionTrackingTargets(afterId,
                    properties.getBatchSize());
            if (targets.isEmpty()) {
                return true;
            }
            for (OrderClaimCollection target : targets) {
                DeliveryCarrier carrier = target.getCarrier();
                String trackingNumber = target.getTrackingNumber();
                if (!poll("collectionId", target.getId(), carrier, trackingNumber, snapshot ->
                        claimService.applyCollectionTracking(target.getId(), carrier, trackingNumber, snapshot, now))) {
                    return false;
                }
            }
            afterId = targets.get(targets.size() - 1).getId();
        }
    }

    private boolean pollReshipments(LocalDateTime now) {
        long afterId = 0L;
        while (true) {
            List<OrderClaim> targets = claimService.findReshipTrackingTargets(afterId, properties.getBatchSize());
            if (targets.isEmpty()) {
                return true;
            }
            for (OrderClaim target : targets) {
                DeliveryCarrier carrier = target.getReshipCarrier();
                String trackingNumber = target.getReshipTrackingNumber();
                if (!poll("claimId", target.getId(), carrier, trackingNumber, snapshot ->
                        claimService.applyReshipTracking(target.getId(), carrier, trackingNumber, snapshot, now))) {
                    return false;
                }
            }
            afterId = targets.get(targets.size() - 1).getId();
        }
    }

    private interface Applier {
        void apply(TrackSnapshot snapshot);
    }

    /** 한 건 조회 · 반영 — 포트 호출은 트랜잭션 밖이고 반영은 건별 트랜잭션이다. */
    private boolean poll(String idName, Long id, DeliveryCarrier carrier, String trackingNumber, Applier applier) {
        try {
            Optional<TrackSnapshot> snapshot = tracker.track(carrier, trackingNumber);
            snapshot.ifPresent(applier::apply);
        } catch (DeliveryTrackerBlockedException e) {
            // 키 단위 차단 — 남은 건을 불러도 전부 실패한다. 다음 회차가 처음부터 다시 돈다.
            log.error("클레임 추적 회차 중단 - {}: {}, cause: {}", idName, id, e.getMessage());
            return false;
        } catch (Exception e) {
            // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
            log.error("클레임 추적 반영 실패 - {}: {}", idName, id, e);
        }
        return pause();
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

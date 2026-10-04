package showroomz.global.delivery.tracker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import showroomz.domain.order.type.DeliveryCarrier;

import java.util.Optional;

/**
 * 연동 전 스텁(34 설계서 0-4) — 형식 검증은 UNAVAILABLE(생략 기록), 추적은 empty.
 * 이 스텁이 붙어 있는 동안 배송완료 자동 전환·배송 이상 감지·반송 감지가 전부 멈춘다 — 연동 활성화가 런칭 게이트다(P6).
 */
@Component
@ConditionalOnProperty(prefix = "delivery.tracker", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDeliveryTracker implements DeliveryTrackerPort {

    @Override
    public ValidationResult validateInvoice(DeliveryCarrier carrier, String trackingNumber) {
        return ValidationResult.UNAVAILABLE;
    }

    @Override
    public Optional<TrackSnapshot> track(DeliveryCarrier carrier, String trackingNumber) {
        return Optional.empty();
    }
}

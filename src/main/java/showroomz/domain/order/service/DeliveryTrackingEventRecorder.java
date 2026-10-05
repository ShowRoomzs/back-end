package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.entity.DeliveryTrackingEvent;
import showroomz.domain.order.repository.DeliveryTrackingEventRepository;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;

import java.util.List;

/**
 * 스캔 이력 저장(앱 클레임 설계서 1-6) — 연동 업체가 매번 전체 이력을 주므로 <b>저장된 건수 뒤의 이력만</b> 넣는다.
 * 주문 송장 · 회수 송장 · 재발송 송장의 추적 배치가 함께 쓴다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryTrackingEventRecorder {

    private static final int TEXT_MAX = 100;

    private final DeliveryTrackingEventRepository repository;

    /** @param events 시간순(오래된 것부터) 전체 이력 */
    @Transactional
    public void record(DeliveryCarrier carrier, String trackingNumber, List<TrackEvent> events) {
        if (carrier == null || trackingNumber == null || events == null || events.isEmpty()) {
            return;
        }
        int stored = (int) repository.countByCarrierAndTrackingNumber(carrier, trackingNumber);
        for (int seq = stored; seq < events.size(); seq++) {
            TrackEvent event = events.get(seq);
            repository.save(DeliveryTrackingEvent.builder()
                    .carrier(carrier)
                    .trackingNumber(trackingNumber)
                    .seq(seq)
                    .occurredAt(event.occurredAt())
                    .location(cut(event.location()))
                    .description(cut(event.description()))
                    .level(event.level())
                    .build());
        }
    }

    private static String cut(String text) {
        return text == null || text.length() <= TEXT_MAX ? text : text.substring(0, TEXT_MAX);
    }
}

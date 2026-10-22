package showroomz.api.admin.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.home.dto.AdminHomeDto;
import showroomz.api.app.order.service.PaymentHealthService;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.health.IntegrationHealthRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 연동 상태 3종(1017 설계서 6-3) — PG · 택배 추적 · 전자서명.
 *
 * <ul>
 *   <li>PG — 결제 운영 지표({@link PaymentHealthService#measure}, 읽기 전용 · 경고 발송 없음) 중 임계를 넘은 것이 있으면 DEGRADED</li>
 *   <li>택배 추적 — 꺼져 있으면 DISABLED, 호출 기록({@link IntegrationHealthRegistry})이 장애면 DEGRADED · 배송 예외 판정 일시 중지</li>
 *   <li>전자서명 — 연동이 없다(수동). 항상 NOT_INTEGRATED</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminIntegrationStatusReader {

    static final String DELIVERY_QUEUE = "배송 예외 확인";

    private final PaymentHealthService paymentHealthService;
    private final IntegrationHealthRegistry healthRegistry;
    private final DeliveryTrackerProperties trackerProperties;

    public List<AdminHomeDto.Integration> read(LocalDateTime now) {
        return List.of(payment(now), deliveryTracking(now),
                new AdminHomeDto.Integration("E_SIGN", "전자서명", "NOT_INTEGRATED", null, "수동", List.of()));
    }

    private AdminHomeDto.Integration payment(LocalDateTime now) {
        List<PaymentHealthService.Metric> exceeded = paymentHealthService.measure(now).stream()
                .filter(PaymentHealthService.Metric::exceeded).toList();
        if (exceeded.isEmpty()) {
            return new AdminHomeDto.Integration("PG", "PG", "OK", null, null, List.of());
        }
        String message = exceeded.stream().map(m -> m.name() + " " + m.value() + "건").collect(Collectors.joining(" · "));
        return new AdminHomeDto.Integration("PG", "PG", "DEGRADED", null, message, List.of("환불 실패"));
    }

    private AdminHomeDto.Integration deliveryTracking(LocalDateTime now) {
        if (!trackerProperties.isEnabled()) {
            return new AdminHomeDto.Integration("DELIVERY_TRACKING", "택배 추적", "DISABLED", null, "연동 꺼짐", List.of());
        }
        IntegrationHealthRegistry.TrackingState state = healthRegistry.trackingState(now);
        if (!state.degraded()) {
            return new AdminHomeDto.Integration("DELIVERY_TRACKING", "택배 추적", "OK", null, null, List.of());
        }
        return new AdminHomeDto.Integration("DELIVERY_TRACKING", "택배 추적", "DEGRADED", state.since(), state.message(),
                List.of(DELIVERY_QUEUE));
    }
}

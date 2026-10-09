package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDateTime;

/**
 * 추적 정지 종결 조건(41 보고 3번 · 권고 3) — 배송중 ∧ 추적 정지 배지 ∧ 마지막 추적(없으면 발송)으로부터 N일 경과. <b>조건</b>이지
 * 상태가 아니고, 자동으로 처리하지 않는다 — 플랫폼이 단정하지 않는다(§37-4). 운영자가 06a 레일에서 분실 · 배송완료 중 하나를 고른다.
 * 06a 조회 · 커맨드 · 06d 가 같은 판정을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class StalledPolicy {

    private final OrderProperties orderProperties;

    public boolean isResolvable(OrderDeliveryGroup group, LocalDateTime now) {
        if (group.getFulfillmentStatus() != FulfillmentStatus.SHIPPING || group.getTrackingAlert() != TrackingAlert.STALLED) {
            return false;
        }
        LocalDateTime basis = group.getLastTrackingAt() != null ? group.getLastTrackingAt() : group.getShippedAt();
        return basis != null && !basis.plusDays(days()).isAfter(now);
    }

    public int days() {
        return orderProperties.getException().getStalledResolveDays();
    }
}

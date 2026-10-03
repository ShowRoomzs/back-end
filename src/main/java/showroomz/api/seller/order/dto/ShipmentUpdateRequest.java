package showroomz.api.seller.order.dto;

import jakarta.validation.constraints.NotNull;
import showroomz.domain.order.type.DeliveryCarrier;

/** 송장 수정(§34-6) — 배송완료 전까지 · 반송중 불가 · 수정 이력 기록. 형식은 맞지만 다른 주문의 송장을 붙여넣은 경우가 남는다. */
public record ShipmentUpdateRequest(@NotNull DeliveryCarrier carrier, @NotNull String trackingNumber) {
}

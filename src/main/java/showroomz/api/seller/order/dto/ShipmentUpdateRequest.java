package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import showroomz.domain.order.type.DeliveryCarrier;

/** 송장 수정(§34-6) — 배송완료 전까지 · 반송중 불가 · 수정 이력 기록. 형식은 맞지만 다른 주문의 송장을 붙여넣은 경우가 남는다. */
public record ShipmentUpdateRequest(
        @Schema(description = "택배사 — 11종", example = "HANJIN") @NotNull DeliveryCarrier carrier,
        @Schema(description = "송장번호 — 숫자 외 문자는 제거 후 판정한다", example = "512345678901") @NotNull String trackingNumber
) {
}

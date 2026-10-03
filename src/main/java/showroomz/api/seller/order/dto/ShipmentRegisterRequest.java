package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.order.type.DeliveryCarrier;

import java.util.List;

/**
 * 송장 등록 확정(§34-5) — 셀 입력은 클라이언트 임시값이고 이 호출이 유일한 확정 지점이다.
 * 배송중 전환은 소비자에게 송장이 전달되는 사건이다.
 */
public record ShipmentRegisterRequest(@NotEmpty @Size(max = 500) @Valid List<Row> rows) {

    public record Row(
            @NotNull Long deliveryGroupId,
            @Schema(description = "11종 외 값은 400 — 자유 입력 없음") @NotNull DeliveryCarrier carrier,
            @NotNull String trackingNumber
    ) {
    }
}

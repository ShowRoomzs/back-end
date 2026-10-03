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
public record ShipmentRegisterRequest(
        @Schema(description = "등록할 행 — 1~500행") @NotEmpty @Size(max = 500) @Valid List<Row> rows
) {

    @Schema(name = "ShipmentRegisterRow")
    public record Row(
            @Schema(description = "하위주문 id", example = "1024") @NotNull Long deliveryGroupId,
            @Schema(description = "택배사 — 11종 외 값은 400 · 자유 입력 없음", example = "CJ") @NotNull DeliveryCarrier carrier,
            @Schema(description = "송장번호 — 숫자 외 문자(하이픈·공백)는 제거 후 판정한다. 제거 후 빈 값이면 에러 없이 제외",
                    example = "640012345678") @NotNull String trackingNumber
    ) {
    }
}

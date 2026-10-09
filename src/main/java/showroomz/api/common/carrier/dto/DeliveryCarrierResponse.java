package showroomz.api.common.carrier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.DeliveryCarrier;

/** 택배사 선택지 1행 — 출고 · 회수 · 재발송 송장이 같은 목록을 쓴다. */
public record DeliveryCarrierResponse(
        @Schema(description = "택배사 코드 — 송장 등록 요청의 carrier 로 보낸다", example = "CJ") DeliveryCarrier code,
        @Schema(description = "표시명", example = "CJ대한통운") String label
) {
    public static DeliveryCarrierResponse of(DeliveryCarrier carrier) {
        return new DeliveryCarrierResponse(carrier, carrier.getLabel());
    }
}

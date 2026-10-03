package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 다건 액션의 부분 성공 응답(34 설계서 2절) — 조건부 UPDATE 0행으로 떨어진 행만 사유와 함께 제외하고
 * 나머지는 진행한다. 오류 행 때문에 나머지를 막지 않는다.
 */
public record BatchActionResponse(int succeeded, List<Skipped> skipped) {

    public record Skipped(
            Long deliveryGroupId,
            @Schema(description = "ORDER_STATE_CHANGED · CANCEL_REQUEST_PENDING_EXISTS · INVOICE_DUPLICATE · INVOICE_FORMAT_INVALID 등") String code,
            String message
    ) {
    }
}

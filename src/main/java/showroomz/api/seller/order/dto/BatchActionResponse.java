package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 다건 액션의 부분 성공 응답(34 설계서 2절) — 조건부 UPDATE 0행으로 떨어진 행만 사유와 함께 제외하고
 * 나머지는 진행한다. 오류 행 때문에 나머지를 막지 않는다.
 */
public record BatchActionResponse(
        @Schema(description = "처리된 하위주문 수", example = "2") int succeeded,
        @Schema(description = "제외된 행과 사유 — 전부 성공이면 빈 배열") List<Skipped> skipped
) {

    @Schema(name = "SellerOrderBatchSkipped")
    public record Skipped(
            @Schema(description = "제외된 하위주문 id", example = "1025") Long deliveryGroupId,
            @Schema(description = "ORDER_STATE_CHANGED · CANCEL_REQUEST_PENDING_EXISTS · INVOICE_DUPLICATE · INVOICE_FORMAT_INVALID · ORDER_GROUP_NOT_FOUND",
                    example = "CANCEL_REQUEST_PENDING_EXISTS") String code,
            @Schema(description = "결과 배너에 그대로 쓰는 안내 문구",
                    example = "검토 중인 취소 요청이 있습니다. 요청을 먼저 처리해 주세요.") String message
    ) {
    }
}

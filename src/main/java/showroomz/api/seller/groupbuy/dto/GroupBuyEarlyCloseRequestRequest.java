package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.EarlyCloseReasonCode;

/** 조기 마감 요청(C3). ETC면 메모 필수. */
@Schema(description = "조기 마감 요청")
public record GroupBuyEarlyCloseRequestRequest(

        @Schema(description = "STOCK_OUT(재고 소진) · TARGET_REACHED(판매 목표 달성) · ETC", example = "STOCK_OUT")
        @NotNull
        EarlyCloseReasonCode reasonCode,

        @Schema(description = "메모 — ETC면 필수 · 1,000자", nullable = true)
        @Size(max = 1000)
        String memo
) {
}

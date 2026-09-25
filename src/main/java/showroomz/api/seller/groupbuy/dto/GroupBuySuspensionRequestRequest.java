package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.SuspensionReasonCode;

/** 공구 중단 요청(C2 · C4). 재고 소진은 중단이 아니라 조기 마감이다. ETC면 메모 필수. */
@Schema(description = "공구 중단 요청")
public record GroupBuySuspensionRequestRequest(

        @Schema(description = "QUALITY_ISSUE · PRICE_TERMS_ERROR · NEGOTIATION_BROKEN · ETC", example = "QUALITY_ISSUE")
        @NotNull
        SuspensionReasonCode reasonCode,

        @Schema(description = "메모 — ETC면 필수 · 1,000자", nullable = true)
        @Size(max = 1000)
        String memo
) {
}

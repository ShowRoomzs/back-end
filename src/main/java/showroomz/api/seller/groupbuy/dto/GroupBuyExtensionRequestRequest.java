package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 기간 연장 요청(C1). 새 종료 = 현재 종료 + N일 · 시각은 그대로다. */
@Schema(description = "기간 연장 요청")
public record GroupBuyExtensionRequestRequest(

        @Schema(description = "연장 일수 — 연장 후 총 기간이 30일 이하여야 한다", example = "7")
        @NotNull @Min(1)
        Integer extensionDays,

        @Schema(description = "사유 — 선택 · 300자", example = "수요가 예상보다 많음", nullable = true)
        @Size(max = 300)
        String reason
) {
}

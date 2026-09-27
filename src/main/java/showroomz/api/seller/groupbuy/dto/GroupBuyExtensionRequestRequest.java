package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 기간 연장 요청(C1). 새 종료 = 현재 종료 + N일 · 시각은 그대로다. */
@Schema(description = "기간 연장 요청 — 공구당 1회 · 인플루언서가 수락해야 종료일이 바뀐다")
public record GroupBuyExtensionRequestRequest(

        @Schema(description = "연장 일수 — 1 이상 · 상세의 extension.maxDays 이하(연장 후 총 기간 30일 이하, 양끝 포함). "
                + "새 종료 = 현재 종료 + N일 · 시각은 그대로", example = "7", minimum = "1")
        @NotNull @Min(1)
        Integer extensionDays,

        @Schema(description = "인플루언서에게 보이는 연장 사유 — 선택 · 300자", example = "수요가 예상보다 많음", nullable = true)
        @Size(max = 300)
        String reason
) {
}

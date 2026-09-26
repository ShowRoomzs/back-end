package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/** 탭 카운트 + GNB 배지(설계서 4-3). GNB 배지는 다른 화면에서도 폴링되므로 목록과 분리한다. */
@Schema(description = "공구 탭 카운트 · GNB 배지")
public record GroupBuySummaryResponse(

        @Schema(description = "탭 코드별 건수 — ALL · PREPARING · READY · IN_PROGRESS · ENDED · SUSPENDED",
                example = "{\"ALL\":11,\"PREPARING\":3,\"READY\":1,\"IN_PROGRESS\":3,\"ENDED\":2,\"SUSPENDED\":2}")
        Map<String, Long> tabCounts,

        @Schema(description = "브랜드가 지금 조치해야 하는 건수 — 물량 확인 대기 · 소명 가능 · 이행 확인 대기", example = "2")
        long actionRequiredCount
) {
}

package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 반품·교환 요약(35 설계서 4-2) — KPI 4칸 + 탭 카운트 7종 + 유형 카운트. 전부 <b>검색 조건과 무관한 전체 기준</b>이고
 * 기간 필터도 타지 않는다. 결제 대기(접수 전) 건은 어디에도 세지 않는다.
 */
public record SellerClaimSummaryResponse(
        Kpi kpi,
        @Schema(description = "탭 코드 → 건수. ALL 은 여섯 탭의 합이다",
                example = "{\"ALL\": 12, \"COLLECT_WAIT\": 3, \"COLLECTING\": 2, \"INSPECTION\": 2, \"RESHIP\": 1, \"REJECT_HOLD\": 1, \"DONE\": 3}")
        Map<String, Long> tabCounts,
        @Schema(description = "유형 → 건수", example = "{\"RETURN\": 7, \"EXCHANGE\": 5}") Map<String, Long> typeCounts
) {

    @Schema(name = "SellerClaimKpi")
    public record Kpi(
            @Schema(description = "회수 대기", example = "3") long collectWait,
            @Schema(description = "입고·검수 — 입고 확인 전 + 검수 대기", example = "2") long inspection,
            @Schema(description = "재발송 대기", example = "1") long reship,
            @Schema(description = "기한 초과 — 회수 대기 방치 + 검수 기한 경과. 0이면 FE 가 경고 톤을 뺀다", example = "1") long overdue
    ) {
    }
}

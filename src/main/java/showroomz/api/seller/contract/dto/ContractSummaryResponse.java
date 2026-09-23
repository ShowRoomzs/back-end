package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 탭 카운트 + GNB 배지(설계서 4-4).
 *
 * <p>목록 API와 분리한다 — GNB 배지는 계약 화면이 아닌 곳에서도 폴링되는데,
 * 목록에 카운트를 얹으면 배지 하나 때문에 매번 20건을 조회하게 된다.
 */
@Schema(description = "계약 탭 카운트 · GNB 배지")
public record ContractSummaryResponse(

        @Schema(description = "탭별 건수 — 키는 ALL/DRAFT/REVIEW/SIGNING/CONCLUDED/CLOSED",
                example = "{\"ALL\":14,\"DRAFT\":2,\"REVIEW\":2,\"SIGNING\":2,\"CONCLUDED\":5,\"CLOSED\":3}")
        Map<String, Long> tabCounts,

        @Schema(description = "브랜드가 지금 조치해야 하는 건수(GNB 배지). "
                + "검토 반려 + 「상대만 서명 완료」(B4c)만 센다 — 검토 대기·체결 처리 대기는 공이 상대에게 있는 "
                + "정상 대기라 넣지 않는다. 브랜드가 할 수 없는 일로 배지가 켜지면 안 된다",
                example = "1")
        long actionRequiredCount
) {
}

package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.global.dto.PaginationInfo;

import java.util.List;

/**
 * 목록 응답 (§23-2).
 *
 * <p>탭·필터 건수는 검색어·선택 필터와 무관하게 <b>마켓 전체 기준</b>으로 센다.
 * 검색 결과가 없어도 필터 카운트는 그대로 두는 화면 규칙이 이 계산에 기대고 있다.
 */
@Getter
@Builder
@Schema(description = "파트너센터 문의 목록 응답")
public class SellerInquiryListResponse {

    @Schema(description = "현재 탭·필터·검색 기준 총 건수 — 툴바의 `총 N건`", example = "16")
    private long totalCount;

    @Schema(description = "마켓 전체 답변대기 건수 — 툴바의 `답변대기 N건` · GNB 배지", example = "3")
    private long waitingCount;

    private List<SellerInquiryDto> content;

    private PaginationInfo pageInfo;

    @Schema(description = "상태 탭 건수 — 마켓 전체 기준")
    private StatusCounts statusCounts;

    @Schema(description = "문의 유형 필터 건수 — 마켓 전체 기준. 유형 코드별 건수")
    private List<FilterCount> typeCounts;

    @Schema(description = "공개여부 필터 건수 — 마켓 전체 기준")
    private List<FilterCount> visibilityCounts;

    @Getter
    @Builder
    @Schema(description = "상태 탭 건수")
    public static class StatusCounts {
        @Schema(example = "16")
        private long all;
        @Schema(example = "3")
        private long waiting;
        @Schema(example = "11")
        private long answered;
        @Schema(example = "1")
        private long deleteRequested;
        @Schema(example = "1")
        private long deleted;
    }

    @Getter
    @Builder
    @Schema(description = "필터 항목 1건 — 코드·라벨·건수를 함께 내려 화면에서 건수를 병기한다")
    public static class FilterCount {
        @Schema(example = "OPTION")
        private String code;
        @Schema(example = "옵션")
        private String label;
        @Schema(example = "3")
        private long count;
    }
}

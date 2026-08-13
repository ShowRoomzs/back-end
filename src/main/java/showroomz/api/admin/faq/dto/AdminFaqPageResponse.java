package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.global.dto.PaginationInfo;

import java.util.List;

@Getter
@Builder
@Schema(description = "FAQ 목록 응답 (기획 §19-2) — 목록 + 카테고리 탭 건수")
public class AdminFaqPageResponse {

    @Schema(description = "FAQ 목록")
    private List<AdminFaqListResponse> content;

    @Schema(description = "페이징 정보 (totalResults = 툴바의 '총 N건')")
    private PaginationInfo pageInfo;

    @Schema(description = "카테고리 탭 건수 (전체 + 5종, 검색어 적용 기준)")
    private List<AdminFaqCategoryCount> categoryCounts;
}

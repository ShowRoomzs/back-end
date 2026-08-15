package showroomz.api.admin.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.global.dto.PaginationInfo;

import java.util.List;

@Getter
@Builder
@Schema(description = "문서 목록 응답 (기획 §21-3) — 목록 + 유형 탭 건수 + 툴바 건수")
public class AdminTermsPageResponse {

    @Schema(description = "문서 목록 (유형 순 → 등록 순)")
    private List<AdminTermsListResponse> content;

    @Schema(description = "페이징 정보 (totalResults = 툴바의 '총 N건')")
    private PaginationInfo pageInfo;

    @Schema(description = "유형 탭 건수 (전체 + 3종, 검색어 적용 기준)")
    private List<AdminTermsTypeCount> typeCounts;

    @Schema(description = "툴바의 '시행 예정 N건' (현재 탭·검색어 기준)", example = "1")
    private long scheduledCount;

    @Schema(description = "툴바의 '구버전 N건' (현재 탭·검색어 기준)", example = "1")
    private long supersededCount;
}

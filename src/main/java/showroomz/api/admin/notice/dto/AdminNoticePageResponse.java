package showroomz.api.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.global.dto.PaginationInfo;

import java.util.List;

@Getter
@Builder
@Schema(description = "공지 목록 응답 (기획 §20-3) — 목록 + 상태 탭 건수 + 툴바 건수")
public class AdminNoticePageResponse {

    @Schema(description = "공지 목록 (중요 고정 상단 + 등록일 최신순)")
    private List<AdminNoticeListResponse> content;

    @Schema(description = "페이징 정보 (totalResults = 툴바의 '총 N건')")
    private PaginationInfo pageInfo;

    @Schema(description = "상태 탭 건수 (전체 + 2종, 검색어 적용 기준)")
    private List<AdminNoticeStatusCount> statusCounts;

    @Schema(description = "툴바의 '중요 N건' (현재 탭·검색어 기준)", example = "2")
    private long pinnedCount;
}

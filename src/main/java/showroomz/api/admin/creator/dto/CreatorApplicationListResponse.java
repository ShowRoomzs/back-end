package showroomz.api.admin.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import showroomz.global.dto.PageResponse;

import java.util.List;

@Getter
@Schema(description = "크리에이터 지원서 목록 응답 (글로벌 PageResponse + 상태별 건수)")
public class CreatorApplicationListResponse extends PageResponse<CreatorApplicationResponse> {

    @Schema(description = "상태별 지원서 건수 (검색어 반영, 상태 필터 미반영)")
    private final StatusCounts statusCounts;

    public CreatorApplicationListResponse(
            List<CreatorApplicationResponse> content,
            Page<?> page,
            StatusCounts statusCounts) {
        super(content, page);
        this.statusCounts = statusCounts;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상태별 지원서 건수")
    public static class StatusCounts {

        @Schema(description = "전체 건수", example = "42")
        private long all;

        @Schema(description = "심사대기 건수", example = "10")
        private long pending;

        @Schema(description = "승인 건수", example = "25")
        private long approved;

        @Schema(description = "반려 건수", example = "7")
        private long rejected;
    }
}

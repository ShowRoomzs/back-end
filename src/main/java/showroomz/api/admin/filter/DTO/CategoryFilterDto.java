package showroomz.api.admin.filter.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class CategoryFilterDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "카테고리-필터 동기화 요청")
    public static class SyncRequest {
        @Schema(description = "매핑할 필터 목록")
        private List<FilterMapping> filters;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "필터 매핑 정보")
    public static class FilterMapping {
        @NotNull
        @Schema(description = "필터 ID", example = "1")
        private Long filterId;

        @Schema(description = "선택된 값 ID 목록 (비어 있으면 전체 활성 값)", example = "[1,2]")
        private List<Long> selectedValueIds;
    }
}

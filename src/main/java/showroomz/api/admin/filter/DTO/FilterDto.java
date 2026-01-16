package showroomz.api.admin.filter.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.filter.type.FilterCondition;
import showroomz.domain.filter.type.FilterType;

import java.util.List;

public class FilterDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "필터 생성 요청")
    public static class CreateFilterRequest {
        @NotBlank
        @Schema(description = "필터 키 (API 파라미터명)", example = "gender")
        private String filterKey;

        @NotBlank
        @Schema(description = "노출명", example = "성별")
        private String label;

        @NotNull
        @Schema(description = "필터 타입", example = "CHECKBOX")
        private FilterType filterType;

        @NotNull
        @Schema(description = "조건", example = "OR")
        private FilterCondition condition;

        @Schema(description = "정렬 순서", example = "1")
        private Integer sortOrder = 0;

        @Schema(description = "활성화 여부", example = "true")
        private Boolean isActive = true;

        @Schema(description = "필터 값 목록")
        private List<FilterValueRequest> values;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "필터 수정 요청")
    public static class UpdateFilterRequest {
        @Schema(description = "노출명", example = "성별")
        private String label;

        @Schema(description = "필터 타입", example = "CHECKBOX")
        private FilterType filterType;

        @Schema(description = "조건", example = "OR")
        private FilterCondition condition;

        @Schema(description = "정렬 순서", example = "1")
        private Integer sortOrder;

        @Schema(description = "활성화 여부", example = "true")
        private Boolean isActive;

        @Schema(description = "필터 값 목록 (전체 교체)")
        private List<FilterValueRequest> values;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "필터 응답")
    public static class FilterResponse {
        @Schema(description = "필터 ID", example = "1")
        private Long id;

        @Schema(description = "필터 키", example = "gender")
        private String filterKey;

        @Schema(description = "노출명", example = "성별")
        private String label;

        @Schema(description = "필터 타입", example = "CHECKBOX")
        private FilterType filterType;

        @Schema(description = "조건", example = "OR")
        private FilterCondition condition;

        @Schema(description = "정렬 순서", example = "1")
        private Integer sortOrder;

        @Schema(description = "활성화 여부", example = "true")
        private Boolean isActive;

        @Schema(description = "필터 값 목록")
        private List<FilterValueResponse> values;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "필터 값 요청")
    public static class FilterValueRequest {
        @NotBlank
        @Schema(description = "검색 값", example = "MALE")
        private String value;

        @NotBlank
        @Schema(description = "노출명", example = "남성")
        private String label;

        @Schema(description = "추가 정보", example = "#FFFFFF")
        private String extra;

        @Schema(description = "정렬 순서", example = "1")
        private Integer sortOrder = 0;

        @Schema(description = "활성화 여부", example = "true")
        private Boolean isActive = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "필터 값 응답")
    public static class FilterValueResponse {
        @Schema(description = "값 ID", example = "1")
        private Long id;

        @Schema(description = "검색 값", example = "MALE")
        private String value;

        @Schema(description = "노출명", example = "남성")
        private String label;

        @Schema(description = "추가 정보", example = "#FFFFFF")
        private String extra;

        @Schema(description = "정렬 순서", example = "1")
        private Integer sortOrder;

        @Schema(description = "활성화 여부", example = "true")
        private Boolean isActive;
    }
}

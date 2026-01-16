package showroomz.api.admin.category.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class CategoryDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "카테고리 생성 요청")
    public static class CreateCategoryRequest {
        @NotBlank(message = "카테고리명은 필수 입력값입니다.")
        @Size(max = 255, message = "카테고리명은 최대 255자까지 입력 가능합니다.")
        @Schema(description = "카테고리명", example = "옷")
        private String name;

        @Min(value = 0, message = "순서는 0 이상이어야 합니다.")
        @Schema(description = "카테고리 순서", example = "1")
        private Integer order;

        @Size(max = 2048, message = "아이콘 URL은 최대 2048자까지 입력 가능합니다.")
        @Schema(description = "아이콘 URL", example = "https://example.com/icon/clothing.png")
        private String iconUrl;

        @Schema(description = "부모 카테고리 ID (2depth 이상 카테고리 생성 시 필수)", example = "2")
        private Long parentId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "카테고리 생성 응답")
    public static class CreateCategoryResponse {
        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "카테고리명", example = "옷")
        private String name;

        @Schema(description = "카테고리 순서", example = "1")
        private Integer order;

        @Schema(description = "부모 카테고리 ID", example = "2")
        private Long parentId;

        @Schema(description = "응답 메시지", example = "카테고리가 성공적으로 생성되었습니다.")
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "카테고리 수정 요청")
    public static class UpdateCategoryRequest {
        @Size(max = 255, message = "카테고리명은 최대 255자까지 입력 가능합니다.")
        @Schema(description = "카테고리명", example = "옷")
        private String name;

        @Min(value = 0, message = "순서는 0 이상이어야 합니다.")
        @Schema(description = "카테고리 순서", example = "1")
        private Integer order;

        @Size(max = 2048, message = "아이콘 URL은 최대 2048자까지 입력 가능합니다.")
        @Schema(description = "아이콘 URL", example = "https://example.com/icon/clothing.png")
        private String iconUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "카테고리 조회 응답")
    public static class CategoryResponse {
        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "카테고리명", example = "옷")
        private String name;

        @Schema(description = "카테고리 순서", example = "1")
        private Integer order;

        @Schema(description = "아이콘 URL", example = "https://example.com/icon/clothing.png")
        private String iconUrl;

        @Schema(description = "부모 카테고리 ID", example = "1")
        private Long parentId;

        @Schema(description = "연결된 필터 목록")
        private java.util.List<FilterInfo> filters;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "필터 정보")
    public static class FilterInfo {
        @Schema(description = "필터 ID", example = "1")
        private Long id;

        @Schema(description = "필터 키", example = "gender")
        private String filterKey;

        @Schema(description = "노출명", example = "성별")
        private String label;

        @Schema(description = "필터 타입", example = "CHECKBOX")
        private showroomz.domain.filter.type.FilterType filterType;

        @Schema(description = "조건", example = "OR")
        private showroomz.domain.filter.type.FilterCondition condition;

        @Schema(description = "정렬 순서", example = "1")
        private Integer sortOrder;

        @Schema(description = "활성화 여부", example = "true")
        private Boolean isActive;

        @Schema(description = "필터 값 목록")
        private java.util.List<FilterValueInfo> values;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "필터 값 정보")
    public static class FilterValueInfo {
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "카테고리 수정 응답")
    public static class UpdateCategoryResponse {
        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "카테고리명", example = "옷")
        private String name;

        @Schema(description = "카테고리 순서", example = "1")
        private Integer order;

        @Schema(description = "아이콘 URL", example = "https://example.com/icon/clothing.png")
        private String iconUrl;

        @Schema(description = "부모 카테고리 ID", example = "1")
        private Long parentId;

        @Schema(description = "응답 메시지", example = "카테고리가 성공적으로 수정되었습니다.")
        private String message;
    }
}


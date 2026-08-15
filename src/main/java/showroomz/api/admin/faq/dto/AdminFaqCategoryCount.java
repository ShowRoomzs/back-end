package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.cs.type.CsCategory;

@Getter
@AllArgsConstructor
@Schema(description = "카테고리 탭 건수 (기획 §19-2) — 전체(ALL) 포함 6종")
public class AdminFaqCategoryCount {

    @Schema(description = "카테고리 코드 (전체 탭은 ALL)", example = "DELIVERY")
    private String category;

    @Schema(description = "카테고리 표시명", example = "배송")
    private String displayName;

    @Schema(description = "건수", example = "1")
    private long count;

    public static AdminFaqCategoryCount of(CsCategory category, long count) {
        return new AdminFaqCategoryCount(category.name(), category.getDescription(), count);
    }

    /** 전체 탭 — 분류 값이 아니라 필터 없음을 뜻하는 의사값이다 */
    public static AdminFaqCategoryCount all(long count) {
        return new AdminFaqCategoryCount(CsCategory.ALL_CODE, CsCategory.ALL_LABEL, count);
    }
}

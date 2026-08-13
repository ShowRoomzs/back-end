package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.faq.type.FaqCategory;

@Getter
@AllArgsConstructor
@Schema(description = "카테고리 탭 건수 (기획 §19-2) — ALL(전체) 포함 6종")
public class AdminFaqCategoryCount {

    @Schema(description = "카테고리", example = "DELIVERY")
    private FaqCategory category;

    @Schema(description = "카테고리 표시명", example = "배송")
    private String displayName;

    @Schema(description = "건수", example = "1")
    private long count;

    public static AdminFaqCategoryCount of(FaqCategory category, long count) {
        return new AdminFaqCategoryCount(category, category.getDisplayName(), count);
    }
}

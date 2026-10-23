package showroomz.api.app.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.cs.type.CsCategory;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FaqCategoryItem {

    @Schema(description = "카테고리 코드 — ALL(전체 · 필터 없음) · DELIVERY · CANCEL_EXCHANGE_RETURN · ORDER_PAYMENT · SERVICE · ACCOUNT. "
            + "ALL은 저장용 카테고리로 쓸 수 없다", example = "DELIVERY")
    private String key;
    @Schema(description = "카테고리 한글 표시명", example = "배송")
    private String description;

    public static FaqCategoryItem from(CsCategory category) {
        return new FaqCategoryItem(category.name(), category.getDescription());
    }

    /** 전체 칩 — 분류 값이 아니라 필터 없음을 뜻하는 의사값이다 */
    public static FaqCategoryItem all() {
        return new FaqCategoryItem(CsCategory.ALL_CODE, CsCategory.ALL_LABEL);
    }
}

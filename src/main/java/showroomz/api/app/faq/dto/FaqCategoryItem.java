package showroomz.api.app.faq.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.cs.type.CsCategory;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FaqCategoryItem {

    private String key;
    private String description;

    public static FaqCategoryItem from(CsCategory category) {
        return new FaqCategoryItem(category.name(), category.getDescription());
    }

    /** 전체 칩 — 분류 값이 아니라 필터 없음을 뜻하는 의사값이다 */
    public static FaqCategoryItem all() {
        return new FaqCategoryItem(CsCategory.ALL_CODE, CsCategory.ALL_LABEL);
    }
}

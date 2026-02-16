package showroomz.api.app.faq.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.faq.type.FaqCategory;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FaqCategoryItem {

    private String key;
    private String description;

    public static FaqCategoryItem from(FaqCategory category) {
        return new FaqCategoryItem(category.name(), category.getDisplayName());
    }
}

package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductListSortType {
    CREATED_AT("등록일순"),
    MODIFIED_AT("수정일순"),
    STOCK_ASC("재고 적은순");

    private final String description;
}

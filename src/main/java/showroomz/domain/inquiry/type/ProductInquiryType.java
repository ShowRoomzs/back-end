package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품 문의 전용 문의 타입
 */
@Getter
@AllArgsConstructor
public enum ProductInquiryType {

    PRODUCT_INQUIRY("상품 문의"),
    SIZE_INQUIRY("사이즈 문의"),
    STOCK_INQUIRY("재고/재입고 문의");

    private final String description;
}

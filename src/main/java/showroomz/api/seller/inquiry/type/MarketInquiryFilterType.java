package showroomz.api.seller.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 마켓(브랜드)에 노출되는 문의 유형 — 상품 문의 전용.
 * 1:1 문의는 어드민으로만 접수되므로 마켓 목록에 포함되지 않는다 (§17).
 */
@Getter
@AllArgsConstructor
public enum MarketInquiryFilterType {
    PRODUCT("상품 문의"),
    SIZE("사이즈 문의"),
    STOCK("재고/재입고 문의");

    private final String description;
}

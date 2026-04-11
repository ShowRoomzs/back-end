package showroomz.api.seller.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MarketInquiryFilterType {
    PRODUCT("상품 문의"),
    SIZE("사이즈 문의"),
    STOCK("재고/재입고 문의"),
    DELIVERY("배송"),
    ORDER_PAYMENT("주문/결제"),
    CANCEL_REFUND_EXCHANGE("취소/교환/환불"),
    DEFECT_AS("불량/AS");

    private final String description;
}

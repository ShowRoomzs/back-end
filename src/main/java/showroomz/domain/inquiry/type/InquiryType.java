package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InquiryType {

    DELIVERY("배송"),
    ORDER_PAYMENT("주문/결제"),
    CANCEL_REFUND_EXCHANGE("취소/교환/환불"),
    USER_INFO("회원정보"),
    PRODUCT_CHECK("상품확인"),
    SERVICE("서비스");

    private final String description;
}

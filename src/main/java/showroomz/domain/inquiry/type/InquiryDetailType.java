package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum InquiryDetailType {

    // 1. 배송 (DELIVERY)
    RESERVED_DELIVERY("예약 배송", InquiryType.DELIVERY),
    DELIVERY_ETC("기타", InquiryType.DELIVERY),
    DELIVERY_SCHEDULE("배송 일정", InquiryType.DELIVERY),

    // 2. 주문/결제 (ORDER_PAYMENT)
    PAYMENT_METHOD("결제수단", InquiryType.ORDER_PAYMENT),
    ORDER_ETC("기타", InquiryType.ORDER_PAYMENT),
    ORDER_CHANGE("주문 변경", InquiryType.ORDER_PAYMENT),

    // 3. 취소/교환/환불 (CANCEL_REFUND_EXCHANGE)
    ORDER_CANCEL("주문 취소", InquiryType.CANCEL_REFUND_EXCHANGE),
    EXCHANGE_RETURN("교환/반품", InquiryType.CANCEL_REFUND_EXCHANGE),
    REFUND_RETURN("환불/반품", InquiryType.CANCEL_REFUND_EXCHANGE),

    // 4. 회원정보 (USER_INFO)
    JOIN_AUTH("가입/인증", InquiryType.USER_INFO),
    LOGIN_INFO("로그인/정보", InquiryType.USER_INFO),
    WITHDRAWAL_ETC("탈퇴/기타", InquiryType.USER_INFO),

    // 5. 상품 확인 (PRODUCT_CHECK)
    PRODUCT_INQUIRY("상품 문의", InquiryType.PRODUCT_CHECK),
    DEFECT("불량/하자", InquiryType.PRODUCT_CHECK),
    AS("AS", InquiryType.PRODUCT_CHECK),

    // 6. 서비스 (SERVICE)
    REVIEW("후기", InquiryType.SERVICE),
    WEB_APP_INQUIRY("웹/앱 이용 문의", InquiryType.SERVICE),
    CUSTOMER_CENTER("고객센터", InquiryType.SERVICE),
    SERVICE_ETC("기타", InquiryType.SERVICE),
    EVENT("이벤트", InquiryType.SERVICE),
    PROMOTION_BENEFIT("프로모션/혜택", InquiryType.SERVICE),
    REPORT("신고", InquiryType.SERVICE);

    private final String description;
    private final InquiryType parentType;

    public static List<InquiryDetailType> findByParentType(InquiryType parentType) {
        return Arrays.stream(values())
                .filter(detail -> detail.getParentType() == parentType)
                .collect(Collectors.toList());
    }
}

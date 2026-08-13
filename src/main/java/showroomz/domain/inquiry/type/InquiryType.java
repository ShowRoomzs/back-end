package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 1:1 문의 유형 (§17-2-1) — FAQ 카테고리와 동일한 5종. 소분류는 두지 않는다.
 * 폐기 값: 환불 · 주문 · 기타 — 재사용하지 않는다.
 */
@Getter
@AllArgsConstructor
public enum InquiryType {

    DELIVERY("배송"),
    CANCEL_EXCHANGE_RETURN("취소/교환/반품"),
    ORDER_PAYMENT("주문·결제"),
    SERVICE("서비스"),
    ACCOUNT("계정");

    private final String description;
}

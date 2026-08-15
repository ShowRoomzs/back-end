package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품 문의 유형 (§23) — 소비자가 작성 시 필수로 고르는 5종.
 * 배송은 상품 자체의 발송 조건(합포장·분리 발송·발송 시점)을 묻는 문의다.
 * 개별 주문의 배송 추적·환불은 1:1 문의(운영자 단일 창구)가 처리한다.
 */
@Getter
@AllArgsConstructor
public enum ProductInquiryType {

    OPTION("옵션"),
    INGREDIENT_USAGE("성분·사용법"),
    RESTOCK("재입고"),
    DELIVERY("배송"),
    ETC("기타");

    private final String description;
}

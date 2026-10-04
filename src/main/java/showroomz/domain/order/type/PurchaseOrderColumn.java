package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 발주서 컬럼 — 고정 양식 없음(§34-4 rev.6). 브랜드가 컬럼을 골라 순서까지 지정하고,
 * 선택 순서 = 엑셀 좌→우 열 순서다.
 *
 * <p>「소비자 요청 배송일」은 만들지 않는다 — 소비자 앱에 해당 입력이 없다(시안 정정 #21).
 */
@Getter
@RequiredArgsConstructor
public enum PurchaseOrderColumn {

    ORDER_NUMBER("주문번호", true),
    RECIPIENT("수취인", true),
    PHONE("연락처", true),
    ZIP_CODE("우편번호", true),
    ADDRESS("주소", true),
    PRODUCT_NAME("상품명", true),
    OPTION("옵션", true),
    QUANTITY("수량", true),
    DELIVERY_MEMO("배송 요청사항", false),
    GROUP_BUY_NAME("공구명", false),
    ORDERED_AT("주문일시", false),
    PAID_AMOUNT("결제금액", false),
    SUB_ORDER_NUMBER("하위주문번호", false);

    private final String header;
    /** 기본 8종 — 템플릿이 없을 때 미리 선택되는 구성. */
    private final boolean basic;
}

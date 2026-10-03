package showroomz.domain.order.type;

/**
 * 파트너센터 주문 목록 정렬 — 기준 시각은 결제일(주문일시)이다. 발송기한 정렬은 신규·상품준비중 탭의 열 정렬용.
 */
public enum OrderSortType {
    OLDEST_FIRST,
    LATEST_FIRST,
    SHIP_DUE_ASC
}

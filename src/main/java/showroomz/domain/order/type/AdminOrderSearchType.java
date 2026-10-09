package showroomz.domain.order.type;

/** 어드민 주문 조회(06a) 검색 대상 셀렉트(37 설계서 8절 #1) — 전체는 주문번호 · 하위주문번호 · 수취인 · 브랜드명 · 송장(숫자) OR 매치. */
public enum AdminOrderSearchType {
    ALL,
    ORDER_NUMBER,
    SUB_ORDER_NUMBER,
    RECIPIENT,
    BRAND,
    TRACKING_NUMBER,
    /** PG 거래번호 — 포트원 거래 id 또는 paymentId 정확 일치. */
    PG_TX_ID
}

package showroomz.domain.order.type;

/** 어드민 주문 조회(06a) 정렬(37 설계서 8절 #2). 이상 지속 오래된순은 배송 이상 탭의 것 — 마지막 추적 · 반송 감지 시각 기준. */
public enum AdminOrderSort {
    PAID_DESC,
    PAID_ASC,
    AMOUNT_DESC,
    ISSUE_OLDEST
}

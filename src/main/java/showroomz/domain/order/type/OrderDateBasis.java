package showroomz.domain.order.type;

/**
 * 조회 기준 5종(§34-2) — 각각 저장된 시각 컬럼과 1:1 이다.
 * 「준비 시작」은 지금 할 행동이고 「발주확인일」은 이미 기록된 시각의 이름이다 — 정산·CS 업계 명칭과 맞춘다.
 */
public enum OrderDateBasis {
    /** 결제일(기본) — {@code orders.paid_at}. */
    PAID,
    /** 발주확인일 — {@code prepare_started_at}. */
    PREPARE_STARTED,
    /** 발송처리일 — {@code shipped_at}. */
    SHIPPED,
    /** 배송완료일 — {@code delivered_at}. */
    DELIVERED,
    /** 구매확정일 — {@code confirmed_at}. */
    CONFIRMED
}

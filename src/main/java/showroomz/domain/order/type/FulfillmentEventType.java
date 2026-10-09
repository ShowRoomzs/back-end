package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 하위주문 처리 이력 이벤트(34 설계서 1-4) — 상세 모달 「처리 이력」 문구와 1:1. */
@Getter
@RequiredArgsConstructor
public enum FulfillmentEventType {

    PAID("결제완료 · 신규 진입"),
    /** 소비자 취소권 종료를 병기한다(§34-4). */
    PREPARE_STARTED("준비 시작 · 소비자 취소권 종료"),
    INVOICE_REGISTERED("송장 등록"),
    INVOICE_UPDATED("송장 수정"),
    PICKUP_UNCONFIRMED("집화 확인 필요 감지"),
    TRACKING_STALLED("추적 정지 감지"),
    RETURN_DETECTED("반송 감지"),
    RETURN_COMPLETED("반송 완료 입고 · PG 자동 환불"),
    DELIVERED("배송완료"),
    PURCHASE_CONFIRMED("구매확정 · D+7 자동"),
    CANCEL_REQUESTED("취소 요청"),
    CANCEL_REQUEST_APPROVED("취소 요청 승인 · 요청 항목 취소"),
    CANCEL_REQUEST_REJECTED("취소 요청 거부"),
    CANCELLED_BY_CONSUMER("소비자 취소 · 준비 시작 전"),
    CANCELLED_BY_SELLER("브랜드 직권 취소"),
    /** 환불 큐 집행 완료 — PG 자동이면 actor SYSTEM, 운영자 집행이면 ADMIN(1009 기획 수정본 2-4). */
    REFUND_EXECUTED("환불 완료"),
    /** PG 가 환불을 거절했다 — 어드민 환불 관리 「실패」 탭이 재시도한다. */
    REFUND_FAILED("환불 실패 · 운영자 확인"),
    /** 운영자 사유 환불 편입(어드민 06a B5 · 06b B2) — 집행은 환불 관리에서 따로. */
    REFUND_ENQUEUED_BY_OPERATOR("운영자 사유 환불 편입"),
    /** 소비자 수령일 이의 — 운영자가 배송완료일을 정정한다(어드민 06a B3). 구매확정 예정도 다시 센다. */
    DELIVERED_AT_CORRECTED("배송완료일 정정");

    private final String label;
}

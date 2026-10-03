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
    RETURN_COMPLETED("반송 완료 입고 · 운영자 큐 편입"),
    DELIVERED("배송완료"),
    PURCHASE_CONFIRMED("구매확정 · D+7 자동"),
    CANCEL_REQUESTED("취소 요청"),
    CANCEL_REQUEST_APPROVED("취소 요청 승인 · 요청 항목 취소"),
    CANCEL_REQUEST_REJECTED("취소 요청 거부"),
    CANCELLED_BY_CONSUMER("소비자 취소 · 준비 시작 전"),
    CANCELLED_BY_SELLER("브랜드 직권 취소"),
    /** 어드민 거래 관리가 append 한다 — 이 모듈 범위 밖. */
    REFUND_EXECUTED("환불 완료");

    private final String label;
}

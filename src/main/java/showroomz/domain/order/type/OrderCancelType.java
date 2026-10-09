package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취소 3경로(§34-8) — 취소 탭 「취소 사유」 열의 표기이자 취소율 반영 여부의 구분값이다.
 * 취소율 집계는 운영정책 [근거 대기](§34-13 #8)라 집계 테이블은 없다 — 이 값으로 소급 집계한다.
 */
@Getter
@RequiredArgsConstructor
public enum OrderCancelType {

    /** 준비 시작 전 · PG 자동 취소 — 브랜드·운영자 개입 없음 · 취소율 미반영. */
    CONSUMER("소비자 취소 · 준비 시작 전"),
    /** 준비 시작 후 · 브랜드 승인 — 환불은 운영자 · 취소율 미반영. */
    REQUEST_APPROVED("취소 요청 승인 · 브랜드 승인"),
    /** 품절·하자 — 환불은 운영자 · 취소율 반영. */
    SELLER_DIRECT("브랜드 직권 취소"),
    /** 추적 정지 N일 뒤 운영자 분실 판정(41 보고 3번) — 재고는 돌아오지 않는다 · PG 자동 환불 · 취소율 미반영. */
    LOST("배송 분실 · 운영자 처리");

    private final String label;
}

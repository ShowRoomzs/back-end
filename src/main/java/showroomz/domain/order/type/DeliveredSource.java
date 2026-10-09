package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배송완료 출처 — 상세·이력 양쪽에 항상 병기한다(§34-7). 구매확정·정산의 기준값이라 신뢰도가 보여야 한다.
 * 브랜드 경로는 없다 — 버튼·요청 창구를 만들지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum DeliveredSource {

    TRACKER("자동 확인"),
    /** 배송완료일 정정(어드민 06a B3). */
    ADMIN("운영자 정정"),
    /** 추적 정지 N일 뒤 운영자 배송완료 판정(어드민 06a · 41 보고 3번) — 추적이 끊긴 송장에만 열리는 유일한 직권 배송완료. */
    ADMIN_STALLED("운영자 처리 · 추적 정지");

    private final String label;
}

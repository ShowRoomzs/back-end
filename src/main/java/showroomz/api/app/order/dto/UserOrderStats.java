package showroomz.api.app.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소비자 앱 마이 「주문 현황」 4단계(C 마이 시안) — 단계마다 그 상태의 항목을 하나라도 가진 <b>주문</b> 수.
 * 한 주문의 항목이 여러 단계에 걸치면 각 단계에 한 번씩 세인다. 상태는 C10 주문 내역의 항목 표시 상태와 같은 규칙이다 —
 * 취소 요청 중 · 반품/교환 진행 중 항목은 네 단계 어디에도 들지 않는다.
 */
@Schema(description = "주문 현황 4단계 — 단계별 주문 수. 0이면 앱이 회색·탭 불가로 그린다. 탭하면 필터 없이 주문 내역(C10)으로 간다")
public record UserOrderStats(
        @Schema(description = "결제완료 — 브랜드가 아직 준비를 시작하지 않은 항목이 있는 주문 수", example = "2")
        long paid,
        @Schema(description = "상품준비중 — 준비가 시작된 항목이 있는 주문 수", example = "0")
        long preparing,
        @Schema(description = "배송중 — 발송된 항목이 있는 주문 수", example = "1")
        long shipping,
        @Schema(description = "배송완료 — 배송이 끝나고 구매확정 전인 항목이 있는 주문 수", example = "3")
        long delivered) {

    public static final UserOrderStats EMPTY = new UserOrderStats(0, 0, 0, 0);
}

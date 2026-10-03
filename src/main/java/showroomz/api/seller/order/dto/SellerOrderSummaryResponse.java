package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 처리 대기 요약 바 5칸 + 탭 카운트 9종(§34-2) — 한 응답이라 동시 갱신이 저절로 된다.
 * 「배송완료 처리」 칸은 없다 — 자동 전환이라 상시 대기 항목이 아니다.
 */
public record SellerOrderSummaryResponse(ActionBar actionBar, Map<String, Long> tabCounts) {

    public record ActionBar(
            @Schema(description = "준비 시작 — 신규(취소 요청 걸린 건 제외)") long prepareStart,
            @Schema(description = "송장 등록 — 상품준비중(〃)") long invoiceRegister,
            @Schema(description = "배송 이상 — 집화 확인 필요 + 추적 정지 + 반송중 합산") long deliveryIssue,
            @Schema(description = "입고 확인 — 반품·교환 관리 모듈 전이라 null. 0이 아니다(설계서 0-6)") Long incomingCheck,
            @Schema(description = "재발송·교환 — 〃") Long reshipExchange
    ) {
    }
}

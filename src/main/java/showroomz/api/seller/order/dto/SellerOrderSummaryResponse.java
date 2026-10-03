package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 처리 대기 요약 바 5칸 + 탭 카운트 9종(§34-2) — 한 응답이라 동시 갱신이 저절로 된다.
 * 「배송완료 처리」 칸은 없다 — 자동 전환이라 상시 대기 항목이 아니다.
 */
public record SellerOrderSummaryResponse(
        ActionBar actionBar,
        @Schema(description = "탭 코드 9종 → 건수. 0건도 0으로 항상 모두 들어 있다(키 순서 = OrderTab 선언 순서)",
                example = "{\"ALL\": 128, \"NEW\": 12, \"PREPARING\": 7, \"CANCEL_REQUESTED\": 2, \"SHIPPING\": 21, "
                        + "\"RETURNING\": 1, \"DELIVERED\": 15, \"CONFIRMED\": 64, \"CANCELLED\": 6}")
        Map<String, Long> tabCounts
) {

    @Schema(name = "SellerOrderActionBar")
    public record ActionBar(
            @Schema(description = "준비 시작 — 신규(취소 요청 걸린 건 제외)", example = "12") long prepareStart,
            @Schema(description = "송장 등록 — 상품준비중(〃)", example = "7") long invoiceRegister,
            @Schema(description = "배송 이상 — 집화 확인 필요 + 추적 정지 + 반송중 합산", example = "3") long deliveryIssue,
            @Schema(description = "입고 확인 — 반품·교환 관리 모듈 전이라 null. 0이 아니다(설계서 0-6)", nullable = true)
            Long incomingCheck,
            @Schema(description = "재발송·교환 — 〃", nullable = true) Long reshipExchange
    ) {
    }
}

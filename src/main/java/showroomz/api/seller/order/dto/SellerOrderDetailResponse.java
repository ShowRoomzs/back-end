package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderBadgeTone;

import java.time.LocalDateTime;
import java.util.List;

/** 주문 상세 모달(§34-9) — 좌 본문(주문 정보 · 배송지 · 항목 표) + 우 레일(상태 · 핵심 시각 · 액션 · 이력). */
public record SellerOrderDetailResponse(
        Long deliveryGroupId,
        String orderNumber,
        @Schema(description = "「이 공구 몫만 표시됩니다」") String subOrderNumber,
        LocalDateTime orderedAt,
        Long groupBuyId,
        String groupBuyName,
        String paymentMethod,
        FulfillmentStatus status,
        String statusLabel,
        OrderBadgeTone statusTone,
        SellerOrderListItem.Overlays overlays,
        @Schema(description = "배송지 — 마스킹 해제. 소비자 입력 · 브랜드는 수정 불가") Recipient recipient,
        List<SellerOrderListItem.Item> items,
        Amounts amounts,
        Timeline timeline,
        @Schema(description = "검토 중 취소 요청 블록(C11) — 없으면 null") CancelRequestBlock cancelRequest,
        Actions actions,
        List<HistoryItem> history
) {

    public record Recipient(String name, String phone, String zipCode, String address, String detailAddress,
                            String deliveryMemo) {
    }

    public record Amounts(
            int productTotal,
            int deliveryFee,
            int totalAmount,
            @Schema(description = "검토 중 취소 요청분 — 금액 요약 별 행") Integer cancelRequestedAmount,
            @Schema(description = "확정된 취소분") int cancelledAmount
    ) {
    }

    public record Timeline(
            LocalDateTime shipDueAt,
            LocalDateTime prepareStartedAt,
            LocalDateTime shippedAt,
            DeliveryCarrier carrier,
            String carrierLabel,
            String trackingNumber,
            LocalDateTime lastTrackingAt,
            LocalDateTime returnDetectedAt,
            LocalDateTime deliveredAt,
            @Schema(description = "출처 병기 — 항상(§34-7)") String deliveredSourceLabel,
            @Schema(description = "구매확정 예정 — 배송완료 + 7일") LocalDateTime confirmDueAt,
            LocalDateTime confirmedAt,
            LocalDateTime cancelledAt,
            String cancelTypeLabel,
            String cancelReasonLabel,
            String cancelReasonDetail
    ) {
    }

    public record CancelRequestBlock(
            Long cancelRequestId,
            String reasonLabel,
            String reasonDetail,
            LocalDateTime requestedAt,
            @Schema(description = "요청 당시 이행 상태 라벨") String statusAtRequestLabel,
            @Schema(description = "준비 시작 후 경과(시간) — 준비 시작 전 요청이면 null") Long hoursSincePrepareStart,
            List<RequestItem> items,
            int totalRefundAmount,
            @Schema(description = "남은(미요청·미취소) 항목 수 — 「남은 N건 발송 대기」") long remainingItemCount
    ) {
        public record RequestItem(Long orderProductId, String productName, String optionName, int quantity,
                                  int refundAmount) {
        }
    }

    /** 버튼 노출 규칙의 정본 — 「그 탭에서 쓸 수 없는 버튼은 노출하지 않는다」(§34-3)를 서버가 소유한다. */
    public record Actions(
            boolean canPrepareStart,
            boolean canRegisterInvoice,
            @Schema(description = "송장 수정 — 배송완료 전까지 · 반송중 불가") boolean canUpdateInvoice,
            boolean canCancelDirectly,
            boolean canDecideCancelRequest
    ) {
    }

    public record HistoryItem(String eventType, String label, String actorType, String actorLabel, String detail,
                              LocalDateTime occurredAt) {
    }
}

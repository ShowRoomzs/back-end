package showroomz.api.admin.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.order.type.AdminOrderSearchType;
import showroomz.domain.order.type.AdminOrderSort;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OperatorRefundReason;
import showroomz.domain.order.type.OrderBadgeTone;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.order.type.SellerCancelReason;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.domain.payment.type.PaymentMethod;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 어드민 거래 관리 · 주문 조회(06a · 1009 기획 수정본 8-1). 단위는 주문(결제 1건)이다. */
public final class AdminOrderDto {

    private AdminOrderDto() {
    }

    // ------------------------------------------------------------------ 목록

    @Schema(name = "AdminOrderListItem")
    public record ListItem(
            Long orderId,
            @Schema(example = "20261003-000123") String orderNumber,
            @Schema(description = "결제완료 시각") LocalDateTime paidAt,
            @Schema(example = "김민지") String recipientName,
            @Schema(description = "결제 금액", example = "54800") Integer totalAmount,
            @Schema(description = "하위주문(브랜드) — 행 확장(A2)") List<GroupSummary> groups,
            @Schema(description = "운영자가 볼 것이 있는 하위주문 수 — 배송 이상 · 발송 기한 경과 · 검토 중 취소 요청", example = "1")
            int attentionCount
    ) {
    }

    @Schema(name = "AdminOrderGroupSummary")
    public record GroupSummary(
            Long deliveryGroupId,
            @Schema(example = "20261003-000123-01") String subOrderNumber,
            @Schema(example = "○○ 브랜드") String brandName,
            FulfillmentStatus status,
            String statusLabel,
            OrderBadgeTone statusTone,
            @Schema(nullable = true) TrackingAlert trackingAlert,
            @Schema(nullable = true) String trackingAlertLabel,
            @Schema(description = "발송 기한 — 공구 진행 중이면 null", nullable = true) LocalDateTime shipDueAt,
            @Schema(description = "발송 기한 경과(발송 전 · 브랜드 귀책)") boolean shipOverdue,
            @Schema(description = "검토 중 취소 요청") boolean cancelRequested
    ) {
    }

    @Schema(name = "AdminOrderSummaryResponse")
    public record SummaryResponse(
            @Schema(description = "탭 건수 — ALL · DELIVERY_ISSUE · CANCEL", example = "{\"ALL\": 120, \"DELIVERY_ISSUE\": 3, \"CANCEL\": 7}")
            Map<String, Long> tabCounts
    ) {
    }

    /** 목록 · 요약 조건(37 설계서 2-1 · 8절 #1 · #2). 요약은 탭 · 상태 · 정렬을 무시한다. */
    public record SearchParams(AdminOrderTab tab, FulfillmentStatus status, Long marketId, Long groupBuyId,
                               Long creatorId, PaymentMethod paymentMethod, TrackingAlert trackingAlert,
                               AdminOrderSearchType searchType, String keyword, AdminOrderSort sort) {

        public SearchParams(AdminOrderTab tab, FulfillmentStatus status, Long marketId, String keyword) {
            this(tab, status, marketId, null, null, null, null, null, keyword, null);
        }

        /** 요약용 — 탭만 바꾼 같은 조건(상태 · 정렬은 뺀다). */
        public SearchParams forTab(AdminOrderTab tab) {
            return new SearchParams(tab, null, marketId, groupBuyId, creatorId, paymentMethod, trackingAlert,
                    searchType, keyword, null);
        }
    }

    // ------------------------------------------------------------------ 상세

    @Schema(name = "AdminOrderDetailResponse")
    public record DetailResponse(
            Long orderId,
            String orderNumber,
            String orderStatus,
            LocalDateTime paidAt,
            Consumer consumer,
            Recipient recipient,
            @Schema(nullable = true) Payment payment,
            @Schema(description = "모달 헤더 「문의 N건」 — 이 주문을 가리키는 1:1 문의 수(37 설계서 8절 #11)", example = "1") long inquiryCount,
            List<GroupDetail> groups
    ) {
    }

    @Schema(name = "AdminOrderConsumer")
    public record Consumer(Long userId, @Schema(nullable = true) String name, @Schema(nullable = true) String email) {
    }

    @Schema(name = "AdminOrderRecipient")
    public record Recipient(String name, String phone, String zipCode, String address, String detailAddress,
                            @Schema(nullable = true) String memo) {
    }

    @Schema(name = "AdminOrderActiveClaim", description = "진행 중 반품·교환 — 06b 상세(`GET /v1/admin/claims/{claimId}`)로 간다")
    public record ActiveClaim(Long claimId, @Schema(example = "CLM-3015") String claimNumber, ClaimType type,
                              ClaimStatus status, @Schema(example = "재발송 대기") String statusLabel) {
    }

    @Schema(name = "AdminOrderPayment")
    public record Payment(
            String paymentId,
            String status,
            @Schema(example = "신한카드") String methodLabel,
            Integer amount,
            @Schema(description = "부분 취소 누적액 — 환불 큐 집행이 올린다", example = "24900") Integer cancelledAmount,
            LocalDateTime paidAt
    ) {
    }

    @Schema(name = "AdminOrderGroupDetail")
    public record GroupDetail(
            Long deliveryGroupId,
            String subOrderNumber,
            Long marketId,
            String brandName,
            @Schema(nullable = true) String groupBuyNumber,
            FulfillmentStatus status,
            String statusLabel,
            OrderBadgeTone statusTone,
            Shipping shipping,
            @Schema(description = "구매확정 — 배송완료 뒤에만", nullable = true) PurchaseConfirm purchaseConfirm,
            @Schema(nullable = true) Cancel cancel,
            @Schema(description = "검토 중 취소 요청(B7) — 응답 마감 · 자동 승인 예정", nullable = true) CancelRequest cancelRequest,
            List<Item> items,
            @Schema(description = "환불 — 이 하위주문의 환불 큐 전부(출처 · 상태)") List<Refund> refunds,
            @Schema(description = "진행 중 클레임(B8 교환 진행 중 등) — 06b 상세 링크용. 종결 · 결제 대기는 없다") List<ActiveClaim> activeClaims,
            @Schema(description = "처리 이력 — 오래된순. 준비 시작 사유(발주서 다운로드 / 개별 / 일괄) · 자동 알림 · 운영자 조치가 남는다")
            List<History> history,
            Actions actions
    ) {
    }

    @Schema(name = "AdminOrderShipping")
    public record Shipping(
            @Schema(description = "발송 기한 = 공구 마감 + N영업일 — 공구 진행 중이면 null", nullable = true) LocalDateTime shipDueAt,
            @Schema(description = "이 주문에 적용된 발송 기한 N(영업일) — 주문 시점 값", example = "3") int shipDueBusinessDays,
            boolean shipOverdue,
            @Schema(description = "발송 기한 경과 자동 알림 횟수 — 독촉 버튼은 없다", example = "2") int overdueNoticeCount,
            @Schema(nullable = true) LocalDateTime prepareStartedAt,
            @Schema(nullable = true) LocalDateTime shippedAt,
            @Schema(nullable = true) DeliveryCarrier carrier,
            @Schema(nullable = true) String carrierLabel,
            @Schema(nullable = true) String trackingNumber,
            @Schema(nullable = true) TrackingAlert trackingAlert,
            @Schema(nullable = true) String trackingAlertLabel,
            @Schema(nullable = true) LocalDateTime lastTrackingAt,
            @Schema(nullable = true) LocalDateTime returnDetectedAt,
            @Schema(nullable = true) LocalDateTime returnCompletedAt,
            @Schema(nullable = true) LocalDateTime deliveredAt,
            @Schema(description = "배송완료 출처 — 자동 확인 · 운영자 정정", nullable = true) String deliveredSourceLabel
    ) {
    }

    @Schema(name = "AdminOrderPurchaseConfirm")
    public record PurchaseConfirm(
            boolean paused,
            @Schema(nullable = true) Long remainingDays,
            @Schema(description = "확정 예정 — 정지 중이면 null", nullable = true) LocalDateTime dueAt,
            @Schema(nullable = true) LocalDateTime confirmedAt
    ) {
    }

    @Schema(name = "AdminOrderCancel")
    public record Cancel(LocalDateTime cancelledAt, String cancelTypeLabel, @Schema(nullable = true) String reasonLabel,
                         @Schema(nullable = true) String reasonDetail) {
    }

    @Schema(name = "AdminOrderCancelRequest")
    public record CancelRequest(
            Long cancelRequestId,
            String reasonLabel,
            @Schema(nullable = true) String reasonDetail,
            LocalDateTime requestedAt,
            @Schema(description = "응답 마감 — 지나면 자동 승인 · PG 즉시 환불. 운영자 조치는 없다") LocalDateTime respondDueAt,
            List<Long> orderProductIds
    ) {
    }

    @Schema(name = "AdminOrderItem")
    public record Item(Long orderProductId, String productName, @Schema(nullable = true) String optionName,
                       Integer quantity, Integer returnedQuantity, Integer price, String status,
                       @Schema(nullable = true) String cancelTypeLabel) {
    }

    @Schema(name = "AdminOrderRefund")
    public record Refund(Long refundTaskId, @Schema(description = "환불번호 — 환불 관리 검색어", example = "RFD-918") String refundNo,
                         RefundTaskSource source, RefundTaskOrigin origin, String originLabel,
                         Integer amount, RefundTaskStatus status, @Schema(nullable = true) String lastError,
                         @Schema(nullable = true) LocalDateTime executedAt, LocalDateTime createdAt) {
    }

    @Schema(name = "AdminOrderHistory")
    public record History(String eventType, String label, String actorType, @Schema(nullable = true) String detail,
                          LocalDateTime occurredAt) {
    }

    @Schema(name = "AdminOrderActions")
    public record Actions(
            @Schema(description = "B3 배송완료일 정정 — 배송완료") boolean canCorrectDeliveredAt,
            @Schema(description = "B4 대행 송장 등록 — 상품준비중 ∧ 발송 기한 경과 ∧ 자동 알림 N회 무응답(근거 대기 · 시안 3회)")
            boolean canRegisterShipment,
            @Schema(description = "B4 · B5 대행 직권 취소 — 신규 · 상품준비중 ∧ (대행 조건 충족 ∨ 위해성 리콜)") boolean canCancel,
            @Schema(description = "B5 운영자 사유 환불 편입 — 발송 이후") boolean canEnqueueRefund,
            @Schema(description = "B6 구매확정 후 하자 반품 대신 열기 — 구매확정(또는 배송완료) ∧ 배송완료 3개월 안") boolean canOpenDefectClaim,
            @Schema(description = "분실 처리 — 배송중 ∧ 추적 정지 ∧ 마지막 추적 + N일(기본 28) 경과(41 보고 3번)") boolean canMarkLost,
            @Schema(description = "배송완료 처리 — 분실 처리와 같은 조건 · 운영자가 둘 중 하나를 고른다") boolean canMarkDelivered
    ) {
    }

    @Schema(name = "AdminMarkLostRequest", description = "추적 정지 종결 — 분실 판정(하위주문 취소 · 재고 원복 없음 · PG 자동 환불)")
    public record MarkLostRequest(
            @NotBlank @Size(max = 300) @Schema(description = "판정 근거 — 택배사 조회 결과 등 · 이력에 남는다") String reason
    ) {
    }

    @Schema(name = "AdminMarkDeliveredRequest", description = "추적 정지 종결 — 배송완료 판정(구매확정 타이머 시작)")
    public record MarkDeliveredRequest(
            @NotNull @Schema(description = "수령 시각 — 발송 이후 · 지금 이전", example = "2026-09-18T15:00:00") LocalDateTime deliveredAt,
            @NotBlank @Size(max = 300) @Schema(description = "판정 근거 — 소비자 확인 등 · 이력에 남는다") String reason
    ) {
    }

    // ------------------------------------------------------------------ 조치

    @Schema(name = "AdminCorrectDeliveredAtRequest", description = "B3 — 소비자 수령일 이의 · 배송완료일 정정")
    public record CorrectDeliveredAtRequest(
            @NotNull @Schema(description = "실제 수령일시", example = "2026-09-18T15:00:00") LocalDateTime deliveredAt,
            @NotBlank @Size(max = 300) @Schema(description = "정정 사유 — 이력에 남는다", example = "소비자 수령일 이의 · 택배사 확인") String reason
    ) {
    }

    @Schema(name = "AdminShipmentRequest", description = "B4 — 운영자 대행 송장 등록")
    public record ShipmentRequest(
            @NotNull DeliveryCarrier carrier,
            @NotBlank @Schema(example = "640012345678") String trackingNumber,
            @NotBlank @Size(max = 300) @Schema(description = "대행 사유(필수) — 이력에 남는다", example = "자동 알림 3회 무응답 · 소비자 문의") String note
    ) {
    }

    @Schema(name = "AdminCancelRequest", description = "B4 · B5 — 운영자 대행 직권 취소(미발송분)")
    public record CancelCommand(
            @NotNull @Schema(example = "DEFECT") SellerCancelReason reasonCode,
            @NotBlank @Size(max = 300) @Schema(description = "소비자에게 그대로 전달되는 설명") String consumerMessage
    ) {
    }

    @Schema(name = "AdminOperatorRefundRequest", description = "B5 — 운영자 사유 환불 편입(집행은 환불 관리에서)")
    public record OperatorRefundRequest(
            @NotNull OperatorRefundReason reason,
            @NotNull @Min(1) @Schema(example = "24900") Integer amount,
            @NotBlank @Size(max = 500) @Schema(description = "근거 — 이력 · 환불 관리에 남는다") String detail
    ) {
    }

    @Schema(name = "AdminDefectClaimRequest", description = "B6 — 구매확정 후 하자 · 반품 대신 열기")
    public record DefectClaimRequest(
            @NotEmpty List<DefectItem> items,
            @NotNull @Schema(description = "DAMAGED_OR_DEFECTIVE · WRONG_OR_LATE_DELIVERY 만", example = "DAMAGED_OR_DEFECTIVE")
            ClaimReason reasonCode,
            @NotBlank @Size(max = 1000) @Schema(description = "하자 내용 — 1:1 문의 요약") String detail,
            @NotEmpty @Schema(description = "증빙 사진 URL — 1장 이상(문의에 첨부된 사진)") List<String> evidenceImageUrls
    ) {
    }

    @Schema(name = "AdminDefectClaimItem")
    public record DefectItem(@NotNull Long orderProductId, @NotNull @Min(1) Integer quantity) {
    }

    @Schema(name = "AdminDefectClaimResponse")
    public record DefectClaimResponse(Long requestId, List<Long> claimIds) {
    }
}

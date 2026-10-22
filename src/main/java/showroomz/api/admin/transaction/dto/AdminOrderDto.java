package showroomz.api.admin.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.order.type.AdminOrderSearchType;
import showroomz.domain.order.type.AdminOrderSort;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
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

import java.time.LocalDate;
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
            @Schema(description = "검토 중 취소 요청") boolean cancelRequested,
            @Schema(description = "행 확장 항목 요약 — 첫 항목 상품명(1종이면 옵션까지) · 나머지는 「외 N종」", example = "데일리 선크림 외 1종")
            String itemSummary,
            @Schema(description = "항목 수량 합", example = "2") int itemQuantity,
            @Schema(description = "하위주문 금액 = 상품 합계 + 배송비 — 한 주문의 합이 결제 금액이다", example = "24400") int amount,
            @Schema(nullable = true) DeliveryCarrier carrier,
            @Schema(example = "CJ대한통운", nullable = true) String carrierLabel,
            @Schema(nullable = true) String trackingNumber,
            @Schema(description = "공구명(계약 제목) — 목록 「공구명」 열. 공구 밖 하위주문이면 null", example = "데일리 선크림 공구", nullable = true)
            String groupBuyTitle,
            @Schema(description = "배송 이상 기준 시각 — 집화 확인 필요 = 송장 등록 · 추적 정지 = 마지막 추적 · 반송 중 = 반송 감지. 이상이 없으면 null",
                    nullable = true) LocalDateTime issueSince,
            @Schema(description = "배송 이상 경과 문구 — 「등록 후 26시간」 · 「8일 무갱신」 · 「감지 09.12」. 이상이 없으면 null",
                    example = "8일 무갱신", nullable = true) String issueElapsedLabel
    ) {
    }

    @Schema(name = "AdminOrderSummaryResponse")
    public record SummaryResponse(
            @Schema(description = "탭 건수 — ALL · DELIVERY_ISSUE · CANCEL", example = "{\"ALL\": 120, \"DELIVERY_ISSUE\": 3, \"CANCEL\": 7}")
            Map<String, Long> tabCounts,
            @Schema(description = "상태 셀렉트 건수(전체 탭 기준) — 그 상태를 고르면 나오는 주문 수. 상태가 갈린 주문은 상태마다 세므로 합이 전체보다 클 수 있다",
                    example = "{\"NEW\": 18, \"PREPARING\": 31, \"SHIPPING\": 44, \"RETURNING\": 1, \"DELIVERED\": 27, \"CONFIRMED\": 15, \"CANCELLED\": 7}")
            Map<String, Long> statusCounts,
            @Schema(description = "이상 유형 셀렉트 건수(배송 이상 탭 기준) — PICKUP_UNCONFIRMED · STALLED 는 `trackingAlert`, "
                    + "RETURNING 은 `status=RETURNING` 으로 고른 건수와 같다",
                    example = "{\"PICKUP_UNCONFIRMED\": 1, \"STALLED\": 2, \"RETURNING\": 1}")
            Map<String, Long> deliveryIssueCounts
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

        /** 요약용 — 상태 셀렉트 하나를 고른 같은 조건. */
        public SearchParams withStatus(FulfillmentStatus status) {
            return new SearchParams(tab, status, marketId, groupBuyId, creatorId, paymentMethod, trackingAlert,
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
            @Schema(description = "이 주문을 가리키는 1:1 문의 — 최신순. B3 수령일 이의의 「1:1 문의 #N」 링크용. `inquiryCount` 와 같은 집합")
            List<Inquiry> inquiries,
            @Schema(description = "① 추가 결제 — 클레임 재발송비(교환 선결제 · 반려 재발송비) 중 PG 결제가 붙은 것. 원래 결제와 PG 거래가 따로다")
            List<AdditionalPayment> additionalPayments,
            List<GroupDetail> groups
    ) {
    }

    @Schema(name = "AdminOrderInquiry")
    public record Inquiry(
            @Schema(example = "4518") Long inquiryId,
            @Schema(example = "배송") String typeLabel,
            LocalDateTime createdAt,
            @Schema(description = "답변 등록 여부") boolean answered
    ) {
    }

    @Schema(name = "AdminOrderAdditionalPayment", description = "클레임 결제 한 건 — 결제가 성립한 것만(결제 대기 · 실패는 없다)")
    public record AdditionalPayment(
            Long deliveryGroupId,
            @Schema(description = "클레임 요청(박스)") Long collectionId,
            @Schema(description = "그 요청의 첫 클레임 — 06b 상세 링크", example = "3015") Long claimId,
            @Schema(example = "CLM-3015") String claimNumber,
            Long chargeId,
            ClaimChargeType chargeType,
            @Schema(example = "교환 재발송비") String typeLabel,
            @Schema(example = "3000") int amount,
            @Schema(description = "청구 상태 — PAID 결제됨 · REFUNDED 결제 취소 등") ClaimChargeStatus status,
            @Schema(example = "결제됨") String statusLabel,
            String paymentId,
            @Schema(description = "PG 거래번호", example = "kakaopay_b204f9c1", nullable = true) String pgTxId,
            @Schema(example = "카카오페이") String methodLabel,
            @Schema(nullable = true) LocalDateTime paidAt,
            @Schema(description = "취소 가능 잔액 — 결제됐고 취소 전이면 결제액, 아니면 0(클레임 결제는 전액 취소만 한다)", example = "3000")
            int cancellableAmount
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
            LocalDateTime paidAt,
            @Schema(description = "운영자 사유 환불 상한 = 취소 가능 잔액 − 아직 나가지 않은 환불(대기 · 집행 중 · 실패). "
                    + "B5 다이얼로그의 「최대 N원」 — 넘기면 409 REFUND_AMOUNT_EXCEEDED", example = "0")
            int operatorRefundableAmount
    ) {
    }

    @Schema(name = "AdminOrderGroupDetail")
    public record GroupDetail(
            Long deliveryGroupId,
            String subOrderNumber,
            Long marketId,
            String brandName,
            @Schema(nullable = true) String groupBuyNumber,
            @Schema(description = "공구 — 공구 밖 하위주문이면 null", nullable = true) Long groupBuyId,
            @Schema(description = "공구명(계약 제목)", example = "데일리 선크림 공구", nullable = true) String groupBuyTitle,
            @Schema(description = "공구를 연 인플루언서", nullable = true) Long creatorId,
            @Schema(description = "인플루언서 쇼룸명", example = "소연_쇼룸", nullable = true) String creatorName,
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
            @Schema(description = "④ 정산 반영(37 설계서 8절 #4) — 이 하위주문이 어느 정산에 얼마로 들어갔나. 정산 생성 전이면 null",
                    nullable = true)
            Settlement settlement,
            Actions actions,
            @Schema(description = "추적 정지 · 소비자 선택(06a · 06d · 1021 기-15) — 「선택 개방까지 N일」 · 소비자 선택. 없으면 null", nullable = true)
            StallChoice stallChoice
    ) {
    }

    @Schema(name = "AdminOrderStallChoice")
    public record StallChoice(
            @Schema(description = "WAITING · OPEN · CHOSEN", example = "WAITING") String state,
            @Schema(nullable = true) LocalDateTime opensAt,
            @Schema(description = "선택 개방까지 N일", nullable = true, example = "3") Integer daysUntilOpen,
            @Schema(nullable = true, example = "9") Integer daysStalled,
            @Schema(nullable = true) showroomz.domain.order.type.StallChoice choice,
            @Schema(example = "재발송", nullable = true) String choiceLabel,
            @Schema(nullable = true) LocalDateTime chosenAt
    ) {
        public static StallChoice of(showroomz.domain.order.service.OrderStallChoiceService.View view) {
            return view == null ? null : new StallChoice(view.state().name(), view.opensAt(), view.daysUntilOpen(),
                    view.daysStalled(), view.choice(), view.choiceLabel(), view.chosenAt());
        }
    }

    @Schema(name = "AdminOrderSettlement", description = "하위주문의 정산 반영(44 어드민 정산관리 설계서 8-3)")
    public record Settlement(
            Long settlementId,
            @Schema(example = "STL-2610-004") String settlementNumber,
            @Schema(description = "정산 상태 — REVIEWING · ADJUSTING · PAYOUT_SCHEDULED · PAID · PAYOUT_FAILED", example = "PAYOUT_SCHEDULED")
            String status,
            @Schema(example = "지급 예정") String statusLabel,
            @Schema(description = "정산 확정 시각 — 확정 전 null", nullable = true) LocalDateTime confirmedAt,
            @Schema(description = "이 하위주문 항목들의 정산 반영액 합(원)", example = "51200") long settledAmount,
            @Schema(description = "이 하위주문 항목들의 리워드 합(원) — 항목 기준(합의 금액이 아니다)", example = "5664") long rewardAmount,
            @Schema(description = "정산 후 환불로 생긴 차감 — 없으면 빈 목록") List<SettlementClawback> clawbacks
    ) {
    }

    @Schema(name = "AdminOrderSettlementClawback")
    public record SettlementClawback(
            @Schema(example = "CLW-0003") String clawbackNumber,
            @Schema(description = "BRAND · CREATOR — 한 환불이 측별 2행을 만든다", example = "BRAND") String side,
            @Schema(description = "PENDING · APPLIED · UNRECOVERABLE", example = "PENDING") String status,
            @Schema(example = "차감 예정") String statusLabel,
            @Schema(example = "24336") long amount
    ) {
    }

    @Schema(name = "AdminOrderShipping")
    public record Shipping(
            @Schema(description = "발송 기한 = 공구 마감 + N영업일 — 공구 진행 중이면 null", nullable = true) LocalDateTime shipDueAt,
            @Schema(description = "이 주문에 적용된 발송 기한 N(영업일) — 주문 시점 값", example = "3") int shipDueBusinessDays,
            boolean shipOverdue,
            @Schema(description = "발송 기한 경과 자동 알림 횟수 — 독촉 버튼은 없다", example = "2") int overdueNoticeCount,
            @Schema(description = "B4 「마지막 자동 알림」 시각 — 알림 전이면 null", nullable = true) LocalDateTime lastOverdueNoticeAt,
            @Schema(nullable = true) LocalDateTime prepareStartedAt,
            @Schema(nullable = true) LocalDateTime shippedAt,
            @Schema(nullable = true) DeliveryCarrier carrier,
            @Schema(nullable = true) String carrierLabel,
            @Schema(nullable = true) String trackingNumber,
            @Schema(description = "송장 출처 — SELLER(브랜드 등록) · ADMIN(운영자 대행). 송장이 없으면 null", example = "SELLER", nullable = true)
            String invoiceSource,
            @Schema(example = "브랜드 등록", nullable = true) String invoiceSourceLabel,
            @Schema(nullable = true) TrackingAlert trackingAlert,
            @Schema(nullable = true) String trackingAlertLabel,
            @Schema(nullable = true) LocalDateTime lastTrackingAt,
            @Schema(description = "마지막 스캔 문구 — 연동 업체 원문(파트너 주문 상세 우 레일과 같다). 스캔 전이면 null", example = "간선 상차",
                    nullable = true) String lastTrackingLabel,
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
            @Schema(description = "B5 운영자 사유 환불 편입 — 배송중 · 배송완료 · 구매확정(반송 중은 자동 환불이라 제외)") boolean canEnqueueRefund,
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
            @NotNull @Schema(description = "RECALL · POST_CONFIRM_DEFECT 만 — 반려 이의 인용 · 검수 무응답은 06b", example = "RECALL")
            OperatorRefundReason reason,
            @NotNull @Min(1) @Schema(example = "24900") Integer amount,
            @NotBlank @Size(max = 500) @Schema(description = "근거 — 이력 · 환불 관리에 남는다") String detail
    ) {
    }

    @Schema(name = "AdminDefectClaimRequest", description = "B6 — 구매확정 후 하자 · 반품 대신 열기")
    public record DefectClaimRequest(
            @NotEmpty List<@Valid DefectItem> items,
            @NotNull @Schema(description = "DAMAGED_OR_DEFECTIVE · WRONG_OR_LATE_DELIVERY 만", example = "DAMAGED_OR_DEFECTIVE")
            ClaimReason reasonCode,
            @NotBlank @Size(max = 1000) @Schema(description = "하자 내용 — 1:1 문의 요약") String detail,
            @NotEmpty @Schema(description = "증빙 사진 URL — 1장 이상(문의에 첨부된 사진)") List<String> evidenceImageUrls,
            @Schema(description = "하자 인지일(선택) — 법정 청약철회 기한 「인지 + 30일」의 기산점. 배송완료일 이후 · 오늘 이전. "
                    + "06b 상세 `legalDeadline` 에 나온다", example = "2026-09-17", nullable = true)
            LocalDate defectNoticedOn
    ) {
    }

    @Schema(name = "AdminDefectClaimItem")
    public record DefectItem(@NotNull Long orderProductId, @NotNull @Min(1) Integer quantity) {
    }

    @Schema(name = "AdminDefectClaimResponse")
    public record DefectClaimResponse(Long requestId, List<Long> claimIds) {
    }
}

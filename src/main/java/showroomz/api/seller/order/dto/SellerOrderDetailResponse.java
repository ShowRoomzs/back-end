package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderBadgeTone;

import java.time.LocalDateTime;
import java.util.List;

/** 주문 상세 모달(§34-9) — 좌 본문(주문 정보 · 배송지 · 항목 표) + 우 레일(상태 · 핵심 시각 · 액션 · 이력). */
public record SellerOrderDetailResponse(
        @Schema(description = "하위주문 id", example = "1024") Long deliveryGroupId,
        @Schema(description = "주문번호", example = "20261003-000123") String orderNumber,
        @Schema(description = "「이 공구 몫만 표시됩니다」", example = "20261003-000123-01") String subOrderNumber,
        @Schema(description = "주문일시 = 결제완료 시각", example = "2026-10-01T14:22:05Z") LocalDateTime orderedAt,
        @Schema(description = "공구 id — 백필 주문은 null", example = "18", nullable = true) Long groupBuyId,
        @Schema(description = "공구명(계약명) — 백필 주문은 null", example = "글로우 크림 앵콜 공구", nullable = true) String groupBuyName,
        @Schema(description = "결제수단 표시명 — 카드 · 간편결제", example = "카드", nullable = true) String paymentMethod,
        @Schema(description = "이행 상태", example = "PREPARING") FulfillmentStatus status,
        @Schema(description = "상태 배지 문구", example = "상품준비중") String statusLabel,
        @Schema(description = "상태 배지 색", example = "INFO") OrderBadgeTone statusTone,
        SellerOrderListItem.Overlays overlays,
        @Schema(description = "배송지 — 마스킹 해제. 소비자 입력 · 브랜드는 수정 불가") Recipient recipient,
        @Schema(description = "항목 표 — 취소 항목도 포함(`cancelled = true`)") List<SellerOrderListItem.Item> items,
        Amounts amounts,
        Timeline timeline,
        @Schema(description = "검토 중 취소 요청 블록(C11) — 없으면 null", nullable = true) CancelRequestBlock cancelRequest,
        Actions actions,
        @Schema(description = "처리 이력 — 최신순(발생 시각 내림차순 · 동률은 id 내림차순)") List<HistoryItem> history
) {

    @Schema(name = "SellerOrderRecipient")
    public record Recipient(
            @Schema(description = "수취인", example = "김민지") String name,
            @Schema(description = "연락처", example = "010-1234-5678") String phone,
            @Schema(description = "우편번호", example = "06236") String zipCode,
            @Schema(description = "주소", example = "서울특별시 강남구 테헤란로 123") String address,
            @Schema(description = "상세 주소", example = "4층 401호", nullable = true) String detailAddress,
            @Schema(description = "배송 요청사항", example = "부재 시 문 앞에 놓아주세요", nullable = true) String deliveryMemo
    ) {
    }

    @Schema(name = "SellerOrderAmounts")
    public record Amounts(
            @Schema(description = "상품 합계(공구가 × 수량 합 · 취소 항목 포함)", example = "81400") int productTotal,
            @Schema(description = "배송비", example = "3000") int deliveryFee,
            @Schema(description = "결제 합계 = 상품 합계 + 배송비", example = "84400") int totalAmount,
            @Schema(description = "검토 중 취소 요청분 — 금액 요약 별 행. 요청이 없으면 null", example = "27000", nullable = true)
            Integer cancelRequestedAmount,
            @Schema(description = "확정된 취소분", example = "0") int cancelledAmount
    ) {
    }

    /** 우 레일 핵심 시각 — 아직 일어나지 않은 시각은 null. */
    @Schema(name = "SellerOrderTimeline")
    public record Timeline(
            @Schema(description = "발송기한 = 공구 마감 + N영업일 — 공구 진행 중이면 null", example = "2026-10-04T23:59:59Z",
                    nullable = true) LocalDateTime shipDueAt,
            @Schema(description = "이 주문에 적용된 발송 기한 N(영업일) — 주문 시점 값", example = "3") int shipDueBusinessDays,
            @Schema(description = "준비 시작(발주확인) 시각 — 소비자 단순 취소권 종료 시점", example = "2026-10-02T09:00:00Z", nullable = true)
            LocalDateTime prepareStartedAt,
            @Schema(description = "발송 처리(송장 등록 확정) 시각 — 발송기한 판정값 · 송장 수정으로 바뀌지 않는다",
                    example = "2026-10-03T11:20:00Z", nullable = true) LocalDateTime shippedAt,
            @Schema(description = "택배사", example = "CJ", nullable = true) DeliveryCarrier carrier,
            @Schema(description = "택배사 표시명", example = "CJ대한통운", nullable = true) String carrierLabel,
            @Schema(description = "송장번호", example = "640012345678", nullable = true) String trackingNumber,
            @Schema(description = "배송 추적 최종 갱신", example = "2026-10-03T21:05:00Z", nullable = true) LocalDateTime lastTrackingAt,
            @Schema(description = "마지막 스캔 위치 — 택배사 원문. 「배송중 · 대전 허브 출발」 · 추적 정지의 「마지막 위치」. 스캔 기록이 없으면 null",
                    example = "대전 허브", nullable = true) String lastTrackingLocation,
            @Schema(description = "마지막 스캔 문구 — 택배사 원문. 스캔 기록이 없으면 null", example = "출발", nullable = true)
            String lastTrackingDescription,
            @Schema(description = "반송 감지 시각", nullable = true) LocalDateTime returnDetectedAt,
            @Schema(description = "배송완료 시각", example = "2026-10-05T16:10:00Z", nullable = true) LocalDateTime deliveredAt,
            @Schema(description = "출처 병기 — 항상(§34-7). 자동 확인 · 운영자 처리", example = "자동 확인", nullable = true)
            String deliveredSourceLabel,
            @Schema(description = "구매확정 예정 — 배송완료 + 7일", example = "2026-10-12T16:10:00Z", nullable = true) LocalDateTime confirmDueAt,
            @Schema(description = "구매확정 시각", nullable = true) LocalDateTime confirmedAt,
            @Schema(description = "취소 시각 — 하위주문 전체 취소일 때만", nullable = true) LocalDateTime cancelledAt,
            @Schema(description = "취소 유형 — 소비자 취소 · 준비 시작 전 / 취소 요청 승인 · 브랜드 승인 / 브랜드 직권 취소",
                    example = "브랜드 직권 취소", nullable = true) String cancelTypeLabel,
            @Schema(description = "직권 취소 사유 — 품절 · 상품 하자 · 배송 불가 지역 · 기타", example = "품절", nullable = true)
            String cancelReasonLabel,
            @Schema(description = "직권 취소 시 소비자에게 전달한 설명", example = "준비 중 재고 오차로 2개 세트 옵션이 품절되었습니다.",
                    nullable = true) String cancelReasonDetail
    ) {
    }

    @Schema(name = "SellerOrderCancelRequestBlock")
    public record CancelRequestBlock(
            @Schema(description = "취소 요청 id — 승인·거부 API 경로 키", example = "77") Long cancelRequestId,
            @Schema(description = "요청자 — 주문한 소비자(소비자 앱은 본인 주문만 요청한다). 실명, 없으면 닉네임", example = "양세린",
                    nullable = true) String requesterName,
            @Schema(description = "요청자 = 수취인 — 「(수취인과 동일)」", example = "true") boolean requesterIsRecipient,
            @Schema(description = "공구 상태 — 백필 주문은 null. 검토 중 요청은 발송 전 하위주문에만 걸리므로 「· 발송 전」은 FE 가 붙인다",
                    example = "IN_PROGRESS", nullable = true) GroupBuyStatus groupBuyStatus,
            @Schema(description = "공구 상태 문구", example = "진행중", nullable = true) String groupBuyStatusLabel,
            @Schema(description = "소비자가 고른 사유 — 단순 변심 · 주문 실수 · 다른 결제 수단으로 변경 · 기타", example = "단순 변심")
            String reasonLabel,
            @Schema(description = "소비자 상세 사유", example = "색상을 잘못 골랐어요", nullable = true) String reasonDetail,
            @Schema(description = "요청 시각", example = "2026-10-02T14:30:00Z") LocalDateTime requestedAt,
            @Schema(description = "응답 기한 — 요청 + 1영업일(주말·공휴일 제외)의 끝. 지나면 자동 승인 · PG 즉시 환불",
                    example = "2026-10-05T23:59:59Z") LocalDateTime respondDueAt,
            @Schema(description = "요청 당시 이행 상태 라벨", example = "상품준비중") String statusAtRequestLabel,
            @Schema(description = "준비 시작 후 경과(시간) — 준비 시작 전 요청이면 null", example = "5", nullable = true)
            Long hoursSincePrepareStart,
            @Schema(description = "요청 항목") List<RequestItem> items,
            @Schema(description = "요청 항목 환불 예정 합계 — 배송비 제외", example = "27000") int totalRefundAmount,
            @Schema(description = "남은(미요청·미취소) 항목 수 — 「남은 N건 발송 대기」", example = "1") long remainingItemCount
    ) {
        @Schema(name = "SellerOrderCancelRequestItem")
        public record RequestItem(
                @Schema(description = "주문 항목 id", example = "5013") Long orderProductId,
                @Schema(description = "상품명", example = "글로우 세럼 30ml") String productName,
                @Schema(description = "옵션명", example = "단품", nullable = true) String optionName,
                @Schema(description = "요청 수량", example = "1") int quantity,
                @Schema(description = "환불 예정 금액", example = "27000") int refundAmount
        ) {
        }
    }

    /** 버튼 노출 규칙의 정본 — 「그 탭에서 쓸 수 없는 버튼은 노출하지 않는다」(§34-3)를 서버가 소유한다. */
    @Schema(name = "SellerOrderActions")
    public record Actions(
            @Schema(description = "준비 시작 — 신규 ∧ 검토 중 취소 요청 없음", example = "false") boolean canPrepareStart,
            @Schema(description = "송장 등록 — 상품준비중 ∧ 검토 중 취소 요청 없음", example = "true") boolean canRegisterInvoice,
            @Schema(description = "송장 수정 — 배송중만(배송완료 전까지 · 반송중 불가)", example = "false") boolean canUpdateInvoice,
            @Schema(description = "직권 취소 — 신규·상품준비중 ∧ 검토 중 취소 요청 없음", example = "true") boolean canCancelDirectly,
            @Schema(description = "취소 요청 승인·거부 — 검토 중 취소 요청 있음", example = "false") boolean canDecideCancelRequest
    ) {
    }

    @Schema(name = "SellerOrderHistoryItem")
    public record HistoryItem(
            @Schema(description = "이벤트 코드 — FulfillmentEventType", example = "PREPARE_STARTED") String eventType,
            @Schema(description = "이벤트 문구", example = "준비 시작 · 소비자 취소권 종료") String label,
            @Schema(description = "주체 코드 — SYSTEM · SELLER · ADMIN · CONSUMER · TRACKER", example = "SELLER") String actorType,
            @Schema(description = "주체 문구", example = "브랜드") String actorLabel,
            @Schema(description = "부가 정보 — 송장·사유·수정 전후 값 등. 없으면 null", example = "발주서 다운로드 동시 처리", nullable = true)
            String detail,
            @Schema(description = "발생 시각", example = "2026-10-02T09:00:00Z") LocalDateTime occurredAt
    ) {
    }
}

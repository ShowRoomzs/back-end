package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.OrderBadgeTone;
import showroomz.domain.order.type.StoragePhase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 반품·교환 목록 한 행 — 탭 공통 superset(35 설계서 4-1). 탭별 컬럼 차이는 FE 가 고른다.
 * 행은 클레임이고 묶음(박스)은 {@code collection.size > 1}로 FE 가 그린다. 연락처는 싣지 않는다 — 상세에 마스킹해서만.
 */
public record SellerClaimListItem(
        @Schema(description = "클레임 id — 상세·액션 API 의 키", example = "3021") Long claimId,
        @Schema(description = "접수번호", example = "CLM-3021") String claimNumber,
        @Schema(description = "RETURN · EXCHANGE", example = "RETURN") ClaimType type,
        @Schema(example = "반품") String typeLabel,
        @Schema(description = "소비자명 — 전체 표기", example = "김민지") String consumerName,
        @Schema(example = "데일리 선크림") String productName,
        @Schema(example = "50ml", nullable = true) String optionName,
        @Schema(description = "교환받을 옵션 — 교환만", example = "리필", nullable = true) String exchangeOptionName,
        @Schema(description = "「상품 옵션 → 교환 옵션」", example = "데일리 선크림 50ml → 리필") String productLabel,
        @Schema(description = "재발송·거절 보류 탭의 「보낼 상품·옵션」/「보관 중인 상품」 — 교환 재발송은 새 옵션, 거절은 원래 옵션. "
                + "그 밖의 단계는 null", example = "데일리 선크림 리필", nullable = true) String shipLabel,
        @Schema(description = "신청 수량", example = "1") int quantity,
        ClaimReason reasonCode,
        @Schema(example = "단순 변심") String reasonLabel,
        @Schema(description = "소비자 첨부 사진 수 — 「증빙 N장」", example = "2") int consumerAttachmentCount,
        ClaimStatus status,
        @Schema(example = "검수 대기") String statusLabel,
        @Schema(description = "그 상태가 속한 탭", example = "INSPECTION") ClaimTab stage,
        @Schema(description = "단계 배지 색 — 전부 NEUTRAL(단계는 진행 순서일 뿐)", example = "NEUTRAL") OrderBadgeTone statusTone,
        @Schema(description = "신청일시", example = "2026-10-01T14:22:05Z") LocalDateTime requestedAt,
        @Schema(description = "현재 단계 진입 시각", example = "2026-10-03T09:00:00Z") LocalDateTime stageEnteredAt,
        @Schema(description = "「경과」 열 — 현재 단계 진입부터의 달력일", example = "2") int elapsedDays,
        @Schema(description = "기한 초과 — 회수 대기 방치(소비자 미발송) 또는 검수 기한 경과(브랜드 귀책)", example = "false")
        boolean overdue,
        Collection collection,
        @Schema(description = "입고 확인 시각", nullable = true) LocalDateTime receivedAt,
        @Schema(description = "검수 기한 — 입고 확인 때 발급", nullable = true) LocalDateTime inspectDueAt,
        @Schema(description = "재발송 사유 — EXCHANGE 교환 재발송 · REJECT_RETURN 거절 반송. 재발송 단계가 아니면 null",
                example = "EXCHANGE", nullable = true) String reshipReason,
        @Schema(example = "교환 재발송", nullable = true) String reshipReasonLabel,
        @Schema(nullable = true) DeliveryCarrier reshipCarrier,
        @Schema(example = "CJ대한통운", nullable = true) String reshipCarrierLabel,
        @Schema(nullable = true) String reshipTrackingNumber,
        @Schema(description = "거절 사유", example = "개봉·사용 흔적", nullable = true) String rejectReasonLabel,
        @Schema(description = "브랜드 거절 증빙 수", example = "0") int sellerEvidenceCount,
        @Schema(nullable = true) LocalDateTime rejectedAt,
        @Schema(description = "고지 · 보관 기한 — 거절 보류만, 그 외 null", nullable = true) Storage storage,
        @Schema(description = "완료 탭의 결과 — 그 밖의 탭은 null", nullable = true) Outcome outcome,
        @Schema(description = "완료 탭 「금액」 — 환불 확정액, 환불 대기면 그 항목의 상품 금액. 교환·거절은 null",
                example = "27200", nullable = true) Long amount,
        @Schema(description = "종결일시", nullable = true) LocalDateTime completedAt,
        Actions actions,
        @Schema(description = "행 확장(▸) — 그 하위주문의 항목 전체. 신청분과 살아 있는 분이 갈린다") List<OrderItem> orderItems,
        @Schema(example = "3개 항목 중 1개 신청") String orderSummary
) {

    /** 회수 묶음(박스) — 같은 요청의 클레임들이 회수 송장 하나를 나눠 갖는다. */
    @Schema(name = "SellerClaimCollection")
    public record Collection(
            Long collectionId,
            @Schema(description = "묶음의 클레임 수 — 2 이상이면 FE 가 그룹 행으로 그린다", example = "3") int size,
            @Schema(description = "「CLM-3021 외 2건」의 앞쪽", example = "CLM-3021") String leadClaimNumber,
            @Schema(description = "회수 택배사 — 소비자 입력. 미입력이면 null", nullable = true) DeliveryCarrier carrier,
            @Schema(example = "우체국택배", nullable = true) String carrierLabel,
            @Schema(nullable = true) String trackingNumber,
            @Schema(description = "「최근 추적」 문구", example = "간선하차", nullable = true) String lastTrackingLabel,
            @Schema(nullable = true) LocalDateTime lastTrackingAt,
            @Schema(description = "추적상 브랜드 도착 시각", nullable = true) LocalDateTime arrivedAt
    ) {
    }

    /** 거절 보류 상품의 고지·보관 — 보관 기한은 저장값이 아니라 계산값이다. */
    @Schema(name = "SellerClaimStorage")
    public record Storage(
            @Schema(description = "미결제 고지 횟수", example = "2") int noticeCount,
            @Schema(description = "최종 고지 시각", nullable = true) LocalDateTime lastNoticeAt,
            @Schema(description = "보관 기한 — 고지 2회 미만이면 null(기한 미정)", nullable = true) LocalDateTime storageDueAt,
            @Schema(description = "NOTICE_PENDING 고지 부족 · STORING 보관 중 · EXPIRED 기한 경과(폐기 가능)", example = "STORING")
            StoragePhase phase
    ) {
    }

    @Schema(name = "SellerClaimOutcome")
    public record Outcome(
            @Schema(description = "REFUND_PENDING · RESHIPPING · REFUNDED · EXCHANGED · REJECTED · CANCELLED", example = "REFUNDED")
            String code,
            @Schema(example = "환불 완료") String label,
            @Schema(description = "종결됐는가 — 환불 대기 · 재발송 중은 false", example = "true") boolean finalized
    ) {
    }

    /** 관리 열 버튼 — 그 탭에서 쓸 수 없는 버튼은 노출하지 않는다. 정본은 서버다. */
    @Schema(name = "SellerClaimListActions")
    public record Actions(
            @Schema(description = "입고 확인") boolean canConfirmReceipt,
            @Schema(description = "검수 통과 / 거절") boolean canInspect,
            @Schema(description = "재발송 송장 등록") boolean canRegisterReshipment
    ) {
    }

    @Schema(name = "SellerClaimOrderItem")
    public record OrderItem(
            String productName,
            @Schema(nullable = true) String optionName,
            @Schema(description = "주문 수량", example = "2") int orderedQuantity,
            @Schema(description = "이 요청에서 신청한 수량 — 신청하지 않은 항목은 0", example = "1") int claimedQuantity,
            @Schema(description = "공구가(단가)", example = "27200") int price,
            @Schema(description = "반품 신청 · 교환 신청 · 구매확정 · 배송완료 · 취소 · 반품", example = "반품 신청") String itemStatusLabel
    ) {
    }
}

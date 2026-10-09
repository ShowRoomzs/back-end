package showroomz.api.admin.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 어드민 거래 관리 — 반품·교환(06b) · 환불 관리(06c) · 예외 관리(06d)(1009 기획 수정본 8-2 ~ 8-4). */
public final class AdminTransactionDto {

    private AdminTransactionDto() {
    }

    // ------------------------------------------------------------------ 06b 반품·교환

    @Schema(name = "AdminClaimListItem", description = "파트너 11 과 같은 행 + 어드민 전용 열(브랜드 · 귀책)")
    public record ClaimListItem(
            SellerClaimListItem claim,
            @Schema(example = "○○ 브랜드") String brandName,
            @Schema(description = "귀책 — 항목 단위(일부 반려에서 갈린다)", example = "CONSUMER") ClaimFeeBearer feeBearer,
            @Schema(example = "소비자 귀책") String feeBearerLabel,
            @Schema(description = "검수에서 브랜드 귀책으로 인정됐다") boolean faultChangedToSeller,
            @Schema(description = "목록 보조줄 「소비자 이의 접수」 — 반려 보류 중이고 걸린 이의 문의가 답변 전") boolean disputeOpen,
            @Schema(description = "가장 최근 이의 접수 시각 — 이의가 없었으면 null", nullable = true) LocalDateTime disputedAt
    ) {
    }

    @Schema(name = "AdminClaimSummaryResponse", description = "파트너 11 요약(KPI · 탭 · 유형) + 어드민 「반려 이의 N건」")
    public record ClaimSummary(
            SellerClaimSummaryResponse.Kpi kpi,
            @Schema(example = "{\"ALL\": 12, \"COLLECT_WAIT\": 3, \"COLLECTING\": 2, \"INSPECTION\": 2, \"RESHIP\": 1, \"REJECT_HOLD\": 1, \"DONE\": 3}")
            Map<String, Long> tabCounts,
            @Schema(example = "{\"RETURN\": 7, \"EXCHANGE\": 5}") Map<String, Long> typeCounts,
            @Schema(description = "반려 이의 미처리 — 반려 보류 중이고 이의 문의가 답변 전. 사이드바 「반품·교환」 배지 후보(기획 §38-8 B-11)",
                    example = "1") long disputeCount
    ) {
        public static ClaimSummary of(SellerClaimSummaryResponse base, long disputeCount) {
            return new ClaimSummary(base.kpi(), base.tabCounts(), base.typeCounts(), disputeCount);
        }
    }

    @Schema(name = "AdminClaimDetailResponse")
    public record ClaimDetail(
            SellerClaimDetailResponse claim,
            String brandName,
            ClaimFeeBearer feeBearer,
            String feeBearerLabel,
            @Schema(description = "B2 반려 이의 인용 — 반품 ∧ 반려됨 ∧ 반송 전(반려 보류 · 재발송 대기)") boolean canAcceptDispute,
            @Schema(description = "B2 인용 환불액 — 서버 계산(단가 × 수량 + 차감된 재발송비 환원분) · 수정 불가. "
                    + "인용할 수 없으면 null", example = "27200", nullable = true) Integer disputeRefundAmount,
            @Schema(description = "④ 소비자 이의 — 앱 「이의 제기」로 걸린 가장 최근 문의. 이의가 없으면 null", nullable = true)
            Dispute dispute,
            @Schema(description = "B1 레일 「자동 알림 N회」 — 검수 기한 경과 자동 알림(영업일 10 · 15시 · 하루 1회)")
            InspectNotice inspectNotice,
            @Schema(description = "「검수 기한 N영업일 초과」 — 입고 · 검수 대기이고 기한이 지났을 때만. 그 밖은 null",
                    example = "3", nullable = true) Integer inspectOverdueBusinessDays
    ) {
    }

    @Schema(name = "AdminClaimInspectNotice", description = "검수 기한 경과 자동 알림 — 06d `noticeCount` 와 같은 값")
    public record InspectNotice(
            @Schema(example = "1") int count,
            @Schema(nullable = true) LocalDateTime lastAt
    ) {
    }

    @Schema(name = "AdminClaimDispute", description = "소비자 이의 — 1:1 문의 원문 · 사진 · 접수 시각. 기각은 그 문의의 답변이다")
    public record Dispute(
            @Schema(description = "1:1 문의 ID — 문의 상세 링크", example = "4527") Long inquiryId,
            String content,
            List<String> imageUrls,
            LocalDateTime createdAt,
            @Schema(description = "문의 답변 등록 여부 — 기각 · 안내가 끝났다") boolean answered,
            @Schema(nullable = true) LocalDateTime answeredAt
    ) {
    }

    @Schema(name = "AdminDisputeAcceptRequest", description = "B2 — 반려 이의 인용 → 운영자 사유 환불 편입(집행은 환불 관리). "
            + "환불액은 받지 않는다 — 서버가 계산한다(상세 `disputeRefundAmount`)")
    public record DisputeAcceptRequest(
            @NotBlank @Size(max = 500) @Schema(description = "인용 근거 — 이력 · 환불 관리에 남는다") String detail
    ) {
    }

    @Schema(name = "AdminDisputeAcceptResponse")
    public record DisputeAcceptResponse(
            @Schema(description = "편입된 환불 큐 id", example = "918") Long refundTaskId,
            @Schema(description = "환불번호 — 환불 관리 검색어", example = "RFD-918") String refundNo,
            @Schema(description = "편입액 — 서버 계산", example = "27200") int amount
    ) {
    }

    // ------------------------------------------------------------------ 06c 환불 관리

    public enum RefundTab {
        /** 운영자 사유 환불 — 재확인 후 집행. */
        PENDING,
        /** PG 환불 실패 — 자동 재시도 뒤 운영자 재시도. */
        FAILED,
        /** 완료 — PG 자동 포함 전체(CS 「환불 언제 들어와요?」). */
        DONE
    }

    @Schema(name = "AdminRefundListItem")
    public record RefundItem(
            Long refundTaskId,
            Long orderId,
            String orderNumber,
            Long deliveryGroupId,
            @Schema(nullable = true) String subOrderNumber,
            String brandName,
            RefundTaskSource source,
            @Schema(example = "반품 검수 통과") String sourceLabel,
            RefundTaskOrigin origin,
            @Schema(description = "출처 열 — PG 자동(중립) · 운영자 사유(경고)", example = "PG 자동") String originLabel,
            @Schema(description = "운영자 사유 — 운영자 사유 환불만", nullable = true) String reasonLabel,
            @Schema(nullable = true) String reasonDetail,
            Integer amount,
            RefundTaskStatus status,
            @Schema(example = "환불 완료") String statusLabel,
            int attempt,
            @Schema(description = "실패 사유 — 실패 탭", nullable = true) String lastError,
            @Schema(description = "편입한 운영자 — 운영자 사유 환불만", nullable = true) Long requestedBy,
            LocalDateTime createdAt,
            @Schema(nullable = true) LocalDateTime executedAt,
            @Schema(description = "집행한 운영자 — PG 자동은 null", nullable = true) Long executedBy,
            @Schema(description = "[집행] · [재시도] 가능") boolean executable
    ) {
    }

    @Schema(name = "AdminRefundSummaryResponse")
    public record RefundSummary(
            @Schema(example = "{\"PENDING\": 1, \"FAILED\": 1, \"DONE\": 42}") Map<String, Long> tabCounts,
            @Schema(description = "사이드바 배지 = 집행 대기 + 실패", example = "2") long badge
    ) {
    }

    @Schema(name = "AdminRefundExecuteResponse")
    public record RefundExecuteResponse(
            @Schema(description = "DONE(환불 완료) · FAILED(PG 거절 — 실패 탭) · UNKNOWN(결과 확인 중 — 정리 배치가 닫는다) · "
                    + "SKIPPED(같은 결제의 다른 환불 처리 중 · 잠시 후 다시)", example = "DONE") String outcome,
            RefundItem refund
    ) {
    }

    // ------------------------------------------------------------------ 06d 예외 관리

    public enum ExceptionTab {
        /** 처리 지연 — 당사자가 기한을 넘긴 건. 운영자가 개입할 수 있는 유일한 탭(기본 진입). */
        DELAY,
        /** 배송 예외 — 택배 · 추적 이상. 플랫폼 미개입(처리 주체 열이 CS 답변 문장). */
        DELIVERY
    }

    public enum ExceptionKind {
        SHIP_OVERDUE("발송 기한 경과"),
        INSPECT_OVERDUE("검수 지연"),
        RESHIP_DELAYED("재발송 지연"),
        PICKUP_UNCONFIRMED("집화 확인 필요"),
        TRACKING_STALLED("추적 정지"),
        RETURNING("반송 중"),
        COLLECTION_UNSCANNED("회수 송장 미조회");

        private final String label;

        ExceptionKind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Schema(name = "AdminOrderExceptionItem")
    public record ExceptionItem(
            ExceptionKind kind,
            @Schema(example = "발송 기한 경과") String kindLabel,
            Long orderId,
            String orderNumber,
            Long deliveryGroupId,
            @Schema(description = "클레임 건이면 그 id — [열기]는 06b 상세", nullable = true) Long claimId,
            String brandName,
            @Schema(description = "기준 시각 — 기한 · 송장 등록 · 마지막 갱신 · 반송 감지", nullable = true) LocalDateTime basisAt,
            @Schema(description = "경과(시간) — 기준 시각부터", example = "30") long elapsedHours,
            @Schema(description = "시스템 자동 알림 횟수 — 처리 지연만", nullable = true) Integer noticeCount,
            @Schema(description = "대행 가능 조건 충족(알림 N회 무응답 · 근거 대기) — 상태가 아니라 조건이라 배지가 아니다")
            boolean actOnBehalfAvailable,
            @Schema(description = "처리 주체 — CS 답변 문장", example = "브랜드가 발송해야 합니다 · 자동 알림 중") String handlerLabel
    ) {
    }

    @Schema(name = "AdminOrderExceptionSummary")
    public record ExceptionSummary(
            @Schema(example = "{\"DELAY\": 3, \"DELIVERY\": 4}") Map<String, Long> tabCounts,
            @Schema(description = "사이드바 배지 — 처리 지연 + 배송 예외", example = "7") long badge
    ) {
    }
}

package showroomz.api.admin.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;
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
            @Schema(description = "검수에서 브랜드 귀책으로 인정됐다") boolean faultChangedToSeller
    ) {
    }

    @Schema(name = "AdminClaimDetailResponse")
    public record ClaimDetail(
            SellerClaimDetailResponse claim,
            String brandName,
            ClaimFeeBearer feeBearer,
            String feeBearerLabel,
            @Schema(description = "B2 반려 이의 인용 — 반려 보류 중인 반품") boolean canAcceptDispute
    ) {
    }

    @Schema(name = "AdminDisputeAcceptRequest", description = "B2 — 반려 이의 인용 → 운영자 사유 환불 편입(집행은 환불 관리)")
    public record DisputeAcceptRequest(
            @NotNull @Min(1) @Schema(description = "환불액", example = "27200") Integer amount,
            @NotBlank @Size(max = 500) @Schema(description = "인용 근거 — 이력 · 환불 관리에 남는다") String detail
    ) {
    }

    @Schema(name = "AdminDisputeAcceptResponse")
    public record DisputeAcceptResponse(Long refundTaskId) {
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

package showroomz.api.admin.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.OperatorRefundReason;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        /** 운영자 사유 환불 — 재확인 후 집행(처리 중 포함). */
        PENDING,
        /** PG 환불 실패 — 출처 불문 · 자동 재시도 뒤 운영자 재시도. */
        FAILED,
        /** 완료 — PG 자동 포함 전체(CS 「환불 언제 들어와요?」) · 기간은 집행 시각 기준. */
        DONE
    }

    /** 경로 셀렉트(39 설계서 3-1) — 서버가 발생 경로 집합으로 바꾼다. 교환은 §39-7 #4 「속할 곳이 없다」의 해소다. */
    public enum RefundRoute {
        CANCEL("취소", EnumSet.of(RefundTaskSource.CANCEL_REQUEST_APPROVED, RefundTaskSource.SELLER_DIRECT_CANCEL,
                RefundTaskSource.USER_CANCEL_BEFORE_PREPARE)),
        RETURN("반품", EnumSet.of(RefundTaskSource.CLAIM_RETURN_PASSED)),
        RETURN_SHIPMENT("반송", EnumSet.of(RefundTaskSource.RETURN_COMPLETED)),
        EXCHANGE("교환", EnumSet.of(RefundTaskSource.CLAIM_PAYMENT_CANCELLED)),
        OPERATOR("운영자 사유", EnumSet.of(RefundTaskSource.OPERATOR_REASON));

        private final String label;
        private final Set<RefundTaskSource> sources;

        RefundRoute(String label, Set<RefundTaskSource> sources) {
            this.label = label;
            this.sources = sources;
        }

        public String getLabel() {
            return label;
        }

        public Set<RefundTaskSource> getSources() {
            return sources;
        }

        public static RefundRoute of(RefundTaskSource source) {
            for (RefundRoute route : values()) {
                if (route.sources.contains(source)) {
                    return route;
                }
            }
            throw new IllegalStateException("경로 없음: " + source);
        }
    }

    public enum RefundSort {
        /** 「일시」 최신순 — 탭별 열(편입 · 마지막 실패 · 집행). */
        LATEST,
        /** 환불액 높은순 — 동률은 최신 id 먼저. */
        AMOUNT_DESC
    }

    @Schema(name = "AdminRefundListItem", description = "06c 목록 한 행 — 시안 10열 + 액션 가능 여부. 다이얼로그 값은 상세 API")
    public record RefundItem(
            Long refundTaskId,
            @Schema(description = "환불번호 — 검색어로도 쓴다", example = "RFD-918") String refundNo,
            Long orderId,
            String orderNumber,
            Long deliveryGroupId,
            @Schema(nullable = true) String subOrderNumber,
            @Schema(description = "브랜드 — 하위주문 스냅샷", example = "글로우코스") String brandName,
            RefundTaskSource source,
            @Schema(description = "경로 셀렉트 값", example = "OPERATOR") RefundRoute route,
            @Schema(description = "경로 열 — 서버 문장", example = "반려 이의 인용") String sourceLabel,
            @Schema(description = "경로 보조(회색) — 클레임 · 취소 요청 번호", example = "CLM-3008", nullable = true) String sourceRef,
            RefundTaskOrigin origin,
            @Schema(description = "출처 열 — PG 자동(중립) · 운영자 사유(경고)", example = "운영자 사유") String originLabel,
            @Schema(description = "운영자 사유 — 운영자 사유 환불만", nullable = true) String reasonLabel,
            @Schema(nullable = true) String reasonDetail,
            @Schema(description = "ORIGINAL 원래 결제 · ADDITIONAL 추가 결제(교환 · 반려 재발송비)", example = "ORIGINAL") String paymentKind,
            @Schema(description = "원래 결제의 부분 취소인가") boolean partial,
            @Schema(description = "결제수단 — 카드사 · 간편결제 제공사", example = "신한카드", nullable = true) String paymentMethodLabel,
            @Schema(description = "결제 열 — 「카드 · 원래 부분」", example = "카드 · 원래", nullable = true) String paymentLabel,
            Integer amount,
            RefundTaskStatus status,
            @Schema(example = "집행 대기") String statusLabel,
            @Schema(description = "상태 보조 — 「편입 09.18 14:20 · 김운영」 · 실패 사유 · 「집행 김운영 · 재확인」", nullable = true)
            String statusNote,
            int attempt,
            @Schema(description = "실패 사유(운영자 문장) — 실패 탭", nullable = true) String lastError,
            @Schema(description = "「일시」 열 — 집행 대기 = 편입 · 실패 = 마지막 실패 · 완료 = 집행", nullable = true) LocalDateTime displayAt,
            LocalDateTime createdAt,
            @Schema(nullable = true) LocalDateTime executedAt,
            @Schema(description = "[집행] · [재시도] 가능 — 대기 · 실패이고 결제가 있다") boolean executable
    ) {
    }

    @Schema(name = "AdminRefundSummaryResponse")
    public record RefundSummary(
            @Schema(description = "탭별 건수 · 합계 — PENDING · FAILED · DONE") Map<String, RefundTabStat> tabs,
            @Schema(description = "사이드바 배지 = 집행 대기 + 실패", example = "2") long badge
    ) {
    }

    @Schema(name = "AdminRefundTabStat")
    public record RefundTabStat(
            @Schema(example = "1") long count,
            @Schema(description = "합계 금액", example = "27200") long amount,
            @Schema(description = "실패 탭 — 가장 오래 기다린 소비자의 대기 일수(적재 시각 기준)", nullable = true) Integer oldestWaitingDays,
            @Schema(description = "완료 탭 — 집계 기간(일)", nullable = true) Integer days,
            @Schema(description = "완료 탭 — 출처별 건수 {PG_AUTO, OPERATOR}", nullable = true) Map<String, Long> byOrigin
    ) {
    }

    @Schema(name = "AdminRefundExecuteResponse")
    public record RefundExecuteResponse(
            @Schema(description = "DONE(환불 완료) · FAILED(PG 거절 — 실패 탭) · UNKNOWN(결과 확인 중 — 정리 배치가 닫는다) · "
                    + "SKIPPED(같은 결제의 다른 환불 처리 중 · 잠시 후 다시)", example = "DONE") String outcome,
            RefundItem refund
    ) {
    }

    @Schema(name = "AdminRefundDetail", description = "06c 상세 — M1 집행 재확인 · M2 재시도 다이얼로그가 보는 값 전부")
    public record RefundDetail(
            Long refundTaskId,
            @Schema(example = "RFD-918") String refundNo,
            RefundOrderRef order,
            RefundSourceRef source,
            RefundTaskOrigin origin,
            String originLabel,
            @Schema(description = "운영자 사유 — 운영자 사유 환불만", nullable = true) RefundReason reason,
            @Schema(description = "취소 대상 결제 — 결제가 없는 주문은 null", nullable = true) RefundTarget target,
            @Schema(description = "추가 결제(재발송비) — 클레임 경로만 · 없으면 빈 배열") List<RefundAdditionalPayment> additionalPayments,
            RefundSettlement settlement,
            RefundTaskStatus status,
            String statusLabel,
            int attempt,
            @Schema(description = "자동 시도 상한(최초 포함) — 운영자 사유는 자동 재시도하지 않는다", example = "2") int autoMaxAttempts,
            @Schema(description = "실패 — 실패 상태일 때만", nullable = true) RefundFailure failure,
            @Schema(description = "집행 결과 — 완료일 때만", nullable = true) RefundExecution execution,
            boolean executable,
            @Schema(description = "이 환불 건의 이력 — 오래된순") List<RefundHistory> history
    ) {
    }

    @Schema(name = "AdminRefundOrderRef")
    public record RefundOrderRef(Long orderId, String orderNumber, Long deliveryGroupId,
                                 @Schema(nullable = true) String subOrderNumber, String brandName) {
    }

    @Schema(name = "AdminRefundSourceRef")
    public record RefundSourceRef(
            RefundTaskSource code,
            RefundRoute route,
            String label,
            @Schema(nullable = true) String ref,
            @Schema(description = "근거 클레임 — 반려 이의 인용 등", nullable = true) Long claimId,
            @Schema(nullable = true) Long cancelRequestId,
            @Schema(description = "클레임 요청(박스) — 반품 검수 통과 · 재발송비 환불", nullable = true) Long collectionId
    ) {
    }

    @Schema(name = "AdminRefundReason")
    public record RefundReason(
            @Schema(nullable = true) OperatorRefundReason code,
            String label,
            @Schema(description = "근거", nullable = true) String detail,
            @Schema(nullable = true) Long requestedBy,
            @Schema(example = "김운영", nullable = true) String requestedByName,
            LocalDateTime requestedAt
    ) {
    }

    @Schema(name = "AdminRefundTarget", description = "M1 「취소 대상」 — 원래 결제 · 수단 · PG 거래번호 · 잔액")
    public record RefundTarget(
            String paymentId,
            @Schema(nullable = true) String pgTxId,
            @Schema(example = "ORIGINAL") String paymentKind,
            @Schema(example = "신한카드") String methodLabel,
            int paymentAmount,
            int cancelledAmount,
            @Schema(description = "지금 취소 가능 잔액") int cancellableAmount,
            @Schema(description = "이번 환불액") int amount,
            boolean partial,
            String paymentStatus
    ) {
    }

    @Schema(name = "AdminRefundAdditionalPayment", description = "M1 「추가 결제」 — 클레임 박스의 재발송비 청구")
    public record RefundAdditionalPayment(
            Long chargeId,
            ClaimChargeType type,
            @Schema(example = "반려 재발송비") String typeLabel,
            int amount,
            ClaimChargeStatus status,
            @Schema(example = "소멸") String statusLabel,
            @Schema(description = "운영자 문장 — 「인용 시 결제 취소됨」 · 「차감분 환원 — 환불액에 포함」", nullable = true) String note,
            @Schema(description = "결제된 청구의 결제 id", nullable = true) String claimPaymentId,
            @Schema(nullable = true) String claimPaymentStatus
    ) {
    }

    @Schema(name = "AdminRefundSettlement", description = "정산 모듈 전 — 자리만(39 설계서 0-8)")
    public record RefundSettlement(
            @Schema(example = "BEFORE_SETTLEMENT") String state,
            @Schema(example = "정산 전 · 클로백 없음") String stateLabel,
            @Schema(nullable = true) Object clawback
    ) {
    }

    @Schema(name = "AdminRefundFailure")
    public record RefundFailure(
            @Schema(description = "운영자 문장") String message,
            @Schema(description = "PG 응답 코드 — 상세에만", nullable = true) String code,
            @Schema(nullable = true) LocalDateTime at,
            @Schema(description = "시도별 실패 기록 — 오래된순") List<RefundFailureAttempt> attempts
    ) {
    }

    @Schema(name = "AdminRefundFailureAttempt")
    public record RefundFailureAttempt(LocalDateTime at, String actorType, @Schema(nullable = true) String message) {
    }

    @Schema(name = "AdminRefundExecution")
    public record RefundExecution(
            LocalDateTime executedAt,
            @Schema(description = "집행 운영자 — PG 자동은 null", nullable = true) Long executedBy,
            @Schema(nullable = true) String executedByName,
            @Schema(nullable = true) Long paymentCancelId,
            @Schema(description = "PG 취소 거래 id", nullable = true) String pgCancellationId
    ) {
    }

    @Schema(name = "AdminRefundHistory")
    public record RefundHistory(
            LocalDateTime at,
            @Schema(example = "REFUND_ENQUEUED_BY_OPERATOR") String type,
            @Schema(example = "운영자 사유 환불 편입") String label,
            @Schema(example = "ADMIN") String actor,
            @Schema(nullable = true) String actorName,
            @Schema(nullable = true) String detail
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

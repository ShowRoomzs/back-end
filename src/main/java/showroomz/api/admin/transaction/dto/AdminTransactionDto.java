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
import showroomz.domain.order.type.RefundMethod;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 어드민 거래 관리 — 반품·교환(06b) · 환불 관리(06c)(1009 기획 수정본 8-2 · 8-3). 예외 관리(06d)는 {@link AdminOrderExceptionDto}. */
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
            @Schema(description = "가장 최근 이의 접수 시각 — 이의가 없었으면 null", nullable = true) LocalDateTime disputedAt,
            @Schema(description = "「경과」 열 — 현재 단계 진입부터의 영업일(기산일 불산입 · 주말·공휴일 제외). 「입고 3영업일」의 앞말은 "
                    + "FE 가 `claim.stage` · `claim.reshipReason` 으로 고른다. 파트너 `claim.elapsedDays`(달력일)는 그대로다", example = "3")
            int elapsedBusinessDays
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
            @Schema(description = "주문 — 「주문 상세 열기」(`GET /v1/admin/orders/{orderId}`)", example = "5012") Long orderId,
            @Schema(description = "하위주문", example = "1024") Long deliveryGroupId,
            String brandName,
            ClaimFeeBearer feeBearer,
            String feeBearerLabel,
            @Schema(description = "B2 반려 이의 인용 — 반품 ∧ 반려됨 ∧ 반송 전(반려 보류 · 재발송 대기)") boolean canAcceptDispute,
            @Schema(description = "B2 인용 환불액 — 서버 계산(단가 × 수량 + 차감된 재발송비 환원분) · 수정 불가. "
                    + "인용할 수 없으면 null", example = "27200", nullable = true) Integer disputeRefundAmount,
            @Schema(description = "검수 무응답 운영자 환불(41 보고 4번) — 입고 · 검수 대기 ∧ 기한 경과 ∧ 자동 알림 N회") boolean canRefundUnanswered,
            @Schema(description = "검수 무응답 환불액 — 단가 × 수량 · 수정 불가. 열 수 없으면 null", nullable = true) Integer unansweredRefundAmount,
            @Schema(description = "④ 소비자 이의 — 앱 「이의 제기」로 걸린 가장 최근 문의. 이의가 없으면 null", nullable = true)
            Dispute dispute,
            @Schema(description = "B1 레일 「자동 알림 N회」 — 검수 기한 경과 자동 알림(영업일 10 · 15시 · 하루 1회)")
            InspectNotice inspectNotice,
            @Schema(description = "「검수 기한 N영업일 초과」 — 입고 · 검수 대기이고 기한이 지났을 때만. 그 밖은 null",
                    example = "3", nullable = true) Integer inspectOverdueBusinessDays,
            @Schema(description = "B4 법정 청약철회 기한 — 운영자가 대신 연 클레임(B6)만. 그 밖은 null", nullable = true)
            LegalDeadline legalDeadline,
            @Schema(description = "B4 정산 반영 — 그 하위주문이 들어간 정산. 정산 생성 전이면 null(「정산 전 · 생성 시 반영」)", nullable = true)
            ClaimSettlement settlement,
            @Schema(description = "최초 배송비 차감 근거(보조줄 · 1021 설계서 3-3) — 이 박스에서 최초 배송비를 뺐을 때만. 그 밖은 null",
                    example = "무료배송 기준 50,000원 미만이 되어 최초 배송비 3,000원 차감(소비자 귀책 반품)", nullable = true)
            String deductionReason,
            @Schema(description = "B2 인용하면 생길 운임 환급 예정액 — 인용할 수 있을 때만(canAcceptDispute). 그 밖은 null. "
                    + "인용 환불액(disputeRefundAmount)과 따로 운임 환급으로 나간다(원래 결제액을 넘으면 운영자 계좌 송금)", nullable = true)
            DisputePreview disputePreview
    ) {
    }

    @Schema(name = "AdminClaimDisputePreview", description = "B2 인용 전 운임 환급 예정액(1021 설계서 4-3 · 4-4)")
    public record DisputePreview(
            @Schema(description = "선불 운임 환급 — 귀책이 브랜드로 바뀌어 소비자가 낸 반품 운임을 돌려준다. 이미 브랜드 귀책이면 0", example = "3000")
            int prepaidFreightRefund,
            @Schema(description = "차감했던 최초 배송비 환원 — 통과분 환불에서 실제로 뺐을 때만", example = "3000") int initialFeeRestored,
            @Schema(description = "합계", example = "6000") int total
    ) {
    }

    @Schema(name = "AdminClaimLegalDeadline", description = "전자상거래법 제17조③ — 공급받은 날부터 3개월 · 그 사실을 안 날부터 30일 중 이른 날")
    public record LegalDeadline(
            @Schema(description = "공급일 — 하위주문 배송완료일", example = "2026-08-27") LocalDate suppliedOn,
            @Schema(description = "공급일 + 3개월", example = "2026-11-27") LocalDate supplyDeadline,
            @Schema(description = "하자 인지일 — B6 개설 때 운영자 입력(선택). 없으면 null", example = "2026-09-17", nullable = true)
            LocalDate noticedOn,
            @Schema(description = "인지일 + 30일 — 인지일이 없으면 null", example = "2026-10-17", nullable = true) LocalDate noticeDeadline,
            @Schema(description = "적용 기한 — 둘 중 이른 날(인지일이 없으면 공급 기준)", example = "2026-10-17") LocalDate appliedDeadline
    ) {
    }

    @Schema(name = "AdminClaimSettlement")
    public record ClaimSettlement(
            @Schema(example = "STL-2610-004") String settlementNumber,
            @Schema(description = "정산 상태 — REVIEWING · ADJUSTING · PAYOUT_SCHEDULED · PAID · PAYOUT_FAILED", example = "PAYOUT_SCHEDULED")
            String status,
            @Schema(example = "지급 예정") String statusLabel,
            @Schema(description = "정산 확정 시각 — 확정 전 null", nullable = true) LocalDateTime confirmedAt,
            @Schema(description = "검수 통과 시 다음 정산에서 차감(브랜드 수취액 · 리워드) — 반품이고 검수 판정 전일 때만 참. 교환 · 반려는 환불이 없어 차감도 없다")
            boolean deductOnPass
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

    @Schema(name = "AdminClaimRefundRequest", description = "검수 무응답 운영자 환불 편입 — 환불액은 서버 계산")
    public record ClaimRefundRequest(
            @NotBlank @Size(max = 500) @Schema(description = "근거 — 이력 · 환불 관리에 남는다") String detail
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
                RefundTaskSource.USER_CANCEL_BEFORE_PREPARE, RefundTaskSource.LOST_IN_TRANSIT,
                RefundTaskSource.STALL_REFUND_CHOSEN)),
        RETURN("반품", EnumSet.of(RefundTaskSource.CLAIM_RETURN_PASSED)),
        RETURN_SHIPMENT("반송", EnumSet.of(RefundTaskSource.RETURN_COMPLETED)),
        EXCHANGE("교환", EnumSet.of(RefundTaskSource.CLAIM_PAYMENT_CANCELLED)),
        OPERATOR("운영자 사유", EnumSet.of(RefundTaskSource.OPERATOR_REASON)),
        /** 귀책 변경 운임 환급(1021 설계서 4-5) — PG 취소분과 원래 결제액 초과분(계좌 송금) 모두. */
        FREIGHT("운임 환급", EnumSet.of(RefundTaskSource.FREIGHT_REFUND));

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
            @Schema(description = "[집행] · [재시도] 가능 — 대기 · 실패이고 결제가 있다") boolean executable,
            @Schema(description = "[철회] 가능 — 집행 전 운영자 사유 환불(반려 이의 인용 제외)") boolean voidable,
            @Schema(description = "[수동 완료 기록] 가능 — 대기 · 실패(계좌 송금 행 제외)") boolean manuallyCompletable,
            @Schema(description = "환불 수단 — PG_CANCEL(원 결제 취소) · BANK_TRANSFER(원래 결제액 초과분 · 운영자 계좌 송금 · 결제 열 「계좌 송금」)",
                    example = "PG_CANCEL") RefundMethod method,
            @Schema(description = "[송금 완료 기록] 가능 — 송금 대기(BANK_TRANSFER ∧ PENDING)") boolean transferable
    ) {
    }

    @Schema(name = "AdminRefundSummaryResponse")
    public record RefundSummary(
            @Schema(description = "탭별 건수 · 합계 — PENDING · FAILED · DONE") Map<String, RefundTabStat> tabs,
            @Schema(description = "사이드바 배지 = 집행 대기 + 실패", example = "2") long badge,
            @Schema(description = "송금 대기 건수 — 집행 대기 탭에 함께 있다(1021 설계서 4-5)", example = "1") long transferPending,
            @Schema(description = "집행 대기 중 PG 취소 금액 — 집행 대기 합계 − 계좌 송금 금액(58 계획 5-14)", example = "27200")
            long pgCancelAmount,
            @Schema(description = "송금 대기 금액(transferPending 건의 합)", example = "3000") long bankTransferAmount
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

    @Schema(name = "AdminRefundVoidRequest", description = "편입 철회 — 집행 전 운영자 사유 환불만(반려 이의 인용 제외)")
    public record RefundVoidRequest(
            @NotBlank @Size(max = 300) @Schema(description = "철회 사유 — 이력에 남는다", example = "오편입 · 금액 재산정") String reason
    ) {
    }

    @Schema(name = "AdminRefundManualCompleteRequest", description = "수동 완료 기록 — PG 콘솔 등 밖에서 이미 돌려준 환불(PG 호출 없음)")
    public record RefundManualCompleteRequest(
            @Size(max = 100) @Schema(description = "PG 취소 거래번호 — 있으면 적는다", example = "tosspay_cancel_7f3a", nullable = true)
            String pgCancellationId,
            @NotBlank @Size(max = 500) @Schema(description = "근거 — 어디서 어떻게 돌려줬는지", example = "포트원 콘솔에서 10.09 14:20 부분 취소") String note
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
            @Schema(description = "M2 「자동 재시도 N회 · 실패 · 시각」 — PG 자동 환불만. 운영자 사유 환불 · 아직 실패가 없으면 null", nullable = true)
            RefundAutoRetry autoRetry,
            @Schema(description = "실패 — 실패 상태일 때만", nullable = true) RefundFailure failure,
            @Schema(description = "집행 결과 — 완료일 때만", nullable = true) RefundExecution execution,
            boolean executable,
            @Schema(description = "[철회] 가능 — 집행 전 운영자 사유 환불(반려 이의 인용 제외)") boolean voidable,
            @Schema(description = "[수동 완료 기록] 가능 — 대기 · 실패") boolean manuallyCompletable,
            @Schema(description = "이 환불 건의 이력 — 오래된순") List<RefundHistory> history,
            @Schema(description = "환불 수단 — BANK_TRANSFER 면 `transfer` 가 있다", example = "PG_CANCEL") RefundMethod method,
            @Schema(description = "M3 계좌 송금 — 원래 결제액 초과 운임 환급 행만(1021 설계서 4-5) · 그 밖은 null", nullable = true)
            RefundTransfer transfer,
            @Schema(description = "M1 운임 정산 요약 — 이 환불의 클레임 박스에 운임 정산 행이 있을 때만(58 계획 5-14). 그 밖은 null", nullable = true)
            RefundFreightAdjustment freightAdjustment,
            @Schema(description = "M3 짝 행 — 같은 환급을 나눈 다른 환불 행(계좌 송금 행이면 PG 취소 행 · PG 취소 행이면 계좌 송금 행). 나뉘지 않았으면 null",
                    example = "919", nullable = true)
            Long pairedRefundTaskId
    ) {
    }

    @Schema(name = "AdminRefundFreightAdjustment", description = "M1 운임 정산 요약 — 브랜드 정산 「± 운임 정산」과 같은 행")
    public record RefundFreightAdjustment(
            @Schema(description = "REFUND_CONSUMER(소비자 환급) · CHARGE_CONSUMER(소비자 부담 · 환불액에서 차감)", example = "REFUND_CONSUMER")
            showroomz.domain.order.type.FreightAdjustmentDirection direction,
            @Schema(example = "소비자 환급") String directionLabel,
            @Schema(description = "금액 — 환급은 선불 운임 + (이의 인용이면) 최초 배송비 환원 · 부담은 착불 운임", example = "6000") int amount,
            @Schema(description = "원래 결제액을 넘어 계좌 송금으로 간 행 — 없으면 null", example = "920", nullable = true)
            Long bankTransferRefundTaskId
    ) {
    }

    @Schema(name = "AdminRefundTransfer", description = "M3 계좌 송금 — 시스템은 송금하지 않는다. 운영자가 송금한 뒤 계좌 · 완료를 기록한다")
    public record RefundTransfer(
            @Schema(description = "[송금 완료 기록] 가능 — 송금 대기") boolean transferable,
            @Schema(description = "폼 기본값 — 소비자가 저장해 둔 환불 계좌. 없으면 null(1:1 문의로 받는다)", nullable = true)
            RefundTransferAccount suggestedAccount,
            @Schema(description = "이 환급 건으로 소비자가 쓴 1:1 문의(「환불 계좌 안내」) 최신 1건 — 「1:1 문의 #N 로 받은 계좌」", nullable = true,
                    example = "4527") Long linkedInquiryId,
            @Schema(description = "송금 완료 기록 — 완료 뒤에만", nullable = true) RefundTransferRecord transferred
    ) {
    }

    @Schema(name = "AdminRefundTransferAccount")
    public record RefundTransferAccount(
            @Schema(example = "088") String bankCode,
            @Schema(example = "신한은행", nullable = true) String bankName,
            @Schema(description = "계좌번호 — 폼 기본값이라 원문", example = "110123456789") String accountNumber,
            @Schema(example = "김수민") String holderName
    ) {
    }

    @Schema(name = "AdminRefundTransferRecord")
    public record RefundTransferRecord(
            @Schema(example = "088") String bankCode,
            @Schema(example = "신한은행", nullable = true) String bankName,
            @Schema(description = "계좌번호 — 뒤 4자리만", example = "********6789") String accountNumberMasked,
            @Schema(example = "김수민") String holderName,
            LocalDateTime transferredAt,
            Long transferredBy,
            @Schema(nullable = true) String transferredByName
    ) {
    }

    @Schema(name = "AdminRefundTransferCompleteRequest", description = "M3 송금 완료 기록 — 운영자가 이미 송금한 계좌")
    public record RefundTransferCompleteRequest(
            @Size(max = 10) @Schema(description = "은행 코드", example = "088") String bankCode,
            @Size(max = 30) @Schema(description = "계좌번호 — 숫자 · 하이픈", example = "110-123-456789") String accountNumber,
            @Size(max = 50) @Schema(description = "예금주 — 주문자 실명 확인은 운영자 체크", example = "김수민") String holderName,
            @Schema(description = "「송금을 완료했습니다」 확인 — true 여야 한다", example = "true") boolean transferConfirmed
    ) {
    }

    @Schema(name = "AdminRefundAutoRetry")
    public record RefundAutoRetry(
            @Schema(description = "자동 재시도 횟수 — 시스템 실패 수 − 1(최초 자동 집행은 재시도로 세지 않는다)", example = "1") int count,
            @Schema(description = "마지막 시스템 실패 시각") LocalDateTime lastAt,
            @Schema(description = "자동 시도가 상한(`autoMaxAttempts`)에 닿았다 — 실패 탭에 올라온 이유. 거짓이면 배치가 다시 시도한다")
            boolean exhausted
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

    @Schema(name = "AdminRefundSettlement", description = "이 환불과 정산(39 설계서 0-8 · 44 어드민 정산관리 설계서 8-3)")
    public record RefundSettlement(
            @Schema(description = "BEFORE_SETTLEMENT(정산 생성 전 — 생성 때 반영) · SETTLED(이미 생성된 정산의 항목 — 차감으로 다음 정산에)",
                    example = "BEFORE_SETTLEMENT") String state,
            @Schema(example = "정산 전 · 정산 생성 시 반영") String stateLabel,
            @Schema(description = "그 하위주문이 들어간 정산 — 정산 전 null", nullable = true, example = "STL-2610-004")
            String settlementNumber,
            @Schema(description = "이 환불로 생긴 차감(측별 2행 중 덜 끝난 쪽 상태) — 없으면 null", nullable = true)
            RefundClawback clawback,
            @Schema(description = "M1 집행 재확인 「클로백(다음 정산 차감)」 예정액 — 정산 생성 후 ∧ 차감 대상(운영자 사유 · 운영자 개설 반품 통과) ∧ "
                    + "아직 차감 행이 없을 때만. 집행하면 같은 식으로 `clawback` 이 생긴다", nullable = true)
            ExpectedClawback expectedClawback
    ) {
    }

    @Schema(name = "AdminRefundClawback")
    public record RefundClawback(
            @Schema(example = "CLW-0003") String clawbackNumber,
            @Schema(description = "PENDING · APPLIED · UNRECOVERABLE", example = "PENDING") String status,
            @Schema(example = "차감 예정") String statusLabel,
            @Schema(description = "브랜드 측 차감액 — 그 측 행이 없으면 null", example = "24208", nullable = true) Long brandAmount,
            @Schema(description = "인플루언서 측 차감액 — 그 측 행이 없으면 null", example = "2720", nullable = true) Long creatorAmount
    ) {
    }

    @Schema(name = "AdminRefundExpectedClawback", description = "INFLUENCER = 단위 리워드 × 환불 수량 · BRAND = 환불액 − INFLUENCER − floor(INFLUENCER × 리워드 부가세율)")
    public record ExpectedClawback(
            @Schema(example = "24208") long brandAmount,
            @Schema(example = "2720") long creatorAmount
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
}

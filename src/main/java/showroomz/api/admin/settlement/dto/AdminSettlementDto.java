package showroomz.api.admin.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import showroomz.api.admin.settlement.type.AdminPayoutAccountSource;
import showroomz.api.admin.settlement.type.AdminTaxInvoiceVerifyResult;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementItemStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.SettlementTone;
import showroomz.global.dto.PaginationInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 어드민 정산 관리(07a · 07b) 응답 — 44 어드민 설계서 7절. 숫자는 정산 행의 스냅샷이고 파트너센터 · 스튜디오와 <b>같은 행</b>을 읽는다.
 * 어드민만 보는 값 — 계좌 전체 · 처리 이력 · 수취자 행 전량 · 재분배 횟수.
 */
public final class AdminSettlementDto {

    private AdminSettlementDto() {
    }

    // ------------------------------------------------------------------ 7-1 목록 · 7-2 툴바 · 7-3 요약

    @Schema(name = "AdminSettlementListResponse",
            description = "목록 — 정산 탭은 content = AdminSettlementListItem. 증빙 탭은 AdminSettlementEvidenceItem(문서 1건) · 차감 탭은 AdminSettlementClawbackItem(차감 번호 1건)")
    public record ListResponse<T>(
            List<T> content,
            PaginationInfo pageInfo,
            @Schema(description = "합계 행 — 전체 탭만 · 지급 예정 · 지급 완료 · 분배 실패만 합산(정산 확인 중 · 조정 협의 제외)",
                    nullable = true) Footer footer,
            @Schema(description = "탭 툴바 — 조정 협의 · 분배 실패 · 증빙 · 차감 탭", nullable = true) Toolbar toolbar) {
    }

    @Schema(name = "AdminSettlementListItem")
    public record ListItem(
            Long settlementId,
            @Schema(example = "STL-2609-006") String settlementNumber,
            Long groupBuyId,
            @Schema(example = "여름 수분 세럼 공구") String groupBuyTitle,
            Long marketId,
            @Schema(example = "벨라코스") String brandName,
            Long creatorId,
            @Schema(example = "소연_쇼룸") String showroomName,
            CreatorBusinessType creatorBusinessType,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            long confirmedSalesAmount,
            long brandPayoutAmount,
            long creatorPayoutAmount,
            @Schema(description = "어드민은 분배 실패(PAYOUT_FAILED)를 접지 않는다") SettlementStatus status,
            String statusLabel,
            SettlementTone statusTone,
            @Schema(description = "「일정」 열 — 상태별 다음 날짜", nullable = true) LocalDateTime scheduleAt,
            @Schema(description = "REVIEW_DUE · AGREEMENT_DUE · PAYOUT_DUE · PAID_AT · FAILED_AT", example = "REVIEW_DUE")
            String scheduleKind) {
    }

    @Schema(name = "AdminSettlementFooter")
    public record Footer(long count, long confirmedSalesAmount, long brandPayoutAmount, long pgFeeAmount,
                         long creatorPayoutAmount, long withholdingAmount, long creatorVatAmount, long rewardVatAmount,
                         long platformFeeAmount) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AdminSettlementToolbar", description = "탭마다 채우는 칸이 다르다 — 값이 있는 칸만 내려간다")
    public record Toolbar(
            @Schema(description = "[조정 협의] 보류 합계 = 확정 거래액 합") Long heldAmount,
            @Schema(description = "[조정 협의] 가장 이른 합의 기한") LocalDateTime earliestDeadlineAt,
            @Schema(description = "[분배 실패] 미지급 합계") Long undeliveredAmount,
            @Schema(description = "[분배 실패] 실패 수취자 — 「인플루언서 2 · 브랜드 1」") String failedPayeeLabel,
            @Schema(description = "[분배 실패] 최장 경과일") Long elapsedDays,
            @Schema(description = "[증빙 · 차감] 건수") Long count,
            @Schema(description = "[증빙] 운영자 조치 필요(확인 대기 + 발행 대기)") Long operatorActionCount,
            @Schema(description = "[증빙] 지급을 막는 문서 수") Long payoutBlockedCount,
            @Schema(description = "[차감] 미회수 건수") Long unrecoverableCount,
            @Schema(description = "[차감] 미회수 금액") Long unrecoverableAmount) {
    }

    @Schema(name = "AdminSettlementSummary")
    public record Summary(
            @Schema(description = "탭 숫자 — ALL · REVIEWING · ADJUSTING · PAYOUT_FAILED · EVIDENCE · CLAWBACK",
                    example = "{\"ALL\": 12, \"REVIEWING\": 3, \"ADJUSTING\": 1, \"PAYOUT_FAILED\": 1, \"EVIDENCE\": 0, \"CLAWBACK\": 0}")
            Map<String, Long> tabCounts,
            @Schema(description = "GNB 배지 = 운영자 조치만 — 분배 실패 + 차감 미회수 + 증빙 확인 대기 · 발행 대기. 조정 협의 · 주민번호 미등록은 세지 않는다",
                    example = "1") long gnbBadge) {
    }

    // ------------------------------------------------------------------ 7-4 상세

    @Schema(name = "AdminSettlementDetail")
    public record Detail(
            Long settlementId,
            @Schema(example = "STL-2609-006") String settlementNumber,
            SettlementStatus status,
            String statusLabel,
            SettlementTone statusTone,
            Overview overview,
            Stage stage,
            Breakdown breakdown,
            @Schema(description = "차감 반영(D3) — 이 정산에서 회수한 이전 회차 환불(없으면 [])") List<AppliedClawback> clawbacksApplied,
            @Schema(description = "조정 내역(D4) — 없으면 null", nullable = true) Adjustment adjustment,
            @Schema(description = "3자 분배 — 확정 전(REVIEWING · ADJUSTING) null(「확정 후 표시」)", nullable = true) Payouts payouts,
            @Schema(description = "증빙 — 확정 시 생긴다(없으면 [])") List<TaxDocument> taxDocuments,
            @Schema(description = "고정 지급비 — 계약 읽기 전용 · 정산 대상 아님", nullable = true) FixedFee fixedFee,
            Items items,
            Rail rail,
            Actions actions,
            @Schema(description = "처리 이력 — 최신순") List<History> history) {
    }

    @Schema(name = "AdminSettlementOverview")
    public record Overview(
            Long groupBuyId,
            @Schema(example = "GB-20260814-041") String groupBuyNumber,
            @Schema(example = "여름 수분 세럼 공구") String groupBuyTitle,
            Long contractId,
            Long marketId,
            String brandName,
            Long creatorId,
            String showroomName,
            CreatorBusinessType creatorBusinessType,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            @Schema(description = "주문 종결 시각") LocalDateTime ordersClosedAt,
            @Schema(description = "정산 생성 시각") LocalDateTime createdAt,
            @Schema(description = "하위주문 수") long orderCount,
            @Schema(description = "종결 하위주문 수 — 정산은 전부 종결된 뒤 생기므로 orderCount 와 같다") long closedCount) {
    }

    @Schema(name = "AdminSettlementStage", description = "단계 줄 — 생성 · 확인 → (조정) → 확정 → 지급")
    public record Stage(
            @Schema(description = "REVIEW · ADJUSTMENT · CONFIRMED · PAID", example = "REVIEW") String current,
            @Schema(description = "조정 없이 확정됐다 — 「조정」 칸을 건너뜀으로") boolean adjustmentSkipped) {
    }

    @Schema(name = "AdminSettlementBreakdown", description = "금액 분해(두 축) — 요율은 정산 행의 스냅샷")
    public record Breakdown(BrandAxis brand, CreatorAxis creator,
                            @Schema(description = "플랫폼 몫") long platformShareAmount) {
    }

    @Schema(name = "AdminSettlementBrandAxis")
    public record BrandAxis(
            long grossOrderAmount,
            SettlementPartyDto.AmountCount cancel,
            @JsonProperty("return") @Schema(description = "반품 차감") SettlementPartyDto.AmountCount returned,
            SettlementPartyDto.AmountCount deliveryException,
            long confirmedSalesAmount,
            RateAmount pgFee,
            RateAmount platformFee,
            @Schema(description = "원래 리워드 — rewardAmount 와 다르면 합의로 바뀐 것") long originalRewardAmount,
            long rewardAmount,
            RateAmount rewardVat,
            SettlementPartyDto.AmountCount reshipFee,
            long consumerDeliveryFee,
            long payoutBeforeClawback,
            long clawbackAmount,
            long payoutAmount) {
    }

    @Schema(name = "AdminSettlementCreatorAxis")
    public record CreatorAxis(
            CreatorBusinessType businessType,
            long rewardAmount,
            @Schema(description = "리워드 차감") long clawbackAmount,
            @Schema(description = "[비사업자] 원천징수 — 사업자는 null", nullable = true) Withholding withholding,
            @Schema(description = "[사업자] 부가세") long vatAmount,
            long payoutAmount) {
    }

    @Schema(name = "AdminSettlementWithholding")
    public record Withholding(long amount, BigDecimal incomeRate, BigDecimal localRate) {
    }

    @Schema(name = "AdminSettlementRateAmount")
    public record RateAmount(BigDecimal rate, long amount) {
    }

    @Schema(name = "AdminSettlementAppliedClawback")
    public record AppliedClawback(String clawbackNumber, String originSettlementNumber, String orderNumber,
                                  String productName, String reasonLabel, long brandAmount, long creatorAmount) {
    }

    @Schema(name = "AdminSettlementAdjustment", description = "조정 내역 — 이슈 스레드 설계서 AdjustmentSummary + 제안별 미리보기")
    public record Adjustment(
            Long adjustmentId,
            @Schema(description = "20b 이슈 스레드 — 어드민 소통 스레드 이슈 탭으로 연다") Long threadId,
            @Schema(description = "OPEN · AGREED · EXPIRED") String status,
            String statusLabel,
            @Schema(description = "SELLER · CREATOR") String requesterType,
            LocalDateTime openedAt,
            LocalDateTime deadlineAt,
            @Schema(nullable = true) Integer remainingBusinessDays,
            long originalRewardAmount,
            long maxRewardAmount,
            @Schema(nullable = true) Long agreedRewardAmount,
            @Schema(nullable = true) Long finalRewardAmount,
            @Schema(nullable = true) LocalDateTime closedAt,
            List<AdjustmentProposal> proposals) {
    }

    @Schema(name = "AdminSettlementAdjustmentProposal")
    public record AdjustmentProposal(
            Long proposalId,
            int seq,
            @Schema(description = "SELLER · CREATOR") String proposerType,
            String proposerName,
            LocalDateTime proposedAt,
            long rewardAmount,
            @Schema(nullable = true) String reason,
            @Schema(description = "PENDING · ACCEPTED · REJECTED · COUNTERED · CLOSED") String status,
            String statusLabel,
            @Schema(nullable = true) LocalDateTime respondedAt,
            @Schema(description = "이 금액으로 확정했다면") ProposalPreview preview) {
    }

    @Schema(name = "AdminSettlementProposalPreview")
    public record ProposalPreview(long creatorNetAmount, long brandPayoutAmount) {
    }

    @Schema(name = "AdminSettlementPayouts")
    public record Payouts(
            @Schema(description = "브랜드 · 인플루언서 · 플랫폼") List<Payout> rows,
            long pgFeeAmount,
            Check check) {
    }

    @Schema(name = "AdminSettlementPayout")
    public record Payout(
            Long payoutId,
            SettlementPayee payee,
            String payeeLabel,
            @Schema(example = "벨라코스") String payeeName,
            long amount,
            @Schema(nullable = true) String bankName,
            @Schema(description = "계좌 전체 — 어드민 기본정보 정책. 스냅샷(브랜드는 확정 시점 · 인플루언서는 지시 시점)이 있으면 그것, 없으면 회원 정보의 현재 계좌", nullable = true)
            String accountNumber,
            @Schema(nullable = true) String accountHolder,
            @Schema(description = "SNAPSHOT(브랜드 확정 시점 · 인플루언서 지시 시점 스냅샷) · CURRENT_PROFILE(회원 정보 현재 값) · NONE", example = "SNAPSHOT")
            String accountSource,
            PayoutStatus status,
            String statusLabel,
            SettlementTone statusTone,
            @Schema(nullable = true) LocalDate dueDate,
            @Schema(nullable = true) LocalDateTime requestedAt,
            @Schema(nullable = true) LocalDateTime paidAt,
            @Schema(nullable = true) LocalDateTime failedAt,
            @Schema(nullable = true) String failCode,
            @Schema(nullable = true) String failReason,
            @Schema(nullable = true) String pgReference,
            @Schema(description = "재분배 횟수") int attempt) {
    }

    @Schema(name = "AdminSettlementPayoutCheck",
            description = "tfoot 검산 — 수취자 합 + PG 수수료 + 원천징수 = 확정 거래액 + 재발송비 + 소비자 배송비")
    public record Check(long total, long inflowAmount, long confirmedSalesAmount, long withholdingAmount,
                        boolean balanced) {
    }

    @Schema(name = "AdminSettlementTaxDocument")
    public record TaxDocument(Long documentId, String type, String typeLabel, String direction, String counterpartyName,
                              long supplyAmount, long vatAmount, long totalAmount, String status, String statusLabel,
                              @Schema(description = "앞 8 · 뒤 4 마스킹") String approvalNumber, LocalDate issuedDate,
                              LocalDate dueDate, LocalDateTime submittedAt, LocalDateTime verifiedAt, String rejectReason,
                              String rejectReasonLabel, String fileName, TaxDocumentActions actions) {
    }

    @Schema(name = "AdminSettlementTaxDocumentActions")
    public record TaxDocumentActions(boolean canVerify, boolean canRegister) {
    }

    @Schema(name = "AdminSettlementFixedFee")
    public record FixedFee(Long contractId, @Schema(nullable = true) Integer amount, @Schema(nullable = true) String trigger,
                           @Schema(nullable = true) String triggerLabel) {
    }

    @Schema(name = "AdminSettlementItems")
    public record Items(long total, int returnCount, @Schema(description = "교환 재발송 건수") int exchangeCount,
                        @Schema(description = "최근 5건 — 주문번호 내림차순. 전체는 GET …/items") List<Item> rows,
                        ItemsFooter footer) {
    }

    @Schema(name = "AdminSettlementItemsFooter")
    public record ItemsFooter(long paidAmount, long settledAmount,
                              @Schema(description = "항목 리워드 합") long itemRewardTotal,
                              @Schema(description = "정산 리워드 — 합의로 바뀌었으면 항목 합과 다르다") long rewardAmount) {
    }

    @Schema(name = "AdminSettlementItem")
    public record Item(
            Long itemId,
            Long orderId,
            Long deliveryGroupId,
            String orderNumber,
            @Schema(nullable = true) String subOrderNumber,
            @Schema(description = "소비자 이름 마스킹") String consumerNameMasked,
            String productName,
            @Schema(nullable = true) String optionName,
            int quantity,
            int returnedQuantity,
            int settledQuantity,
            long unitPrice,
            long paidAmount,
            SettlementItemStatus status,
            String statusLabel,
            long settledAmount,
            BigDecimal rewardRate,
            long rewardAmount) {
    }

    @Schema(name = "AdminSettlementRail", description = "우 레일 — 상태 · 금액 · 날짜 · 보류 사유 · 분배 실패")
    public record Rail(
            SettlementStatus status,
            String statusLabel,
            SettlementTone statusTone,
            long confirmedSalesAmount,
            long brandPayoutAmount,
            long creatorPayoutAmount,
            LocalDateTime reviewDueAt,
            @Schema(description = "조정 협의 합의 기한", nullable = true) LocalDateTime deadlineAt,
            @Schema(nullable = true) LocalDateTime confirmedAt,
            @Schema(nullable = true) SettlementConfirmReason confirmReason,
            @Schema(nullable = true) String confirmReasonLabel,
            @Schema(nullable = true) LocalDate payoutDueDate,
            @Schema(description = "지급 예정일 근거 — 「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」", nullable = true)
            String payoutBasis,
            @Schema(nullable = true) LocalDateTime paidAt,
            @Schema(description = "지급 보류 사유(인플루언서 행) — 확정 전 · 보류 없음이면 []") List<BlockReason> blockReasons,
            @Schema(description = "분배 실패 행 — PAYOUT_FAILED 일 때 첫 실패 행", nullable = true) FailedPayout failedPayout) {
    }

    @Schema(name = "AdminSettlementBlockReason")
    public record BlockReason(@Schema(example = "RESIDENT_NUMBER_MISSING") String code,
                              @Schema(example = "주민등록번호 미등록") String label) {
    }

    @Schema(name = "AdminSettlementFailedPayout")
    public record FailedPayout(Long payoutId, SettlementPayee payee, String payeeLabel, long amount,
                               LocalDateTime failedAt, @Schema(nullable = true) String failCode,
                               @Schema(nullable = true) String failReason, int attempt,
                               @Schema(description = "재분배 상한") int retryLimit) {
    }

    @Schema(name = "AdminSettlementActions", description = "버튼 — 판정은 서버 한 곳 · 커맨드가 다시 검사한다")
    public record Actions(
            @Schema(description = "PAYOUT_FAILED ∧ 실패 행 재분배 횟수 < 상한") boolean canRedistribute,
            @Schema(description = "인플루언서 세금계산서 확인 대기(SUBMITTED) — 증빙 단계 전까지 false") boolean canVerifyInvoice,
            @Schema(description = "브랜드 세금계산서 발행 대기(PENDING_ISSUE) — 증빙 단계 전까지 false") boolean canRegisterBrandInvoice,
            @Schema(description = "확정 후(REVIEWING · ADJUSTING 아님)") boolean canDownloadStatement) {
    }

    @Schema(name = "AdminSettlementHistory")
    public record History(SettlementEventType eventType, String label, SettlementActorType actorType,
                          @Schema(description = "시스템 · PG · 운영자 이름 · 브랜드명 · 쇼룸명") String actorLabel,
                          @Schema(nullable = true) String detail, LocalDateTime occurredAt) {
    }

    // ------------------------------------------------------------------ 7-9 by-thread · 7-5 M3

    @Schema(name = "AdminSettlementByThread", description = "20b 이슈 패널의 「정산 영향 · 진행 단계」 보강 — 7-4 의 rail + adjustment")
    public record ByThread(Long settlementId, String settlementNumber, Rail rail, Adjustment adjustment) {
    }

    @Schema(name = "AdminSettlementVerifyRequest", description = "M4 승인번호 대조 — 운영자는 번호를 입력하지 않고 결과만 고른다")
    public record VerifyRequest(
            @NotNull @Schema(description = "MATCH(확인) · AMOUNT_MISMATCH · RECIPIENT_MISMATCH · NOT_FOUND(반려 사유)",
                    requiredMode = Schema.RequiredMode.REQUIRED, example = "MATCH")
            AdminTaxInvoiceVerifyResult result) {
    }

    // ------------------------------------------------------------------ 7-2 증빙 탭

    @Schema(name = "AdminSettlementEvidenceItem",
            description = "증빙 탭 행 = 문서 1건(정산 1건에 2건일 수 있다) · 처리 끝(확인 · 발행 · 생성 완료)은 뺀다. "
                    + "비사업자 주민번호 미등록은 문서가 아니라 보류 사유지만 type = RESIDENT_NUMBER_MISSING 가상 행으로 함께 내린다")
    public record EvidenceItem(
            @Schema(description = "문서 id — 가상 행(주민번호 미등록)은 null", nullable = true) Long documentId,
            Long settlementId,
            String settlementNumber,
            String groupBuyTitle,
            @Schema(description = "조치할 상대 — 인플루언서 건은 쇼룸명 · 브랜드 건은 브랜드명") String counterpartyName,
            CreatorBusinessType creatorBusinessType,
            @Schema(description = "CREATOR_TAX_INVOICE · BRAND_TAX_INVOICE · BRAND_TAX_INVOICE_CREDIT · WITHHOLDING_RECEIPT · RESIDENT_NUMBER_MISSING")
            String type,
            String typeLabel,
            @Schema(example = "인플루언서 → 플랫폼") String direction,
            @Schema(description = "인플루언서 입력 시각", nullable = true) LocalDateTime inputAt,
            @Schema(description = "기한 — 브랜드 세금계산서", nullable = true) LocalDate dueAt,
            @Schema(description = "문서 상태 · 가상 행은 WAITING_CREATOR") String status,
            String statusLabel,
            @Schema(description = "BLOCKING(인플루언서 몫 지급을 막는다) · NONE") String payoutImpact) {
    }

    @Schema(name = "AdminSettlementClawbackItem",
            description = "차감 탭 행 = 차감 번호 1건(측별 행을 한 줄로 합친다 · 상태가 다르면 측별로 따로 내린다)")
    public record ClawbackItem(
            @Schema(example = "CLW-0003") String clawbackNumber,
            Long originSettlementId,
            @Schema(example = "STL-2609-002") String originSettlementNumber,
            @Schema(nullable = true) String orderNumber,
            @Schema(nullable = true) String productName,
            String brandName,
            String showroomName,
            @Schema(example = "구매확정 후 하자") String reasonLabel,
            @Schema(description = "환불 상품 금액분") long refundAmount,
            @Schema(description = "브랜드 측 차감(이월 포함 합)") long brandAmount,
            @Schema(description = "인플루언서 측 차감(이월 포함 합)") long creatorAmount,
            @Schema(description = "PENDING · APPLIED · UNRECOVERABLE — 이월 중이면 PENDING", nullable = true) String brandStatus,
            @Schema(nullable = true) String brandStatusLabel,
            @Schema(nullable = true) String creatorStatus,
            @Schema(nullable = true) String creatorStatusLabel,
            @Schema(description = "반영된 정산(마지막)", nullable = true) String appliedSettlementNumber,
            @Schema(description = "미회수 사유 — 인플루언서 탈퇴 · 브랜드 탈퇴", nullable = true) String unrecoverableReasonLabel,
            LocalDateTime createdAt) {
    }

    @Schema(name = "AdminSettlementRedistributeRequest")
    public record RedistributeRequest(
            @NotNull @Schema(description = "CURRENT_PROFILE(회원 정보의 현재 계좌로 다시 스냅샷) · PREVIOUS(지난 지시의 계좌 그대로) — 운영자는 계좌를 입력하지 않는다",
                    requiredMode = Schema.RequiredMode.REQUIRED, example = "CURRENT_PROFILE")
            AdminPayoutAccountSource accountSource) {
    }
}

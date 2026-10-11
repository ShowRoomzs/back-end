package showroomz.api.creator.settlement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementItemStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.SettlementTone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 쇼룸 스튜디오 정산 관리(12) 응답(44 스튜디오 설계서 2절). 응답에 <b>없는 것</b> — 고정 지급비 · PG · 플랫폼 수수료 행 · 브랜드 수취액
 * (분배 행 금액으로만 보인다) · 반품 · 교환 배송비 블록 · 이력 · 소비자 정보. FE 가 숨기는 것이 아니라 서버가 안 내린다.
 */
public final class CreatorSettlementDto {

    private CreatorSettlementDto() {
    }

    // ------------------------------------------------------------------ 목록(2-2)

    @Schema(name = "CreatorSettlementListItem")
    public record ListItem(
            Long settlementId,
            String settlementNumber,
            Long groupBuyId,
            String groupBuyTitle,
            @Schema(example = "벨라코스") String brandName,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            long confirmedSalesAmount,
            @Schema(description = "「15%」 · 상품마다 다르면 「상품별」", example = "15%") String rewardRateLabel,
            long rewardAmount,
            @Schema(description = "차감 — 있으면 FE 가 「차감 −6,150원」 보조줄") long rewardClawbackAmount,
            long withholdingAmount,
            long creatorVatAmount,
            @Schema(description = "실지급액") long creatorPayoutAmount,
            @Schema(description = "4종 — 분배 실패는 PAID 로 접힌다") SettlementStatus status,
            String statusLabel,
            SettlementTone statusTone,
            @Schema(description = "내 행의 지급 예정일 · 지급일 — 확인 중 · 협의 · 보류 · 실패면 null", nullable = true) LocalDate payoutDate,
            @Schema(description = "SCHEDULED(「09.09 예정」) · PAID(「09.02」)", nullable = true) String payoutDateKind
    ) {
    }

    // ------------------------------------------------------------------ 요약(2-3)

    @Schema(name = "CreatorSettlementSummary", description = "인플루언서 전체 기준 — 검색어 · 상태 칩과 무관. 빈 값은 amount = null")
    public record Summary(
            @Schema(description = "누적 수령 — 내 행 PAID · 실지급액(세후)") SettlementPartyDto.Kpi totalPaid,
            @Schema(description = "지급 예정 — 내 행 SCHEDULED · BLOCKED") ScheduledKpi payoutScheduled,
            ReviewingKpi reviewing,
            CountKpi adjusting,
            SettlementPartyDto.StatusCounts statusCounts,
            @Schema(description = "GNB 배지 = 요청 가능 창이 열린 정산 확인 중 + 세금계산서 입력 대기 · 반려") long attentionCount
    ) {
    }

    @Schema(name = "CreatorSettlementScheduledKpi")
    public record ScheduledKpi(@Schema(nullable = true) Long amount, long count,
                               @Schema(description = "가장 가까운 지급 예정일(보류 건 제외)", nullable = true) LocalDate nearestDate) {
    }

    @Schema(name = "CreatorSettlementReviewingKpi")
    public record ReviewingKpi(long count, @Schema(nullable = true) LocalDateTime nearestDueAt) {
    }

    @Schema(name = "CreatorSettlementCountKpi")
    public record CountKpi(long count) {
    }

    // ------------------------------------------------------------------ 상세(2-4)

    @Schema(name = "CreatorSettlementDetail")
    public record Detail(
            SettlementHeader settlement,
            Timeline timeline,
            @Schema(description = "정산 확인 중에서만 · 그 외 null", nullable = true) Review review,
            Breakdown breakdown,
            @Schema(description = "차감 반영(D3) — 없으면 []") List<Clawback> clawbacks,
            Payouts payouts,
            @Schema(description = "내 계좌 · 뒤 6자리 — 계좌가 없으면 null", nullable = true) Payment payment,
            @Schema(nullable = true) SettlementPartyDto.AdjustmentBlock adjustment,
            @Schema(description = "비사업자만 · 사업자 null", nullable = true) Withholding withholding,
            @Schema(description = "사업자 ∧ 확정 후만 · 그 전 null", nullable = true) TaxInvoice taxInvoice,
            Items items
    ) {
    }

    @Schema(name = "CreatorSettlementHeader")
    public record SettlementHeader(
            Long settlementId, String settlementNumber, Long groupBuyId, String groupBuyTitle, String brandName,
            Long marketId, LocalDateTime periodStartAt, LocalDateTime periodEndAt,
            SettlementStatus status, String statusLabel, SettlementTone statusTone,
            CreatorBusinessType creatorBusinessType, boolean taxInvoiceRequired
    ) {
    }

    @Schema(name = "CreatorSettlementTimeline")
    public record Timeline(
            LocalDateTime createdAt,
            LocalDateTime ordersClosedAt,
            LocalDateTime reviewDueAt,
            @Schema(nullable = true) LocalDateTime confirmedAt,
            @Schema(nullable = true) SettlementConfirmReason confirmReason,
            @Schema(nullable = true) String confirmReasonLabel,
            @Schema(description = "내 행 지급 예정일 — 사업자 발행 전 · 확인 중 · 협의면 null", nullable = true) LocalDate payoutDueDate,
            @Schema(description = "「자동 확정 08.28 + 3영업일」 · 「발행 확인 후 + 3영업일」 등 — 서버 문장", nullable = true) String payoutDueNote,
            @Schema(nullable = true) LocalDateTime paidAt
    ) {
    }

    @Schema(name = "CreatorSettlementReview")
    public record Review(boolean canRequestAdjustment, LocalDateTime requestDeadlineAt,
                         @Schema(description = "조정 상한 — 「조정 후 브랜드 수취액 ≥ 0」 · 10원 절사", example = "5396720") long maxRewardAmount) {
    }

    @Schema(name = "CreatorSettlementBreakdown", description = "분해 — 브랜드 축 상단(확정 거래액까지) + 인플루언서 축")
    public record Breakdown(
            long grossOrderAmount,
            SettlementPartyDto.AmountCount cancel,
            @JsonProperty("return") @Schema(description = "반품 차감") SettlementPartyDto.AmountCount returned,
            SettlementPartyDto.AmountCount deliveryException,
            long confirmedSalesAmount,
            @Schema(nullable = true) String rewardRateLabel,
            @Schema(description = "「상품별」일 때 주석") List<ProductRate> rewardRates,
            long originalRewardAmount,
            long rewardAmount,
            long rewardClawbackAmount,
            long rewardAfterClawback,
            BigDecimal withholdingIncomeRate,
            BigDecimal withholdingLocalRate,
            long withholdingAmount,
            BigDecimal creatorVatRate,
            long creatorVatAmount,
            long creatorPayoutAmount
    ) {
    }

    @Schema(name = "CreatorSettlementProductRate")
    public record ProductRate(String productName, BigDecimal rate) {
    }

    @Schema(name = "CreatorSettlementClawback")
    public record Clawback(
            String clawbackNumber,
            String orderNumber,
            String originGroupBuyTitle,
            @Schema(nullable = true) LocalDateTime originSettlementPaidAt,
            String reasonLabel,
            long refundAmount,
            long amount,
            @Schema(nullable = true) BigDecimal rewardRate,
            LocalDateTime createdAt
    ) {
    }

    @Schema(name = "CreatorSettlementPayouts")
    public record Payouts(
            @Schema(description = "확정 전(REVIEWING · ADJUSTING)이면 true — 금액만 보이고 상태는 「확인 기간 후 지급」 · 「보류 중」") boolean shownAfterConfirm,
            @Schema(description = "인플루언서(내 행) 첫 행 · 브랜드 · 플랫폼") List<PayoutRow> rows
    ) {
    }

    @Schema(name = "CreatorSettlementPayoutRow")
    public record PayoutRow(
            SettlementPayee payee,
            @Schema(example = "인플루언서") String payeeLabel,
            @Schema(example = "소연_쇼룸") String name,
            long amount,
            @Schema(nullable = true) String note,
            @Schema(description = "내 행 실패만 FAILED(「지급 확인 중」) · 브랜드 행 실패는 SCHEDULED(「지급 예정」)로 접힌다") PayoutStatus status,
            @Schema(example = "10.12 지급 예정") String statusLabel,
            @Schema(nullable = true) LocalDate date
    ) {
    }

    @Schema(name = "CreatorSettlementPayment")
    public record Payment(
            @Schema(nullable = true) String bankName,
            @Schema(nullable = true) String accountNumberMasked,
            @Schema(nullable = true) String accountHolder,
            @Schema(nullable = true) LocalDate dueDate,
            @Schema(nullable = true) String dueNote,
            @Schema(nullable = true) LocalDateTime paidAt,
            @Schema(description = "내 행 PAID 일 때만", nullable = true) String pgReference
    ) {
    }

    @Schema(name = "CreatorSettlementWithholding")
    public record Withholding(
            @Schema(example = "SHOWROOMZ") String receiptIssuer,
            long amount,
            @Schema(description = "지급 완료 ∧ 원천징수영수증 생성 완료") boolean receiptAvailable,
            @Schema(example = "징수 예정액") String receiptLabel
    ) {
    }

    @Schema(name = "CreatorSettlementTaxInvoice")
    public record TaxInvoice(
            @Schema(description = "PENDING_INPUT(발행 필요) · SUBMITTED(확인 중) · REJECTED(반려) · VERIFIED(확인 완료)") String cardStatus,
            Supplier supplier,
            long supplyAmount,
            long vatAmount,
            long totalAmount,
            @Schema(nullable = true) String approvalNumber,
            @Schema(nullable = true) LocalDateTime submittedAt,
            @Schema(nullable = true) String attachmentName,
            @Schema(description = "AMOUNT_MISMATCH · RECIPIENT_MISMATCH · NOT_FOUND", nullable = true) String rejectReason,
            @Schema(nullable = true) String rejectReasonLabel,
            @Schema(nullable = true) LocalDateTime rejectedAt,
            @Schema(nullable = true) LocalDateTime verifiedAt
    ) {
    }

    @Schema(name = "CreatorSettlementSupplier", description = "공급받는자 — SHOWROOMZ(설정)")
    public record Supplier(String name, String representative, String registrationNumber, String address,
                           String taxEmail) {
    }

    @Schema(name = "CreatorSettlementItems")
    public record Items(@Schema(description = "명세 미리보기 5건 · 주문번호 내림차순") List<Item> preview, long totalCount,
                        @Schema(description = "확정 후만") boolean downloadAvailable) {
    }

    @Schema(name = "CreatorSettlementItem", description = "명세 행 — 소비자 열 없음")
    public record Item(
            String orderNumber,
            String productName,
            @Schema(nullable = true) String optionName,
            int quantity,
            int settledQuantity,
            long paidAmount,
            SettlementItemStatus status,
            String statusLabel,
            long settledAmount,
            BigDecimal rewardRate,
            @Schema(description = "내 리워드") long rewardAmount
    ) {
    }

    // ------------------------------------------------------------------ 세금계산서 제출(3-1)

    @Schema(name = "CreatorTaxInvoiceSubmitResponse")
    public record TaxInvoiceSubmitResponse(Long settlementId, TaxInvoice taxInvoice) {
    }
}

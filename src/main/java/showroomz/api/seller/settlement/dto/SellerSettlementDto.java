package showroomz.api.seller.settlement.dto;

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
 * 파트너센터 정산 관리(13) 응답(44 파트너 설계서 2절). 라벨 · 톤은 서버가 정한다 — FE 매핑표 없음. 응답에 <b>없는 것</b>은 서버가 안
 * 내린다 — 고정 지급비 · 인플루언서 원천징수 금액(분해) · 이력 · 소비자 식별 정보(명세의 마스킹 이름만).
 */
public final class SellerSettlementDto {

    private SellerSettlementDto() {
    }

    // ------------------------------------------------------------------ 목록(2-2)

    @Schema(name = "SellerSettlementListItem")
    public record ListItem(
            Long settlementId,
            @Schema(example = "STL-2610-004") String settlementNumber,
            Long groupBuyId,
            @Schema(example = "여름 수분 세럼 공구") String groupBuyTitle,
            Influencer influencer,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            @Schema(description = "확정 거래액") long confirmedSalesAmount,
            @Schema(description = "수수료 = PG + 플랫폼(시안 「수수료」 한 열)") long feeAmount,
            long rewardAmount,
            long rewardVatAmount,
            @Schema(description = "브랜드 수취액(차감 후)") long brandPayoutAmount,
            @Schema(description = "REVIEWING · ADJUSTING · PAYOUT_SCHEDULED · PAID — 분배 실패는 PAID 로 접힌다") SettlementStatus status,
            String statusLabel,
            SettlementTone statusTone,
            @Schema(description = "「지급(예정)일」 열 — 브랜드 행 기준") Schedule schedule
    ) {
    }

    @Schema(name = "SellerSettlementInfluencer")
    public record Influencer(Long creatorId, @Schema(example = "소연_쇼룸") String showroomName) {
    }

    @Schema(name = "SellerSettlementSchedule", description = "REVIEW_DUE(「09.28까지 확인」) · PAYOUT_DUE(「10.12 예정」) · PAID(「07.24」) · NONE(—)")
    public record Schedule(String kind, @Schema(nullable = true) LocalDate date) {
    }

    // ------------------------------------------------------------------ 요약(2-3)

    @Schema(name = "SellerSettlementSummary", description = "마켓 전체 기준 — 검색어 · 상태 칩과 무관. 빈 값은 amount = null")
    public record Summary(
            @Schema(description = "누적 지급 — 브랜드 행 PAID") SettlementPartyDto.Kpi paid,
            @Schema(description = "지급 예정 — 브랜드 행 SCHEDULED") SettlementPartyDto.Kpi scheduled,
            @Schema(description = "다음 지급 예정일", nullable = true) LocalDate nextPayoutDate,
            @Schema(description = "지급 예정 중 차감 반영 건이 있는가") boolean scheduledHasClawback,
            @Schema(description = "정산 확인 중 건수(요청 가능 창 열림)") long reviewingCount,
            @Schema(description = "가장 임박한 확인 마감", nullable = true) LocalDateTime reviewingDueAt,
            long adjustingCount,
            SettlementPartyDto.StatusCounts statusCounts,
            @Schema(description = "GNB 배지 = 요청 가능 창이 열린 정산 확인 중 — 조정 협의 「내 응답 필요」는 연결·소통 배지가 센다") long attentionCount
    ) {
    }

    // ------------------------------------------------------------------ 상세(2-4)

    @Schema(name = "SellerSettlementDetail")
    public record Detail(
            Long settlementId,
            String settlementNumber,
            GroupBuyRef groupBuy,
            Influencer influencer,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            SettlementStatus status,
            String statusLabel,
            SettlementTone statusTone,
            Dates dates,
            @Schema(nullable = true) SettlementPartyDto.AdjustmentBlock adjustment,
            Breakdown breakdown,
            @Schema(description = "3자 분배 — 브랜드 첫 행. 확정 전(REVIEWING · ADJUSTING) null(「확정 후 표시」)", nullable = true)
            List<Payout> payouts,
            @Schema(description = "지급 정보 — 확정 전 null", nullable = true) Payment payment,
            @Schema(description = "SHOWROOMZ 발행 세금계산서 — 확정 시 생긴다 · 확정 전 null", nullable = true) List<TaxDocument> taxDocuments,
            InfluencerTax influencerTax,
            ClaimShipping claimShipping,
            @Schema(description = "명세 미리보기 5건 — 주문번호 내림차순") List<Item> items,
            long itemTotalCount,
            Actions actions
    ) {
    }

    @Schema(name = "SellerSettlementGroupBuyRef")
    public record GroupBuyRef(Long groupBuyId, String title) {
    }

    @Schema(name = "SellerSettlementDates")
    public record Dates(
            LocalDateTime createdAt,
            LocalDateTime ordersClosedAt,
            LocalDateTime reviewDueAt,
            @Schema(nullable = true) LocalDateTime confirmedAt,
            @Schema(nullable = true) SettlementConfirmReason confirmReason,
            @Schema(nullable = true) String confirmReasonLabel,
            @Schema(description = "브랜드 행 지급 예정일", nullable = true) LocalDate payoutDueDate,
            @Schema(description = "브랜드 행 지급 완료 시각", nullable = true) LocalDateTime paidAt,
            @Schema(description = "정산 근거 — 「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」", nullable = true) String payoutBasis
    ) {
    }

    @Schema(name = "SellerSettlementBreakdown", description = "정산 금액 분해 — 브랜드 축(계산 순서)")
    public record Breakdown(
            long grossOrderAmount,
            SettlementPartyDto.AmountCount cancel,
            @JsonProperty("return") @Schema(description = "반품 차감") SettlementPartyDto.AmountCount returned,
            SettlementPartyDto.AmountCount deliveryException,
            long confirmedSalesAmount,
            RateAmount pgFee,
            PlatformFee platformFee,
            @Schema(description = "원래 리워드 — rewardAmount 와 다르면 합의로 바뀐 것") long originalRewardAmount,
            long rewardAmount,
            RateAmount rewardVat,
            SettlementPartyDto.AmountCount reshipFee,
            @Schema(description = "소비자 결제 배송비 — 브랜드 가산(§46 B-6 미결 행)") long consumerDeliveryFee,
            @Schema(description = "차감 전 브랜드 수취액") long brandPayoutBeforeClawback,
            @Schema(description = "차감 반영 — 이전 회차 환불의 회수(없으면 [])") List<Clawback> clawbacks,
            long brandClawbackAmount,
            @Schema(description = "이번 지급액") long brandPayoutAmount
    ) {
    }

    @Schema(name = "SellerSettlementRateAmount")
    public record RateAmount(@Schema(example = "0.0300") BigDecimal rate, long amount) {
    }

    @Schema(name = "SellerSettlementPlatformFee")
    public record PlatformFee(BigDecimal rate, long amount, @Schema(description = "정상 요율(베타 종료 후)") BigDecimal normalRate) {
    }

    @Schema(name = "SellerSettlementClawback")
    public record Clawback(
            String clawbackNumber,
            String originSettlementNumber,
            String originGroupBuyTitle,
            String orderNumber,
            String reasonLabel,
            long amount,
            @Schema(description = "수정세금계산서 상태 — PENDING_ISSUE · ISSUED", nullable = true) String creditInvoiceStatus
    ) {
    }

    @Schema(name = "SellerSettlementPayout")
    public record Payout(
            SettlementPayee payee,
            @Schema(example = "우리") String label,
            long amount,
            @Schema(description = "「리워드 548,000 − 원천징수 18,084(3.3%)」 · 플랫폼 「리워드 부가세」", nullable = true) String note,
            @Schema(description = "수취자 상태 — 남의 행의 분배 실패는 SCHEDULED 로 접는다 · 브랜드 행 실패만 FAILED") PayoutStatus status,
            @Schema(example = "지급 예정") String statusLabel,
            @Schema(nullable = true) LocalDate dueDate,
            @Schema(nullable = true) LocalDateTime paidAt
    ) {
    }

    @Schema(name = "SellerSettlementPayment")
    public record Payment(
            @Schema(nullable = true) String bankName,
            @Schema(description = "뒤 6자리 노출", nullable = true) String accountMasked,
            @Schema(nullable = true) String accountHolder,
            @Schema(nullable = true) LocalDate payoutDueDate,
            @Schema(nullable = true) LocalDateTime paidAt,
            @Schema(description = "PG 참조번호 — 브랜드 행 PAID 일 때만", nullable = true) String pgReference
    ) {
    }

    @Schema(name = "SellerSettlementTaxDocument")
    public record TaxDocument(
            Long documentId,
            @Schema(description = "BRAND_TAX_INVOICE · BRAND_TAX_INVOICE_CREDIT") String type,
            String typeLabel,
            long supplyAmount,
            long vatAmount,
            long totalAmount,
            @Schema(description = "PENDING_ISSUE(운영팀이 {due}까지 발행) · ISSUED") String status,
            String statusLabel,
            @Schema(nullable = true) LocalDate dueDate,
            @Schema(nullable = true) LocalDate issuedDate,
            @Schema(nullable = true) String approvalNumber,
            boolean downloadable
    ) {
    }

    @Schema(name = "SellerSettlementInfluencerTax", description = "인플루언서 세무 — 표시값만(「브랜드가 챙길 인플루언서 증빙은 없습니다」)")
    public record InfluencerTax(CreatorBusinessType businessType, String withholdingType,
                                @Schema(example = "비사업자 · 원천징수 3.3% · 플랫폼 신고") String label) {
    }

    @Schema(name = "SellerSettlementClaimShipping", description = "반품 · 교환 배송비 — 분해 밖 · 표시 전용(1-1)")
    public record ClaimShipping(
            @Schema(description = "소비자 부담 — 반품 배송비 차감 합 · 브랜드 수취") SettlementPartyDto.AmountCount consumer,
            @Schema(description = "브랜드 부담 — 요금표 × 건수 · 정산 대상 아님") SettlementPartyDto.AmountCount brand
    ) {
    }

    @Schema(name = "SellerSettlementItem")
    public record Item(
            String orderNumber,
            @Schema(nullable = true) String subOrderNumber,
            @Schema(description = "소비자 이름 마스킹") String consumerNameMasked,
            String productName,
            @Schema(nullable = true) String optionName,
            int quantity,
            @Schema(description = "정산 반영 수량 — 부분 반품은 「확정 2/3」") int settledQuantity,
            long unitPrice,
            long paidAmount,
            SettlementItemStatus status,
            String statusLabel,
            long settledAmount,
            BigDecimal rewardRate,
            long rewardAmount
    ) {
    }

    @Schema(name = "SellerSettlementActions")
    public record Actions(
            @Schema(description = "정산 확인 중 ∧ 마감 전 ∧ 협의 없음") boolean canRequestAdjustment,
            @Schema(description = "조정 협의 차례 — MY_TURN · OPEN_FLOOR") boolean canRespondAdjustment,
            @Schema(description = "확정 후만") boolean canDownloadStatement,
            @Schema(description = "브랜드 세금계산서 발행본 등록 후") boolean canDownloadTaxInvoice
    ) {
    }
}

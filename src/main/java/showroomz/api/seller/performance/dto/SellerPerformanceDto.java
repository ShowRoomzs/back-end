package showroomz.api.seller.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.api.common.performance.dto.PerformanceFigure;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.seller.settlement.dto.SellerSettlementDto;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.performance.type.PerformanceTone;
import showroomz.global.dto.PaginationInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 파트너센터 성과 관리(12) 응답(48 파트너 설계서 3절). 지표는 전부 {@link PerformanceFigure} — 값 · 상태 · 원래 값 · 근거.
 * 응답에 <b>없는 것</b>은 서버가 안 내린다 — 인플루언서 실지급 · 원천징수 · 플랫폼 몫 · 소비자 식별값 · 응답(동의 · 제안) 버튼.
 *
 * <p>비율은 % 값(소수 1자리 · 93.0 = 93.0%) · ROAS 는 % 정수(578 = 578%) · 금액은 원.
 */
public final class SellerPerformanceDto {

    private SellerPerformanceDto() {
    }

    // ------------------------------------------------------------------ 목록(3-2)

    @Schema(name = "SellerPerformanceListPage")
    public record ListPage(List<ListItem> content, PaginationInfo pageInfo,
                           @Schema(description = "조회기간 표기 — 결과 공구들의 시작 ~ 종료", nullable = true) ListPeriod listPeriod) {
    }

    @Schema(name = "SellerPerformanceListPeriod")
    public record ListPeriod(LocalDate from, LocalDate to) {
    }

    @Schema(name = "SellerPerformanceListItem", description = "집계 중 행은 여섯 지표 전부 value = null(「—」) · 조정 협의 행은 payout 만 NEGOTIATING")
    public record ListItem(
            Long groupBuyId,
            @Schema(example = "수분 앰플 공구") String groupBuyTitle,
            Influencer influencer,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            PerformanceStatus status,
            String statusLabel,
            PerformanceTone statusTone,
            @Schema(description = "쇼룸 방문(명)") PerformanceFigure<Long> visitors,
            @Schema(description = "전환율(%) = 구매확정 ÷ 쇼룸 방문") PerformanceFigure<BigDecimal> conversionRate,
            PerformanceFigure<Long> confirmedSales,
            @Schema(description = "정산액 — 브랜드 실지급") PerformanceFigure<Long> payout,
            PerformanceFigure<Long> confirmedQuantity,
            @Schema(description = "구매확정률(%) = 구매확정 ÷ 주문") PerformanceFigure<BigDecimal> confirmRate,
            @Schema(description = "상세 열람 가능 — 집계 중 행도 상세(D2)가 열린다") boolean detailAvailable
    ) {
    }

    @Schema(name = "SellerPerformanceInfluencer")
    public record Influencer(Long creatorId, @Schema(example = "데일리뷰티랩") String showroomName) {
    }

    // ------------------------------------------------------------------ 요약(3-3)

    @Schema(name = "SellerPerformanceSummary", description = "모집단 = 지급 완료 공구 합산 · 검색어 · 상태 칩과 무관 · 평균은 합산 비율(Σ분자 ÷ Σ분모)")
    public record Summary(
            Population population,
            PerformanceFigure<Long> confirmedSales,
            PerformanceFigure<Long> visitors,
            PerformanceFigure<Long> avgAov,
            PerformanceFigure<BigDecimal> avgConversionRate,
            PerformanceFigure<Long> avgRoas,
            GenderRatio genderRatio,
            PerformanceFigure<BigDecimal> avgConfirmRate,
            PerformanceFigure<BigDecimal> avgRepurchaseRate,
            @Schema(description = "상태 칩 숫자 — 모집단 전체") Map<PerformanceStatus, Long> statusCounts
    ) {
    }

    @Schema(name = "SellerPerformancePopulation")
    public record Population(@Schema(example = "PAID") PerformanceStatus status, long count) {
    }

    @Schema(name = "SellerPerformanceGenderRatio", description = "성비(%) — 미확인은 분모에서 뺀다 · 없으면 null")
    public record GenderRatio(@Schema(nullable = true) Integer female, @Schema(nullable = true) Integer male,
                              @Schema(nullable = true) String basis) {
    }

    // ------------------------------------------------------------------ 상세(3-4)

    @Schema(name = "SellerPerformanceDetail")
    public record Detail(
            Long groupBuyId,
            String groupBuyTitle,
            Influencer influencer,
            @Schema(description = "[이 인플루언서와 재협업 제안] — 연결·소통 화면 이동용", nullable = true) Long connectionId,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            PerformanceStatus status,
            String statusLabel,
            PerformanceTone statusTone,
            @Schema(description = "D2 배너 — 집계 중일 때만", nullable = true) Aggregating aggregating,
            ContractBar contract,
            Kpi kpi,
            Traffic traffic,
            Sales sales,
            Delivery delivery,
            Breakdown breakdown,
            Consumers consumers,
            Cost cost,
            @Schema(description = "정산 연결 — 집계 중이면 null", nullable = true) SettlementLink settlement
    ) {
    }

    @Schema(name = "SellerPerformanceAggregating")
    public record Aggregating(
            @Schema(description = "종결되지 않은 하위주문") int unclosedCount,
            @Schema(description = "미처리 반품(건) — 「미처리 반품 N건이 남아 있습니다」") long openReturnCount,
            List<Stage> unclosedStages
    ) {
    }

    @Schema(name = "SellerPerformanceStage")
    public record Stage(String stage, String label, int count) {
    }

    @Schema(name = "SellerPerformanceContractBar")
    public record ContractBar(
            Long contractId,
            @Schema(nullable = true) Long fixedFeeAmount,
            @Schema(nullable = true) String fixedFeeTriggerLabel,
            @Schema(description = "「고정 지급비 400,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급」", nullable = true) String fixedFeeText,
            @Schema(description = "「10%」 · 상품마다 다르면 「상품별」", nullable = true) String rewardRateLabel,
            @Schema(description = "리워드 — 합의 반영이면 original 에 원래 값") PerformanceFigure<Long> reward,
            @Schema(description = "총 집행비 = 고정 지급비 + 리워드") PerformanceFigure<Long> totalSpend,
            int periodDays
    ) {
    }

    @Schema(name = "SellerPerformanceKpi")
    public record Kpi(
            PerformanceFigure<Long> confirmedSales,
            PerformanceFigure<Long> payout,
            PerformanceFigure<BigDecimal> conversionRate,
            PerformanceFigure<BigDecimal> confirmRate,
            PerformanceFigure<Long> confirmedQuantity,
            @Schema(description = "주문 수량(보조줄)") long orderedQuantity,
            PerformanceFigure<Long> aov,
            PerformanceFigure<Long> roas,
            PerformanceFigure<BigDecimal> repurchaseRate
    ) {
    }

    @Schema(name = "SellerPerformanceTraffic")
    public record Traffic(
            @Schema(description = "쇼룸 방문(명)") long visitorCount,
            @Schema(description = "방문(세션)") long visitCount,
            @Schema(description = "유입 경로 4종 — 사람당 첫 방문 소스 · 합 = visitorCount") List<Source> sources,
            @Schema(nullable = true) Post post,
            Funnel funnel,
            @Schema(description = "커버리지 한계 고지") List<String> limits
    ) {
    }

    @Schema(name = "SellerPerformanceSource")
    public record Source(String source, String label, long count, @Schema(nullable = true) BigDecimal ratio) {
    }

    @Schema(name = "SellerPerformancePost")
    public record Post(
            Long postId,
            @Schema(description = "게시물 조회(회)") long impressionCount,
            @Schema(description = "고유 방문(명)") long uniqueViewerCount,
            @Schema(description = "상품 상세 이동(회) · basis 에 조회 대비 %") PerformanceFigure<Long> productViewCount,
            @Schema(description = "게시물 → 주문(건) · basis 에 상세 대비 %") PerformanceFigure<Long> orderCount,
            long likeCount,
            @Schema(description = "공유 — 원천 로그 없음(NOT_AVAILABLE)") PerformanceFigure<Long> shareCount
    ) {
    }

    @Schema(name = "SellerPerformanceFunnel")
    public record Funnel(
            @Schema(description = "구간 1 — 막대 기준 = 쇼룸 방문") long visitors,
            long orders,
            @Schema(description = "「방문 N명은 주문하지 않았습니다」") long notOrdered,
            @Schema(nullable = true) BigDecimal orderRate,
            @Schema(description = "구간 2 — 막대 기준 = 주문 · 누적 도달") long shipped,
            long delivered,
            PerformanceFigure<Long> confirmed,
            @Schema(nullable = true) BigDecimal shippedRate,
            @Schema(nullable = true) BigDecimal deliveredRate,
            @Schema(nullable = true) BigDecimal confirmedRate,
            @Schema(description = "검산 — 주문 = 구매확정 + 취소 + 반품 + 배송 예외") int cancelled,
            int returned,
            int deliveryException,
            @Schema(description = "방문 대비 최종 구매전환율") PerformanceFigure<BigDecimal> conversionRate
    ) {
    }

    @Schema(name = "SellerPerformanceSales")
    public record Sales(List<ProductRow> products, ProductRow total, Highlights highlights) {
    }

    @Schema(name = "SellerPerformanceProductRow", description = "total 행은 contractItemId · productId · rewardRate 가 null")
    public record ProductRow(
            @Schema(nullable = true) Long contractItemId,
            @Schema(nullable = true) Long productId,
            String productName,
            @Schema(nullable = true) BigDecimal rewardRate,
            @Schema(description = "상세 조회(회)") long detailViews,
            @Schema(description = "조회자(명) — 합계는 중복 제거") long viewers,
            int orderCount,
            @Schema(description = "전환율(%) = 주문 ÷ 고유 조회자") PerformanceFigure<BigDecimal> conversionRate,
            PerformanceFigure<Long> confirmedQuantity,
            PerformanceFigure<Long> confirmedSales,
            @Schema(description = "반품률(%) = 반품 수량 ÷ 확정 수량") PerformanceFigure<BigDecimal> returnRate
    ) {
    }

    @Schema(name = "SellerPerformanceHighlights", description = "값이 없는 카드는 null")
    public record Highlights(@Schema(nullable = true) Highlight mostViewed,
                             @Schema(nullable = true) Highlight bestConversion,
                             @Schema(nullable = true) Highlight highestReturn) {
    }

    @Schema(name = "SellerPerformanceHighlight")
    public record Highlight(String productName, @Schema(example = "확정 54개 중 4개 · 7.4%") String basis) {
    }

    @Schema(name = "SellerPerformanceDelivery")
    public record Delivery(
            @Schema(description = "발송기한 준수율(%)") PerformanceFigure<BigDecimal> shipOnTimeRate,
            @Schema(description = "평균 발송 소요(일)") PerformanceFigure<BigDecimal> avgShipDays,
            @Schema(description = "배송 클레임률(%) — 반송 · 분실") PerformanceFigure<BigDecimal> claimRate,
            @Schema(description = "문의 응답 평균(시간) — 상품 문의") PerformanceFigure<BigDecimal> avgInquiryHours
    ) {
    }

    @Schema(name = "SellerPerformanceBreakdown")
    public record Breakdown(
            @Schema(description = "총 건수(취소 + 반품 + 배송 예외)") int totalCount,
            @Schema(description = "거래액 차감 합") PerformanceFigure<Long> deductionTotal,
            BreakdownGroup cancel,
            BreakdownGroup returned,
            BreakdownGroup deliveryException,
            @Schema(description = "미처리 반품(건) — 집계 중일 때만", nullable = true) Long pendingReturnCount
    ) {
    }

    @Schema(name = "SellerPerformanceBreakdownGroup")
    public record BreakdownGroup(int count, long amount, List<BreakdownLine> lines) {
    }

    @Schema(name = "SellerPerformanceBreakdownLine", description = "개수(항목 수량) · 금액(차감 몫) · 건수(그 분류로 접힌 하위주문)")
    public record BreakdownLine(String key, String label, long quantity, long amount, int count) {
    }

    @Schema(name = "SellerPerformanceConsumers")
    public record Consumers(
            @Schema(description = "판매 · 소비자 블록이 예정값인가(집계 중)") boolean estimated,
            @Schema(description = "구매확정(건)") int confirmedOrderCount,
            @Schema(description = "구매확정 소비자(명 · 중복 제거)") int confirmedBuyerCount,
            PerformanceFigure<Long> newBuyerCount,
            @Schema(description = "재구매 소비자 — 이 브랜드의 이전 공구 구매확정") PerformanceFigure<Long> repurchaseBuyerCount,
            @Schema(description = "쇼룸 팔로워 구매 비중(%) — 결제 시각 팔로워") PerformanceFigure<BigDecimal> followerShare,
            @Schema(description = "신규 고객 획득 단가 = 총 집행비 ÷ 신규 소비자") PerformanceFigure<Long> acquisitionCost,
            Gender gender,
            @Schema(description = "연령대 × 성별 — 구간 5명 미만은 「기타」 합산(merged)") List<AgeBand> ageBands
    ) {
    }

    @Schema(name = "SellerPerformanceGender")
    public record Gender(long female, long male, long unknown,
                         @Schema(nullable = true) BigDecimal femaleRatio, @Schema(nullable = true) BigDecimal maleRatio) {
    }

    @Schema(name = "SellerPerformanceAgeBand")
    public record AgeBand(@Schema(example = "TWENTIES · OTHER · UNKNOWN") String band, String label, long female,
                          long male, long total, @Schema(nullable = true) BigDecimal ratio,
                          @Schema(description = "「기타」로 합산된 행") boolean merged) {
    }

    @Schema(name = "SellerPerformanceCost", description = "브랜드 전용 — 인플루언서 · 운영자 응답에는 없다")
    public record Cost(
            @Schema(description = "저장 가능 — 집계 중이면 false") boolean editable,
            @Schema(description = "AGGREGATING — 잠긴 이유", nullable = true) String lockedReason,
            List<CostItem> items,
            @Schema(nullable = true) Long packagingCost,
            @Schema(nullable = true) Long shippingCost,
            @Schema(nullable = true) Long returnDisposalCost,
            @Schema(nullable = true) Long etcCost,
            @Schema(description = "개당 원가가 하나라도 비면 null(D1-C)") PerformanceFigure<Long> totalCost,
            @Schema(description = "원가 차감 후 수익 = 정산액 − 총 원가 − 고정 지급비") PerformanceFigure<Long> profit,
            @Schema(description = "수익률(%) = 수익 ÷ 확정 거래액") PerformanceFigure<BigDecimal> profitRate,
            @Schema(nullable = true) LocalDateTime updatedAt
    ) {
    }

    @Schema(name = "SellerPerformanceCostItem")
    public record CostItem(Long contractItemId, String productName, PerformanceFigure<Long> confirmedQuantity,
                           @Schema(nullable = true) Long unitCost, @Schema(nullable = true) Long subtotal) {
    }

    @Schema(name = "SellerPerformanceSettlementLink", description = "정산 관리의 값 그대로 — 이 화면은 계산하지 않는다")
    public record SettlementLink(
            Long settlementId,
            PerformanceStatus status,
            String statusLabel,
            PerformanceTone statusTone,
            @Schema(description = "파트너 13 분해 그대로") SellerSettlementDto.Breakdown breakdown,
            @Schema(description = "정산액 — 조정 협의 중이면 NEGOTIATING + original") PerformanceFigure<Long> payout,
            LocalDateTime reviewDueAt,
            @Schema(nullable = true) LocalDate payoutDueDate,
            @Schema(nullable = true) LocalDateTime paidAt,
            @Schema(description = "조정 협의 · 합의 이력 — 응답 버튼은 없다(이슈 스레드에서만)", nullable = true)
            SettlementPartyDto.AdjustmentBlock adjustment
    ) {
    }

    // ------------------------------------------------------------------ 원가 저장(4-1)

    @Schema(name = "SellerPerformanceCostRequest", description = "전체 교체 — 계약 상품 전부 · 미입력은 null · 0 이상 10억 이하")
    public record CostRequest(
            List<CostItemRequest> items,
            @Schema(nullable = true) Long packagingCost,
            @Schema(nullable = true) Long shippingCost,
            @Schema(nullable = true) Long returnDisposalCost,
            @Schema(nullable = true) Long etcCost
    ) {
    }

    @Schema(name = "SellerPerformanceCostItemRequest")
    public record CostItemRequest(Long contractItemId, @Schema(nullable = true, example = "7200") Long unitCost) {
    }
}

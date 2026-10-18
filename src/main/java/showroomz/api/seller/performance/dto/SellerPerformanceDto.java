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

    @Schema(name = "SellerPerformanceListPage", description = "성과 목록 페이지")
    public record ListPage(
            @Schema(description = "목록 행") List<ListItem> content,
            @Schema(description = "페이지 정보") PaginationInfo pageInfo,
            @Schema(description = "조회기간 표기 — 필터를 통과한 공구들의 MIN(시작) ~ MAX(종료) · 결과가 없으면 null", nullable = true)
            ListPeriod listPeriod) {
    }

    @Schema(name = "SellerPerformanceListPeriod", description = "「조회기간 2026.07.18~08.07」 — 목록에 기간 필터는 없다")
    public record ListPeriod(
            @Schema(description = "가장 이른 공구 시작일", example = "2026-07-18") LocalDate from,
            @Schema(description = "가장 늦은 공구 종료일", example = "2026-08-07") LocalDate to) {
    }

    @Schema(name = "SellerPerformanceListItem", description = "집계 중 행은 여섯 지표 전부 value = null(「—」) · 조정 협의 행은 payout 만 NEGOTIATING")
    public record ListItem(
            @Schema(description = "공구 ID — 상세 경로 키", example = "55") Long groupBuyId,
            @Schema(description = "공구명(계약 제목)", example = "수분 앰플 공구") String groupBuyTitle,
            @Schema(description = "인플루언서") Influencer influencer,
            @Schema(description = "공구 시작", example = "2026-07-18T10:00:00") LocalDateTime periodStart,
            @Schema(description = "공구 종료 — 조기 마감이면 실제 종료 시각", example = "2026-07-25T23:59:00") LocalDateTime periodEnd,
            @Schema(description = "성과 상태 5종", example = "PAID") PerformanceStatus status,
            @Schema(description = "상태 라벨", example = "지급 완료") String statusLabel,
            @Schema(description = "배지 톤 — NEUTRAL · WARNING · SUCCESS", example = "SUCCESS") PerformanceTone statusTone,
            @Schema(description = "쇼룸 방문(명) — 공구 기간 · 고유 방문자") PerformanceFigure<Long> visitors,
            @Schema(description = "전환율(%) = 구매확정(건) ÷ 쇼룸 방문(명)") PerformanceFigure<BigDecimal> conversionRate,
            @Schema(description = "확정 거래액(원)") PerformanceFigure<Long> confirmedSales,
            @Schema(description = "정산액(원) — 브랜드 실지급 · 조정 협의 중이면 NEGOTIATING + original") PerformanceFigure<Long> payout,
            @Schema(description = "확정 수량(개)") PerformanceFigure<Long> confirmedQuantity,
            @Schema(description = "구매확정률(%) = 구매확정 ÷ 주문") PerformanceFigure<BigDecimal> confirmRate,
            @Schema(description = "상세 열람 가능 — 집계 중 행도 상세(D2)가 열려 항상 true", example = "true") boolean detailAvailable
    ) {
    }

    @Schema(name = "SellerPerformanceInfluencer", description = "공구 인플루언서")
    public record Influencer(
            @Schema(description = "인플루언서 ID", example = "12") Long creatorId,
            @Schema(description = "쇼룸명", example = "데일리뷰티랩") String showroomName) {
    }

    // ------------------------------------------------------------------ 요약(3-3)

    @Schema(name = "SellerPerformanceSummary", description = "모집단 = 지급 완료 공구 합산 · 검색어 · 상태 칩과 무관 · 평균은 합산 비율(Σ분자 ÷ Σ분모). 지급 완료 0건이면 지표 value 는 전부 null")
    public record Summary(
            @Schema(description = "KPI 모집단 — 「지급 완료 N건 합산」") Population population,
            @Schema(description = "확정 거래액 합(원)") PerformanceFigure<Long> confirmedSales,
            @Schema(description = "쇼룸 방문 누적(명) — 공구별 고유 방문의 합") PerformanceFigure<Long> visitors,
            @Schema(description = "평균 객단가(원) = Σ확정 거래액 ÷ Σ확정 주문") PerformanceFigure<Long> avgAov,
            @Schema(description = "평균 전환율(%) = Σ구매확정 ÷ Σ쇼룸 방문") PerformanceFigure<BigDecimal> avgConversionRate,
            @Schema(description = "평균 ROAS(% 정수) = Σ확정 거래액 ÷ Σ총 집행비 — 총 집행비 0원 공구는 분자 · 분모 모두 제외") PerformanceFigure<Long> avgRoas,
            @Schema(description = "성비") GenderRatio genderRatio,
            @Schema(description = "평균 구매확정률(%) = Σ구매확정 ÷ Σ주문") PerformanceFigure<BigDecimal> avgConfirmRate,
            @Schema(description = "평균 재구매율(%) = Σ재구매 소비자 ÷ Σ구매확정 소비자") PerformanceFigure<BigDecimal> avgRepurchaseRate,
            @Schema(description = "상태 칩 숫자 — 모집단 전체 · 다섯 키가 항상 있다(0 포함)",
                    example = "{\"AGGREGATING\": 3, \"REVIEWING\": 0, \"ADJUSTING\": 1, \"PAYOUT_SCHEDULED\": 0, \"PAID\": 2}")
            Map<PerformanceStatus, Long> statusCounts
    ) {
    }

    @Schema(name = "SellerPerformancePopulation", description = "KPI 모집단")
    public record Population(
            @Schema(description = "모집단 상태 — 항상 PAID", example = "PAID") PerformanceStatus status,
            @Schema(description = "지급 완료 공구 수 — 0 이면 빈 상태(L2)", example = "2") long count) {
    }

    @Schema(name = "SellerPerformanceGenderRatio", description = "성비(정수 %) — 미확인은 분모에서 뺀다 · 아는 성별이 없으면 female · male 이 null · 큰 쪽을 앞에 두는 것은 FE")
    public record GenderRatio(
            @Schema(description = "여성 %", example = "80", nullable = true) Integer female,
            @Schema(description = "남성 %", example = "20", nullable = true) Integer male,
            @Schema(description = "보조줄", example = "지급 완료 2건 합산", nullable = true) String basis) {
    }

    // ------------------------------------------------------------------ 상세(3-4)

    @Schema(name = "SellerPerformanceDetail", description = "공구 성과 상세 — 블록 순서 = 계약 바 → KPI → 유입 → 판매 → 배송 → 소비자 → 원가 → 정산 연결")
    public record Detail(
            @Schema(description = "공구 ID", example = "55") Long groupBuyId,
            @Schema(description = "공구명(계약 제목)", example = "수분 앰플 공구") String groupBuyTitle,
            @Schema(description = "인플루언서") Influencer influencer,
            @Schema(description = "[이 인플루언서와 재협업 제안] — 연결·소통 화면 이동용 연결 ID", example = "40", nullable = true) Long connectionId,
            @Schema(description = "공구 시작 — 유입 · 게시물 · 좋아요 · 문의 집계 창의 시작", example = "2026-07-18T10:00:00") LocalDateTime periodStart,
            @Schema(description = "공구 종료 — 조기 마감이면 실제 종료 시각", example = "2026-07-25T23:59:00") LocalDateTime periodEnd,
            @Schema(description = "성과 상태 5종", example = "PAID") PerformanceStatus status,
            @Schema(description = "상태 라벨", example = "지급 완료") String statusLabel,
            @Schema(description = "배지 톤", example = "SUCCESS") PerformanceTone statusTone,
            @Schema(description = "D2 배너 — 집계 중일 때만", nullable = true) Aggregating aggregating,
            @Schema(description = "계약 조건 바") ContractBar contract,
            @Schema(description = "KPI 8") Kpi kpi,
            @Schema(description = "① 유입 · 전환 퍼널") Traffic traffic,
            @Schema(description = "② 상품별 판매") Sales sales,
            @Schema(description = "③ 배송 운영 품질") Delivery delivery,
            @Schema(description = "③ 취소 · 반품 · 배송 예외 분해") Breakdown breakdown,
            @Schema(description = "④ 소비자") Consumers consumers,
            @Schema(description = "⑤ 원가 · 수익 — 브랜드 전용") Cost cost,
            @Schema(description = "⑥ 정산 연결 — 집계 중이면 null", nullable = true) SettlementLink settlement
    ) {
    }

    @Schema(name = "SellerPerformanceAggregating", description = "D2 배너 — 「미처리 반품 N건이 남아 있습니다」. 미종결 0 이면 배너 없이 「예정」만")
    public record Aggregating(
            @Schema(description = "종결되지 않은 하위주문(건)", example = "4") int unclosedCount,
            @Schema(description = "미처리 반품(건) — 결과가 나지 않은 반품 클레임 수", example = "3") long openReturnCount,
            @Schema(description = "미종결 하위주문의 이행 단계별 건수") List<Stage> unclosedStages
    ) {
    }

    @Schema(name = "SellerPerformanceStage", description = "미종결 단계 한 줄")
    public record Stage(
            @Schema(description = "하위주문 이행 상태 키", example = "RETURNING") String stage,
            @Schema(description = "단계 라벨", example = "반송중") String label,
            @Schema(description = "건수", example = "3") int count) {
    }

    @Schema(name = "SellerPerformanceContractBar", description = "계약 조건 바")
    public record ContractBar(
            @Schema(description = "계약 ID — [계약서 보기]", example = "70") Long contractId,
            @Schema(description = "고정 지급비(원) — 없는 계약은 null", example = "400000", nullable = true) Long fixedFeeAmount,
            @Schema(description = "고정 지급비 지급 시점 라벨", example = "공구 게시물 등록 후", nullable = true) String fixedFeeTriggerLabel,
            @Schema(description = "고정 지급비 문장", example = "고정 지급비 400,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급", nullable = true) String fixedFeeText,
            @Schema(description = "리워드율 표기 — 상품마다 같으면 「10%」 · 다르면 「상품별」", example = "10%", nullable = true) String rewardRateLabel,
            @Schema(description = "리워드(원) — 집계 중 PROJECTED(상품별 리워드율 × 확정 수량) · 조정 협의 NEGOTIATING · 합의 반영이면 original 에 원래 값") PerformanceFigure<Long> reward,
            @Schema(description = "총 집행비(원) = 고정 지급비 + 리워드 · 리워드 부가세 제외") PerformanceFigure<Long> totalSpend,
            @Schema(description = "공구 기간(일 · 시작일 · 종료일 포함)", example = "8") int periodDays
    ) {
    }

    @Schema(name = "SellerPerformanceKpi", description = "KPI 8 — 각 basis 에 분모 문장")
    public record Kpi(
            @Schema(description = "확정 거래액(원) — basis 「구매확정 N건 기준」") PerformanceFigure<Long> confirmedSales,
            @Schema(description = "정산액(원) — 브랜드 실지급 · 정산 관리 값 그대로") PerformanceFigure<Long> payout,
            @Schema(description = "전환율(%) = 구매확정(건) ÷ 쇼룸 방문(명)") PerformanceFigure<BigDecimal> conversionRate,
            @Schema(description = "구매확정률(%) = 구매확정 ÷ 주문") PerformanceFigure<BigDecimal> confirmRate,
            @Schema(description = "확정 수량(개) — basis 「주문 수량 N개」") PerformanceFigure<Long> confirmedQuantity,
            @Schema(description = "주문 수량(개 · 보조줄)", example = "326") long orderedQuantity,
            @Schema(description = "객단가(원) = 확정 거래액 ÷ 구매확정 하위주문") PerformanceFigure<Long> aov,
            @Schema(description = "ROAS(% 정수) = 확정 거래액 ÷ 총 집행비 — 총 집행비 0원이면 null") PerformanceFigure<Long> roas,
            @Schema(description = "재구매율(%) = 이 브랜드 이전 공구 구매자 ÷ 구매확정 소비자") PerformanceFigure<BigDecimal> repurchaseRate
    ) {
    }

    @Schema(name = "SellerPerformanceTraffic", description = "① 유입 — 공구 기간 · 그 인플루언서 쇼룸 · 언제나 FINAL(줄지 않는 값)")
    public record Traffic(
            @Schema(description = "쇼룸 방문(명) — 고유 방문자", example = "5010") long visitorCount,
            @Schema(description = "방문(세션) — 30분 세션 기준", example = "6120") long visitCount,
            @Schema(description = "유입 경로 4종 — 사람당 첫 방문 소스 · 항상 4행 · 합 = visitorCount") List<Source> sources,
            @Schema(description = "공구 게시물 — 게시물이 없으면 null", nullable = true) Post post,
            @Schema(description = "전환 퍼널") Funnel funnel,
            @Schema(description = "커버리지 한계 고지 문장(서버 문장 그대로 · 순서대로 표시)") List<String> limits
    ) {
    }

    @Schema(name = "SellerPerformanceSource", description = "유입 경로 한 줄")
    public record Source(
            @Schema(description = "경로 키 — INTERNAL(쇼룸 내부 탐색) · PROFILE_LINK(프로필 쇼룸 링크) · POST_LINK(공구 게시물 링크) · PRODUCT_LINK(상품 상세 직접 링크)",
                    example = "INTERNAL") String source,
            @Schema(description = "경로 라벨", example = "쇼룸 내부 탐색") String label,
            @Schema(description = "방문자(명)", example = "1880") long count,
            @Schema(description = "비율(%) = count ÷ 쇼룸 방문 — 방문 0 이면 null", example = "37.5", nullable = true) BigDecimal ratio) {
    }

    @Schema(name = "SellerPerformancePost", description = "공구 게시물 지표 — 공구당 1건 · 공구 기간")
    public record Post(
            @Schema(description = "게시물 ID", example = "301") Long postId,
            @Schema(description = "게시물 조회(회)", example = "4290") long impressionCount,
            @Schema(description = "고유 방문(명)", example = "3140") long uniqueViewerCount,
            @Schema(description = "상품 상세 이동(회) · basis 에 조회 대비 %") PerformanceFigure<Long> productViewCount,
            @Schema(description = "게시물 → 주문(건) — 결제 시점 라스트 터치(24시간)가 이 게시물인 하위주문 · basis 에 상세 대비 %") PerformanceFigure<Long> orderCount,
            @Schema(description = "좋아요(기간)", example = "312") long likeCount,
            @Schema(description = "공유 — 원천 로그 없음(항상 NOT_AVAILABLE · 「집계 준비 중」)") PerformanceFigure<Long> shareCount
    ) {
    }

    @Schema(name = "SellerPerformanceFunnel", description = "전환 퍼널 — 구간 1 막대 기준 = 쇼룸 방문 · 구간 2 막대 기준 = 주문(누적 도달)")
    public record Funnel(
            @Schema(description = "구간 1 — 쇼룸 방문(명)", example = "5010") long visitors,
            @Schema(description = "주문(건) — 결제 완료 하위주문", example = "313") long orders,
            @Schema(description = "「방문 N명은 주문하지 않았습니다」 = 방문 − 주문 · 음수면 0", example = "4697") long notOrdered,
            @Schema(description = "주문률(%) = 주문 ÷ 방문", example = "6.2", nullable = true) BigDecimal orderRate,
            @Schema(description = "구간 2 — 발송(건) · 반품된 건도 센다(누적 도달)", example = "305") long shipped,
            @Schema(description = "배송완료(건) · 누적 도달", example = "303") long delivered,
            @Schema(description = "구매확정(건) — 집계 중이면 PROJECTED") PerformanceFigure<Long> confirmed,
            @Schema(description = "발송률(%) = 발송 ÷ 주문", example = "97.4", nullable = true) BigDecimal shippedRate,
            @Schema(description = "배송완료율(%) = 배송완료 ÷ 주문", example = "96.8", nullable = true) BigDecimal deliveredRate,
            @Schema(description = "구매확정률(%) = 구매확정 ÷ 주문", example = "93.0", nullable = true) BigDecimal confirmedRate,
            @Schema(description = "검산 — 취소(건) · 주문 = 구매확정 + 취소 + 반품 + 배송 예외", example = "8") int cancelled,
            @Schema(description = "검산 — 반품(건) · 전량 반품 하위주문", example = "13") int returned,
            @Schema(description = "검산 — 배송 예외(건) · 반송 · 분실 등", example = "1") int deliveryException,
            @Schema(description = "방문 대비 최종 구매전환율(%)") PerformanceFigure<BigDecimal> conversionRate
    ) {
    }

    @Schema(name = "SellerPerformanceSales", description = "② 상품별 판매 — 행 = 계약 상품 · 계약 순서")
    public record Sales(
            @Schema(description = "상품별 행") List<ProductRow> products,
            @Schema(description = "합계 행 — 조회자는 상품 간 중복 제거 · returnRate.basis 에 공구 전체 반품률(반품 건 ÷ 주문 건)") ProductRow total,
            @Schema(description = "하이라이트 3장") Highlights highlights) {
    }

    @Schema(name = "SellerPerformanceProductRow", description = "total 행은 contractItemId · productId · rewardRate 가 null")
    public record ProductRow(
            @Schema(description = "계약 상품 ID — 원가 입력 키", example = "81", nullable = true) Long contractItemId,
            @Schema(description = "상품 ID", example = "501", nullable = true) Long productId,
            @Schema(description = "상품명 — 합계 행은 「합계」", example = "수분 앰플 30ml") String productName,
            @Schema(description = "리워드율(%)", example = "10.0", nullable = true) BigDecimal rewardRate,
            @Schema(description = "상세 조회(회)", example = "2140") long detailViews,
            @Schema(description = "고유 조회자(명) — 합계는 중복 제거", example = "1680") long viewers,
            @Schema(description = "주문(건) — 이 상품이 든 결제 완료 하위주문", example = "150") int orderCount,
            @Schema(description = "상품 전환율(%) = 주문 ÷ 고유 조회자") PerformanceFigure<BigDecimal> conversionRate,
            @Schema(description = "확정 수량(개)") PerformanceFigure<Long> confirmedQuantity,
            @Schema(description = "확정 거래액(원)") PerformanceFigure<Long> confirmedSales,
            @Schema(description = "반품률(%) = 반품 수량 ÷ 확정 수량 — 공구 전체 반품률(건 기준)과 분모가 다르다") PerformanceFigure<BigDecimal> returnRate
    ) {
    }

    @Schema(name = "SellerPerformanceHighlights", description = "하이라이트 3장 — 값이 없는 카드는 null · 평가어 없음")
    public record Highlights(
            @Schema(description = "가장 많이 본 상품 — 상세 조회 최대", nullable = true) Highlight mostViewed,
            @Schema(description = "전환율이 높은 상품 — 주문 ÷ 고유 조회자 최대", nullable = true) Highlight bestConversion,
            @Schema(description = "반품률이 높은 상품 — 반품 수량 ÷ 확정 수량 최대", nullable = true) Highlight highestReturn) {
    }

    @Schema(name = "SellerPerformanceHighlight", description = "하이라이트 카드")
    public record Highlight(
            @Schema(description = "상품명", example = "수분 크림 50ml") String productName,
            @Schema(description = "근거 문장(서버 문장 그대로)", example = "확정 54개 중 4개 · 7.4%") String basis) {
    }

    @Schema(name = "SellerPerformanceDelivery", description = "③ 배송 운영 품질 — 브랜드 전용")
    public record Delivery(
            @Schema(description = "발송기한 준수율(%) = 기한 안 발송 ÷ 발송 · basis 「발송 N건 중」") PerformanceFigure<BigDecimal> shipOnTimeRate,
            @Schema(description = "평균 발송 소요(일 · 소수 1자리) — 결제완료 → 송장 등록 · 발송 0 이면 null") PerformanceFigure<BigDecimal> avgShipDays,
            @Schema(description = "배송 클레임률(%) = 반송 · 분실 건 ÷ 주문") PerformanceFigure<BigDecimal> claimRate,
            @Schema(description = "문의 응답 평균(시간 · 소수 1자리) — 계약 상품의 상품 문의 · 기간 + 7일 · 미답변 제외") PerformanceFigure<BigDecimal> avgInquiryHours
    ) {
    }

    @Schema(name = "SellerPerformanceBreakdown", description = "③ 취소 · 반품 · 배송 예외 분해 — 건수는 하위주문 · 개수 · 금액은 항목")
    public record Breakdown(
            @Schema(description = "총 건수(취소 + 반품 + 배송 예외)", example = "22") int totalCount,
            @Schema(description = "「거래액 차감 −N원 반영」 합(원)") PerformanceFigure<Long> deductionTotal,
            @Schema(description = "취소 — 줄 키 AFTER_PAYMENT(결제 직후) · PREPARING(상품준비중) · SELLER_DIRECT(직권 취소) · OTHER(기타 · 있을 때만)") BreakdownGroup cancel,
            @Schema(description = "반품 — 줄 키 = 반품 사유(CHANGE_OF_MIND · ORDER_MISTAKE · SIZE_MISMATCH · DAMAGED_OR_DEFECTIVE · WRONG_OR_LATE_DELIVERY · OTHER). 부분 반품은 개수 · 금액에만 넣고 건수에는 넣지 않는다") BreakdownGroup returned,
            @Schema(description = "배송 예외 — 줄 키 RETURNED_TO_SENDER(반송) · LOST(분실) · OPERATOR_REFUND(운영자 사유 환불 · 있을 때만)") BreakdownGroup deliveryException,
            @Schema(description = "미처리 반품(건) — 집계 중일 때만", example = "3", nullable = true) Long pendingReturnCount
    ) {
    }

    @Schema(name = "SellerPerformanceBreakdownGroup", description = "분해 묶음 하나")
    public record BreakdownGroup(
            @Schema(description = "건수(하위주문)", example = "13") int count,
            @Schema(description = "금액 합(원)", example = "260000") long amount,
            @Schema(description = "세부 줄") List<BreakdownLine> lines) {
    }

    @Schema(name = "SellerPerformanceBreakdownLine", description = "개수(항목 수량) · 금액(차감 몫) · 건수(그 분류로 접힌 하위주문)")
    public record BreakdownLine(
            @Schema(description = "줄 키", example = "CHANGE_OF_MIND") String key,
            @Schema(description = "줄 라벨", example = "단순 변심") String label,
            @Schema(description = "개수(항목 수량)", example = "9") long quantity,
            @Schema(description = "금액(원)", example = "180000") long amount,
            @Schema(description = "건수(하위주문)", example = "8") int count) {
    }

    @Schema(name = "SellerPerformanceConsumers", description = "④ 소비자 — 구매확정 하위주문의 소비자 기준 · 건과 명을 분리한다")
    public record Consumers(
            @Schema(description = "판매 · 소비자 블록이 예정값인가(집계 중)", example = "false") boolean estimated,
            @Schema(description = "구매확정(건)", example = "291") int confirmedOrderCount,
            @Schema(description = "구매확정 소비자(명 · 중복 제거)", example = "291") int confirmedBuyerCount,
            @Schema(description = "신규 소비자(명) = 구매확정 소비자 − 재구매") PerformanceFigure<Long> newBuyerCount,
            @Schema(description = "재구매 소비자(명) — 이 공구 시작 전 이 브랜드의 다른 공구에서 구매확정한 소비자") PerformanceFigure<Long> repurchaseBuyerCount,
            @Schema(description = "쇼룸 팔로워 구매 비중(%) — 결제 시각 팔로워 · 언제나 FINAL") PerformanceFigure<BigDecimal> followerShare,
            @Schema(description = "신규 고객 획득 단가(원) = 총 집행비 ÷ 신규 소비자 — 신규 0 이면 null") PerformanceFigure<Long> acquisitionCost,
            @Schema(description = "성별 인원 · 비율") Gender gender,
            @Schema(description = "연령대 × 성별 — 10대 · 20대 · 30대 · 40대 · 50대+ 순 · 구간 5명 미만은 「기타」 합산(merged) · 미확인은 마지막 별도 행 · 0명 구간은 없다") List<AgeBand> ageBands
    ) {
    }

    @Schema(name = "SellerPerformanceGender", description = "구매확정 소비자 성별 — 비율은 미확인을 뺀 분모")
    public record Gender(
            @Schema(description = "여성(명)", example = "239") long female,
            @Schema(description = "남성(명)", example = "52") long male,
            @Schema(description = "미확인(명)", example = "0") long unknown,
            @Schema(description = "여성(%)", example = "82.1", nullable = true) BigDecimal femaleRatio,
            @Schema(description = "남성(%)", example = "17.9", nullable = true) BigDecimal maleRatio) {
    }

    @Schema(name = "SellerPerformanceAgeBand", description = "연령대 한 줄 — 나이는 공구 시작일 기준 만 나이 · 비율 분모 = 구매확정 소비자 전체")
    public record AgeBand(
            @Schema(description = "구간 키 — TEENS · TWENTIES · THIRTIES · FORTIES · FIFTIES_PLUS · OTHER(기타) · UNKNOWN(미확인)", example = "TWENTIES") String band,
            @Schema(description = "구간 라벨", example = "20대") String label,
            @Schema(description = "여성(명)", example = "104") long female,
            @Schema(description = "남성(명)", example = "15") long male,
            @Schema(description = "구간 인원(명) — 미확인 행은 성별 미확인 소비자도 포함", example = "119") long total,
            @Schema(description = "구간 비율(%)", example = "40.9", nullable = true) BigDecimal ratio,
            @Schema(description = "「기타」로 합산된 행", example = "false") boolean merged) {
    }

    @Schema(name = "SellerPerformanceCost", description = "⑤ 원가 · 수익 — 브랜드 전용(인플루언서 · 운영자 응답에는 없다). D1-C = 개당 원가 미입력 · D1-D = 입력 완료")
    public record Cost(
            @Schema(description = "저장 가능 — 집계 중이면 false", example = "true") boolean editable,
            @Schema(description = "잠긴 이유 — 집계 중이면 「AGGREGATING」 · 아니면 null", example = "AGGREGATING", nullable = true) String lockedReason,
            @Schema(description = "계약 상품별 원가 — 계약 순서") List<CostItem> items,
            @Schema(description = "부자재비(원) — 미입력 null", example = "219200", nullable = true) Long packagingCost,
            @Schema(description = "배송비(원 · 실지출) — 미입력 null", example = "918000", nullable = true) Long shippingCost,
            @Schema(description = "반품 폐기 손실(원) — 미입력 null", example = "92000", nullable = true) Long returnDisposalCost,
            @Schema(description = "기타(원) — 미입력 null", example = "0", nullable = true) Long etcCost,
            @Schema(description = "총 원가(원) = Σ상품 원가 소계 + 4종(null 은 0) — 개당 원가가 하나라도 비면 null(D1-C) · 집계 중 PENDING_SETTLEMENT") PerformanceFigure<Long> totalCost,
            @Schema(description = "원가 차감 후 수익(원) = 정산액 − 총 원가 − 고정 지급비 — 조정 협의 중 NEGOTIATING(original = 현재 정산액 기준) · 합의 반영이면 original 에 원래 정산액 기준") PerformanceFigure<Long> profit,
            @Schema(description = "수익률(%) = 수익 ÷ 확정 거래액") PerformanceFigure<BigDecimal> profitRate,
            @Schema(description = "마지막 저장 시각 — 저장한 적 없으면 null", example = "2026-08-06T14:20:00", nullable = true) LocalDateTime updatedAt
    ) {
    }

    @Schema(name = "SellerPerformanceCostItem", description = "계약 상품 원가 한 줄")
    public record CostItem(
            @Schema(description = "계약 상품 ID — 저장 요청의 키", example = "81") Long contractItemId,
            @Schema(description = "상품명", example = "수분 앰플 30ml") String productName,
            @Schema(description = "확정 수량(개)") PerformanceFigure<Long> confirmedQuantity,
            @Schema(description = "개당 원가(원) — 미입력 null", example = "7200", nullable = true) Long unitCost,
            @Schema(description = "상품 원가 소계(원) = 개당 원가 × 확정 수량 — 미입력 · 집계 중이면 null", example = "1584000", nullable = true) Long subtotal) {
    }

    @Schema(name = "SellerPerformanceSettlementLink", description = "⑥ 정산 연결 — 정산 관리(13)의 값 그대로 · 이 화면은 계산하지 않는다")
    public record SettlementLink(
            @Schema(description = "정산 ID — [정산 상세 보기]", example = "26") Long settlementId,
            @Schema(description = "성과 상태(분배 실패는 PAID)", example = "PAID") PerformanceStatus status,
            @Schema(description = "상태 라벨", example = "지급 완료") String statusLabel,
            @Schema(description = "배지 톤", example = "SUCCESS") PerformanceTone statusTone,
            @Schema(description = "정산 분해 — 파트너 정산 관리(13) 상세의 분해 그대로 · 0원 행 숨김은 FE") SellerSettlementDto.Breakdown breakdown,
            @Schema(description = "정산액(원) — 조정 협의 중이면 NEGOTIATING + original") PerformanceFigure<Long> payout,
            @Schema(description = "정산 확인 기한 — 「확인 중 · {reviewDueAt}까지」", example = "2026-07-31T23:59:59") LocalDateTime reviewDueAt,
            @Schema(description = "지급 예정일", example = "2026-08-05", nullable = true) LocalDate payoutDueDate,
            @Schema(description = "지급 완료 시각", example = "2026-08-05T10:00:00", nullable = true) LocalDateTime paidAt,
            @Schema(description = "조정 협의 · 합의 이력 — 응답 버튼 · 권한은 없다(이슈 스레드에서만) · [이슈 스레드 열기] = threadId", nullable = true)
            SettlementPartyDto.AdjustmentBlock adjustment
    ) {
    }

    // ------------------------------------------------------------------ 원가 저장(4-1)

    @Schema(name = "SellerPerformanceCostRequest", description = "전체 교체 — 계약 상품 전부 · 미입력은 null · 금액은 0 이상 10억 이하 정수(원)")
    public record CostRequest(
            @Schema(description = "계약 상품 전부를 정확히 한 번씩 — 빠지거나 중복 · 다른 공구 항목이면 400 PERFORMANCE_COST_ITEM_MISMATCH") List<CostItemRequest> items,
            @Schema(description = "부자재비(원)", example = "219200", nullable = true) Long packagingCost,
            @Schema(description = "배송비(원 · 실지출)", example = "918000", nullable = true) Long shippingCost,
            @Schema(description = "반품 폐기 손실(원)", example = "92000", nullable = true) Long returnDisposalCost,
            @Schema(description = "기타(원)", example = "0", nullable = true) Long etcCost
    ) {
    }

    @Schema(name = "SellerPerformanceCostItemRequest", description = "계약 상품 개당 원가")
    public record CostItemRequest(
            @Schema(description = "계약 상품 ID — 상세 cost.items[].contractItemId", example = "81") Long contractItemId,
            @Schema(description = "개당 원가(원) — 미입력 null", nullable = true, example = "7200") Long unitCost) {
    }
}

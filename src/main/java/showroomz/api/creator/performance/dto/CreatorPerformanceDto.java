package showroomz.api.creator.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.api.creator.performance.type.CreatorPerformanceStatus;
import showroomz.api.creator.performance.type.RewardStage;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.performance.type.PerformanceTone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 쇼룸 스튜디오 성과 관리(10) 응답(49 스튜디오 성과 설계서 3절).
 *
 * <p>파트너 12 와 DTO 를 공유하지 않는다 — 공유하면 언젠가 파트너 전용 필드(정산액 · ROAS · 원가 · 배송 품질)가 붙고 그게 스튜디오로
 * 샌다(31 설계 0-5). 값이 없으면 {@code null}(「—」)이고 0 은 <b>측정된 0</b>이다(§47-2 빈 KPI).
 *
 * <p>Swagger 스키마 이름은 전부 {@code CreatorPerformance} 접두어를 단다 — 다른 DTO 의 {@code Detail} · {@code Summary} 와 이름이
 * 겹치면 문서에 엉뚱한 스키마가 붙는다.
 */
public final class CreatorPerformanceDto {

    private CreatorPerformanceDto() {
    }

    // ------------------------------------------------------------------ 목록 · 요약

    @Schema(name = "CreatorPerformanceListItem",
            description = "성과 목록 행 — 집계 중은 판매 3열 · 리워드가 null(「—」) · 조정 협의는 판매 3열만 값 · 정산 완료는 전부 값")
    public record ListItem(
            @Schema(description = "공구 ID — 상세 경로 키", example = "21") Long groupBuyId,
            @Schema(description = "공구 번호", example = "GB-20260801-003") String groupBuyNumber,
            @Schema(description = "공구명(계약 제목)", example = "데일리 선크림 공구") String groupBuyTitle,
            @Schema(description = "브랜드명", example = "데일리랩") String brandName,
            @Schema(description = "브랜드(마켓) ID", example = "4") Long marketId,
            @Schema(description = "공구 시작", example = "2026-08-01T10:00:00") LocalDateTime periodStartAt,
            @Schema(description = "공구 종료 — 조기 마감이면 실제 종료 시각", example = "2026-08-07T23:59:00") LocalDateTime periodEndAt,
            @Schema(description = "상태 — COLLECTING(집계 중) · ADJUSTING(조정 협의) · SETTLED(정산 완료)", example = "SETTLED")
            CreatorPerformanceStatus status,
            @Schema(description = "상태 라벨", example = "정산 완료") String statusLabel,
            @Schema(description = "배지 톤 — NEUTRAL · WARNING · SUCCESS", example = "SUCCESS") PerformanceTone statusTone,
            @Schema(description = "확정 거래액(원) — 집계 중이면 null", example = "5480000", nullable = true)
            Long confirmedSalesAmount,
            @Schema(description = "판매 수량(확정 · 개) — 집계 중이면 null", example = "274", nullable = true)
            Long confirmedQuantity,
            @Schema(description = "구매전환율(%) = 구매확정(건) ÷ 쇼룸 방문(명) — 집계 중 · 방문 0 이면 null", example = "5.8",
                    nullable = true)
            Double conversionRate,
            @Schema(description = "리워드 단계 — CONFIRMED 면 myRewardAmount 가 값 · AFTER_AGREEMENT 「합의 후 확정」 · AFTER_SETTLEMENT 「—」",
                    example = "CONFIRMED")
            RewardStage rewardStage,
            @Schema(description = "내 리워드(원) = 판매 리워드(합의 반영) + 고정 지급비 · 세전 — CONFIRMED 에서만", example = "1003600",
                    nullable = true)
            Long myRewardAmount) {
    }

    @Schema(name = "CreatorPerformanceSummary", description = "목록 머리(기간) · KPI 8장 · 상태 칩 — 기간만 따르고 검색어 · 칩과 무관")
    public record Summary(
            @Schema(description = "기간 시작(오늘 − months)", example = "2026-04-18") LocalDate periodFrom,
            @Schema(description = "기간 끝(오늘)", example = "2026-10-18") LocalDate periodTo,
            @Schema(description = "기간 라벨", example = "최근 6개월") String periodLabel,
            @Schema(description = "KPI 8장 — 정산 완료 공구만") Kpi kpi,
            @Schema(description = "상태 칩 건수 — 기간 안 · 세 키가 항상 있다(0 포함)",
                    example = "{\"COLLECTING\": 1, \"ADJUSTING\": 1, \"SETTLED\": 4}")
            Map<CreatorPerformanceStatus, Long> statusCounts,
            @Schema(description = "기간 안에 종료 공구가 하나라도 있는가 — false 면 빈 상태(L2)", example = "true") boolean hasAnyEnded) {
    }

    @Schema(name = "CreatorPerformanceKpi",
            description = "KPI 8장 — 모집단은 정산 완료 공구만(§49-1) · 평균은 공구별 값의 산술평균. 정산 완료 0건이면 settledCount 만 0 이고 나머지는 null")
    public record Kpi(
            @Schema(description = "누적 리워드(원) = Σ(판매 리워드 + 고정 지급비) · 세전", example = "2536600", nullable = true)
            Long totalReward,
            @Schema(description = "평균 리워드(원) — 공구 1건당 · 1원 절사", example = "634150", nullable = true) Long averageReward,
            @Schema(description = "완료 공구(건)", example = "4") long settledCount,
            @Schema(description = "평균 구매전환율(%) — 공구별 (구매확정 ÷ 쇼룸 방문) 산술평균", example = "5.4", nullable = true)
            Double averageConversionRate,
            @Schema(description = "평균 재구매율(%) — 공구별 (이전 내 공구 구매자 ÷ 구매 소비자) 산술평균", example = "28.6", nullable = true)
            Double averageRepeatRate,
            @Schema(description = "평균 구매 소비자 중 팔로워(%) — 결제 시점 팔로워 기준", example = "45.2", nullable = true)
            Double averageFollowerBuyerRate,
            @Schema(description = "평균 공구 게시물 조회수(회) — 게시물이 있는 공구만", example = "4180", nullable = true)
            Long averagePostImpressions,
            @Schema(description = "성비 — 아는 성별이 없으면 null", nullable = true) GenderRatio genderRatio) {
    }

    @Schema(name = "CreatorPerformanceGenderRatio", description = "성비(정수 %) — 미확인 제외 · 두 값의 합은 100 · 큰 쪽을 앞에(majorFirst)")
    public record GenderRatio(
            @Schema(description = "앞에 둘 쪽 — FEMALE | MALE", example = "FEMALE") String majorFirst,
            @Schema(description = "여성 %(정수)", example = "83") int female,
            @Schema(description = "남성 %(정수)", example = "17") int male) {
    }

    // ------------------------------------------------------------------ 상세

    @Schema(name = "CreatorPerformanceDetail",
            description = "성과 상세(D1 ~ D4) — 상태에 따라 블록이 값 · estimated · null 로 갈린다. 원가 · 정산액 · 배송 품질 · 사유 분해는 없다(파트너 전용)")
    public record Detail(
            @Schema(description = "헤더") Header groupBuy,
            @Schema(description = "상단 안내 — 해당 없으면 null", nullable = true) Notice notice,
            @Schema(description = "리워드 바 — 판매 리워드 · 고정 지급비 · 합계") RewardBar rewardBar,
            @Schema(description = "KPI") DetailKpi kpi,
            @Schema(description = "① 유입") Traffic traffic,
            @Schema(description = "전환 퍼널 2구간") Funnel funnel,
            @Schema(description = "② 판매") Sales sales,
            @Schema(description = "③ 소비자") Consumers consumers,
            @Schema(description = "④ 팔로워") Followers followers,
            @Schema(description = "⑤ 리워드") Reward reward,
            @Schema(description = "[이 브랜드와 재협업 제안] 링크 재료") Recollaboration recollaboration) {
    }

    @Schema(name = "CreatorPerformanceHeader", description = "상세 헤더")
    public record Header(
            @Schema(description = "공구 ID", example = "21") Long groupBuyId,
            @Schema(description = "공구 번호", example = "GB-20260801-003") String groupBuyNumber,
            @Schema(description = "공구명(계약 제목)", example = "데일리 선크림 공구") String groupBuyTitle,
            @Schema(description = "브랜드명", example = "데일리랩") String brandName,
            @Schema(description = "브랜드(마켓) ID", example = "4") Long marketId,
            @Schema(description = "계약 ID — [계약서 보기]", example = "70") Long contractId,
            @Schema(description = "공구 시작 — 유입 · 게시물 · 팔로워 집계 창의 시작", example = "2026-08-01T10:00:00")
            LocalDateTime periodStartAt,
            @Schema(description = "공구 종료 — 조기 마감이면 실제 종료 시각", example = "2026-08-07T23:59:00") LocalDateTime periodEndAt,
            @Schema(description = "상태 3종", example = "SETTLED") CreatorPerformanceStatus status,
            @Schema(description = "상태 라벨", example = "정산 완료") String statusLabel,
            @Schema(description = "배지 톤", example = "SUCCESS") PerformanceTone statusTone) {
    }

    @Schema(name = "CreatorPerformanceNotice",
            description = "상단 안내 — 집계 중(미종결 있음) NEUTRAL · 조정 협의 WARNING · 합의 반영 NEUTRAL. 그 외(정산 완료 · 정산 확인 중 · 지급 예정 · 기한 만료)는 null")
    public record Notice(
            @Schema(description = "톤 — NEUTRAL | WARNING", example = "WARNING") PerformanceTone tone,
            @Schema(description = "안내 문장(서버 문장 그대로)",
                    example = "정산 조정 협의 중이에요. 리워드는 브랜드와 합의한 금액으로 확정되고, 합의 기한(09.10)까지 합의가 없으면 원래 금액으로 확정돼요. 판매 · 유입 · 소비자 지표는 확정값입니다.")
            String text,
            @Schema(description = "버튼 — ISSUE_THREAD([이슈 스레드 열기] · 조정 협의) | ISSUE_THREAD_HISTORY([기록 보기] · 합의 반영) | null(집계 중)",
                    example = "ISSUE_THREAD", nullable = true)
            String link,
            @Schema(description = "이슈 스레드 ID — link 가 있을 때만", example = "140", nullable = true) Long threadId) {
    }

    @Schema(name = "CreatorPerformanceMoney",
            description = "금액 칸 — stage 가 CONFIRMED 가 아니면 amount = null 이고 금액 대신 stage 라벨(「정산 후 확정」 · 「합의 후 확정」)을 그린다")
    public record Money(
            @Schema(description = "돈의 단계 — AFTER_SETTLEMENT · AFTER_AGREEMENT · CONFIRMED", example = "CONFIRMED") RewardStage stage,
            @Schema(description = "확정 금액(원) — CONFIRMED 에서만", example = "603600", nullable = true) Long amount,
            @Schema(description = "판매 리워드의 원래 금액(계약 기준) — 정산 전이면 예정액(「예정 677,400원」) · 협의 중이면 「원래 N원」. 고정 지급비 · 합계 칸은 null",
                    example = "603600", nullable = true)
            Long originalAmount,
            @Schema(description = "보조줄 — 판매 리워드는 「확정 거래액 × 리워드율(상품별)」(정산 전이면 「 · 예정」) · 합의 반영이면 「원래 N원 + 조정 +N원」",
                    example = "확정 거래액 × 리워드율(상품별)", nullable = true) String note) {
    }

    @Schema(name = "CreatorPerformanceRewardBar",
            description = "리워드 바 — 고정 지급비는 계약 금액이라 언제나 CONFIRMED · 합계(totalReward)는 판매 리워드의 stage 를 따른다")
    public record RewardBar(
            @Schema(description = "판매 리워드 — 합의 반영(D4)이면 note 「원래 N원 + 조정 +N원」") Money salesReward,
            @Schema(description = "고정 지급비 — note 「브랜드 직접 지급」 · 고정 지급비가 없는 계약은 0") Money fixedFee,
            @Schema(description = "내 리워드 합계 = 판매 리워드 + 고정 지급비 · 세전") Money totalReward) {
    }

    @Schema(name = "CreatorPerformanceAmountFigure", description = "금액 지표 — estimated = true 면 「예정」 태그")
    public record AmountFigure(
            @Schema(description = "금액(원)", example = "5480000", nullable = true) Long value,
            @Schema(description = "산식 · 분모 보조줄", example = "구매확정 291건 기준") String basis,
            @Schema(description = "예정값인가(집계 중 · 정산 전)", example = "false") boolean estimated) {
    }

    @Schema(name = "CreatorPerformanceRateFigure", description = "비율 지표(%) — estimated = true 면 「예정」 태그 · 분모가 0 이면 null")
    public record RateFigure(
            @Schema(description = "비율(% · 소수 1자리)", example = "5.8", nullable = true) Double value,
            @Schema(description = "산식 · 분모 보조줄", example = "쇼룸 방문 5,010명 대비 구매확정") String basis,
            @Schema(description = "예정값인가(집계 중 · 정산 전)", example = "false") boolean estimated) {
    }

    @Schema(name = "CreatorPerformanceDetailKpi", description = "상세 KPI")
    public record DetailKpi(
            @Schema(description = "내 리워드 = 리워드 바의 totalReward 와 같다") Money myReward,
            @Schema(description = "확정 거래액 — basis 「구매확정 N건 기준」") AmountFigure confirmedSales,
            @Schema(description = "구매전환율 = 구매확정(건) ÷ 쇼룸 방문(명)") RateFigure conversionRate,
            @Schema(description = "재구매율 = 이전 내 공구에서도 구매확정한 소비자 ÷ 구매 소비자(명)") RateFigure repeatRate,
            @Schema(description = "팔로워 증감 — 확정값") FollowerDelta followerDelta,
            @Schema(description = "구매 소비자 성비 — 아는 성별이 없으면 null", nullable = true) GenderRatio genderRatio,
            @Schema(description = "성비가 예정값인가", example = "false") boolean genderEstimated) {
    }

    @Schema(name = "CreatorPerformanceFollowerDelta", description = "팔로워 증감 — 확정값(예정 없음)")
    public record FollowerDelta(
            @Schema(description = "기간 증감 = 종료 시점 − 시작 시점 팔로워(음수 가능)", example = "312") int delta,
            @Schema(description = "종료 시점 팔로워 수", example = "12480") int countAtEnd) {
    }

    @Schema(name = "CreatorPerformanceTraffic", description = "① 유입 — 공구 기간 · 이 쇼룸 · 확정값")
    public record Traffic(
            @Schema(description = "쇼룸 방문(명) — 공구 기간 고유 방문자", example = "5010") long visitorCount,
            @Schema(description = "유입 경로 4종 — 항상 4행(INTERNAL · PROFILE_LINK · POST_LINK · PRODUCT_LINK)") List<SourceItem> sources,
            @Schema(description = "공구 게시물 — 게시물이 없으면 null", nullable = true) PostBlock post) {
    }

    @Schema(name = "CreatorPerformanceSourceItem", description = "유입 경로 — 사람당 첫 방문의 경로 하나로 센다 · 4종 합 = 쇼룸 방문(명)")
    public record SourceItem(
            @Schema(description = "경로 키 — INTERNAL(쇼룸 내부 탐색) · PROFILE_LINK(프로필 쇼룸 링크) · POST_LINK(공구 게시물 링크) · PRODUCT_LINK(상품 상세 직접 링크)",
                    example = "INTERNAL")
            String source,
            @Schema(description = "경로 라벨", example = "쇼룸 내부 탐색") String label,
            @Schema(description = "방문자(명)", example = "1880") long visitors,
            @Schema(description = "비율(%) = visitors ÷ 쇼룸 방문 — 방문 0 이면 null", example = "37.5", nullable = true) Double ratio) {
    }

    @Schema(name = "CreatorPerformancePostBlock", description = "공구 게시물 — 쇼룸 내부 지표 · 공구당 1건 · 공구 기간")
    public record PostBlock(
            @Schema(description = "게시물 ID", example = "301") Long postId,
            @Schema(description = "게시물 조회(회)", example = "4290") long impressions,
            @Schema(description = "고유 조회자(명)", example = "3140") long uniqueViewers,
            @Schema(description = "게시물 → 상품 상세 이동(회)", example = "1062") long productViews,
            @Schema(description = "상세 이동률(%) = productViews ÷ impressions", example = "24.8", nullable = true) Double productViewRate,
            @Schema(description = "게시물 → 주문(건) — 결제 시점 라스트 터치(24시간)가 이 게시물인 하위주문", example = "152") long orders,
            @Schema(description = "주문률(%) = orders ÷ productViews", example = "14.3", nullable = true) Double orderRate,
            @Schema(description = "좋아요(기간)", example = "312") long likes,
            @Schema(description = "공유 — 앱에 공유 이벤트가 없어 항상 null(「—」)", nullable = true) Long shares) {
    }

    @Schema(name = "CreatorPerformanceFunnel",
            description = "전환 퍼널 2구간 — 구간 1 막대 기준 = 쇼룸 방문 · 구간 2 막대 기준 = 주문. 검산식: 주문 = 구매확정 + 취소 + 반품 + 배송 예외")
    public record Funnel(
            @Schema(description = "구간 1 — 쇼룸 방문(명)", example = "5010") long visitorCount,
            @Schema(description = "주문(건) — 결제 완료 하위주문 수", example = "313") long orderCount,
            @Schema(description = "「방문 N명은 주문하지 않았습니다」 = 방문 − 주문 · 음수면 0", example = "4697") long nonOrderVisitors,
            @Schema(description = "구간 2 — 구매확정(건)", example = "291") long confirmedOrderCount,
            @Schema(description = "취소(건)", example = "8") long cancelCount,
            @Schema(description = "반품(건) — 전량 반품 하위주문", example = "13") long returnCount,
            @Schema(description = "배송 예외(건) — 반송 · 분실 등", example = "1") long deliveryExceptionCount,
            @Schema(description = "구매확정률(%) = 구매확정 ÷ 주문", example = "93.0", nullable = true) Double confirmedRate,
            @Schema(description = "최종 구매전환율(%) = 구매확정 ÷ 쇼룸 방문", example = "5.8", nullable = true) Double finalConversionRate,
            @Schema(description = "구간 2 가 예정값인가(집계 중 · 정산 전)", example = "false") boolean estimated,
            @Schema(description = "주문 전환율(%) = 주문 ÷ 쇼룸 방문 — 방문 0 이면 null(58 계획 5-9)", example = "6.2", nullable = true)
            Double orderVisitRate) {
    }

    @Schema(name = "CreatorPerformanceSales", description = "② 판매 — 상품별 값은 계약 기준(합의 차액은 adjustmentDelta 한 줄)")
    public record Sales(
            @Schema(description = "예정값인가(집계 중 · 정산 전) — 「예정」 태그", example = "false") boolean estimated,
            @Schema(description = "상품별 판매 — 계약 상품 순서") List<ProductRow> products,
            @Schema(description = "상품별 리워드 합(원) = 계약 기준 판매 리워드", example = "603600", nullable = true)
            Long contractRewardAmount,
            @Schema(description = "합의 반영 차액(원) = 합의 금액 − 원래 금액 — D4 만 · 상품에 배분하지 않는다", example = "14472",
                    nullable = true)
            Long adjustmentDelta,
            @Schema(description = "하이라이트 3장") Highlights highlights) {
    }

    @Schema(name = "CreatorPerformanceProductRow", description = "상품별 판매 한 줄")
    public record ProductRow(
            @Schema(description = "상품 ID", example = "501") Long productId,
            @Schema(description = "상품명", example = "데일리 선크림 50ml") String productName,
            @Schema(description = "상세 조회(회) — 공구 기간 · 이 공구로 귀속된 상품 상세 조회", example = "2140") long detailViews,
            @Schema(description = "주문(건) — 이 상품이 든 결제 완료 하위주문", example = "150") long orders,
            @Schema(description = "확정 수량(개)", example = "135") long confirmedQuantity,
            @Schema(description = "확정 거래액(원)", example = "2700000") long confirmedAmount,
            @Schema(description = "리워드율(%) — 계약 상품의 율", example = "10.0") BigDecimal rewardRate,
            @Schema(description = "판매 리워드(원) — 계약 기준 · 단가별 1원 절사", example = "270000") long rewardAmount) {
    }

    @Schema(name = "CreatorPerformanceHighlights",
            description = "하이라이트 3장 — 상품별 표 아래 요약 · 근거 수치와 분모를 basis 에 · 평가어 없음. 값이 모두 0 이면 그 카드는 null")
    public record Highlights(
            @Schema(description = "가장 많이 본 상품 — 상세 조회 최대", nullable = true) Highlight mostViewed,
            @Schema(description = "전환율이 높은 상품 — 주문 ÷ 상세 조회 최대(조회 0 제외)", nullable = true) Highlight bestConversion,
            @Schema(description = "리워드가 큰 상품 — 판매 리워드 최대", nullable = true) Highlight topReward) {
    }

    @Schema(name = "CreatorPerformanceHighlight", description = "하이라이트 카드")
    public record Highlight(
            @Schema(description = "상품 ID", example = "502") Long productId,
            @Schema(description = "상품명", example = "톤업 선크림 30ml") String productName,
            @Schema(description = "근거 문장(서버 문장 그대로)", example = "조회 1,720회 중 163건 · 9.5%") String basis) {
    }

    @Schema(name = "CreatorPerformanceConsumers", description = "③ 소비자 — 구매확정 하위주문의 소비자(중복 제거) 기준")
    public record Consumers(
            @Schema(description = "예정값인가(집계 중 · 정산 전)", example = "false") boolean estimated,
            @Schema(description = "구매 소비자(명) — 같은 소비자의 재구매 제외", example = "288") long buyerCount,
            @Schema(description = "신규 소비자(명) = 구매 소비자 − 재구매", example = "206") long newBuyerCount,
            @Schema(description = "재구매 소비자(명) — 이 공구 시작 전 같은 인플루언서의 다른 공구에서 구매확정한 소비자", example = "82")
            long repeatBuyerCount,
            @Schema(description = "팔로워 구매 소비자(명) — 결제 시점 팔로워", example = "130") long followerBuyerCount,
            @Schema(description = "구매 소비자 중 팔로워(%)", example = "45.1", nullable = true) Double followerBuyerRate,
            @Schema(description = "연령 · 성별 분포") Distribution distribution,
            @Schema(description = "신규 소비자 비중(%) = 신규 ÷ 구매 소비자 — 구매 0 이면 null(58 계획 5-9)", example = "71.5", nullable = true)
            Double newBuyerRate) {
    }

    @Schema(name = "CreatorPerformanceDistribution",
            description = "연령 · 성별 분포 — 표본(구매 소비자) 30명 미만이면 suppressed(비율 비공개) · 구간 5명 미만은 「기타」로 합산 · 나이는 공구 종료일 기준")
    public record Distribution(
            @Schema(description = "비공개인가 — true 면 genders = null · ageGroups = []", example = "false") boolean suppressed,
            @Schema(description = "표본(구매 소비자 · 명)", example = "288") long sampleSize,
            @Schema(description = "공개 최소 표본(명)", example = "30") int minimumSampleSize,
            @Schema(description = "성별 — suppressed 면 null", nullable = true) GenderSplit genders,
            @Schema(description = "연령대 — 쇼룸 현황과 같은 구간(18–24세 · 25–34세 · 35–44세 · 45세 이상 · 미확인) + 「기타」(5명 미만 구간 합산) · 0명 구간은 없다")
            List<AgeGroup> ageGroups) {
    }

    @Schema(name = "CreatorPerformanceGenderSplit", description = "성별 분포 — 비율은 미확인을 뺀 분모 · 인원은 미확인 포함")
    public record GenderSplit(
            @Schema(description = "여성(%)", example = "82.5", nullable = true) Double female,
            @Schema(description = "남성(%)", example = "17.5", nullable = true) Double male,
            @Schema(description = "여성(명)", example = "236") long femaleCount,
            @Schema(description = "남성(명)", example = "50") long maleCount,
            @Schema(description = "미확인(명)", example = "2") long unknownCount) {
    }

    @Schema(name = "CreatorPerformanceAgeGroup", description = "연령대 한 줄 — 비율 분모는 전체 구매 소비자(sampleSize)")
    public record AgeGroup(
            @Schema(description = "구간 라벨", example = "25–34세") String label,
            @Schema(description = "인원(명)", example = "128") long count,
            @Schema(description = "구간 비율(%)", example = "44.4") Double total,
            @Schema(description = "구간 안 여성 — 전체 대비 %", example = "36.8") Double female,
            @Schema(description = "구간 안 남성 — 전체 대비 %", example = "7.6") Double male) {
    }

    @Schema(name = "CreatorPerformanceFollowers",
            description = "④ 팔로워 — 팔로워 · 비팔로워 비교표 + 스냅샷. 판정은 사건 시각(첫 방문 · 첫 조회 · 결제) 기준 · 비로그인은 비팔로워")
    public record Followers(
            @Schema(description = "팔로워 열") Side follower,
            @Schema(description = "비팔로워 열") Side nonFollower,
            @Schema(description = "종료 시점 팔로워 수", example = "12480") int countAtEnd,
            @Schema(description = "기간 증감(음수 가능)", example = "312") int delta,
            @Schema(description = "쇼룸 방문 중 팔로워 비중(%)", example = "44.1", nullable = true) Double followerVisitorRate,
            @Schema(description = "구매전환율 열만 예정값인가(집계 중 · 정산 전)", example = "false") boolean conversionEstimated) {
    }

    @Schema(name = "CreatorPerformanceSide", description = "비교표 한 열 — 합계 행은 없다(위 패널에 총량이 있다)")
    public record Side(
            @Schema(description = "쇼룸 방문(명)", example = "2210") long visitors,
            @Schema(description = "게시물 고유 조회자(명) — 게시물이 없으면 0", example = "1480") long postViewers,
            @Schema(description = "주문(건)", example = "140") long orders,
            @Schema(description = "구매확정(하위주문 건) — 전환율의 분자. 두 열 합 = 퍼널 confirmedOrderCount", example = "132")
            long confirmedOrders,
            @Schema(description = "구매전환율(%) = 구매확정 ÷ 방문 — 방문 0 이면 null", example = "6.0", nullable = true)
            Double conversionRate) {
    }

    @Schema(name = "CreatorPerformanceReward", description = "⑤ 리워드 — 돈은 정산 행이 정본이고 서버 · FE 모두 계산하지 않는다")
    public record Reward(
            @Schema(description = "돈의 단계 — AFTER_SETTLEMENT · AFTER_AGREEMENT · CONFIRMED", example = "CONFIRMED") RewardStage stage,
            @Schema(description = "확정 거래액(원) — 집계 중(정산 전)이면 예정값", example = "5480000", nullable = true)
            Long confirmedSalesAmount,
            @Schema(description = "판매 리워드 원래 금액(계약 기준 · 원) — 정산 전은 예정액 · 항상 값", example = "603600", nullable = true)
            Long originalRewardAmount,
            @Schema(description = "조정 협의 — 내 최신 제안 · ADJUSTING 이 아니면 null", nullable = true) Offer myRequest,
            @Schema(description = "조정 협의 — 브랜드 최신 제안 · ADJUSTING 이 아니면 null", nullable = true) Offer counterProposal,
            @Schema(description = "합의 반영 — 정산 완료 + 합의로 금액이 바뀐 경우만(D4). 기한 만료(원래 금액)는 null", nullable = true)
            Adjustment adjustment,
            @Schema(description = "확정 판매 리워드(원 · 합의 반영) — CONFIRMED 에서만", example = "603600", nullable = true)
            Long finalRewardAmount,
            @Schema(description = "고정 지급비(원) — 계약 금액 · 브랜드 직접 지급 · 없으면 0", example = "400000") long fixedFeeAmount,
            @Schema(description = "내 리워드 합계(원 · 세전) = 확정 판매 리워드 + 고정 지급비 — CONFIRMED 에서만", example = "1003600",
                    nullable = true)
            Long totalRewardAmount,
            @Schema(description = "판매 리워드 실지급") Payout payout,
            @Schema(description = "정산 ID — [정산 상세 보기] · 정산 전이면 null", example = "26", nullable = true) Long settlementId,
            @Schema(description = "정산 상태 라벨(수취자 기준 · 분배 실패는 「지급 완료」) — 정산 전이면 null", example = "지급 완료", nullable = true)
            String settlementStatusLabel,
            @Schema(description = "조정 이슈 스레드 ID — 조정 이력이 있으면 값", example = "140", nullable = true) Long adjustmentThreadId,
            @Schema(description = "합의 기한 — ADJUSTING 에서만", example = "2026-09-10T23:59:59", nullable = true)
            LocalDateTime adjustmentDeadlineAt) {
    }

    @Schema(name = "CreatorPerformanceOffer", description = "조정 제안 — 금액 · 시각")
    public record Offer(
            @Schema(description = "제안한 판매 리워드(원)", example = "1591512") long amount,
            @Schema(description = "제안 시각", example = "2026-09-05T15:02:00") LocalDateTime at) {
    }

    @Schema(name = "CreatorPerformanceAdjustment", description = "합의 반영 — 「조정 반영 +14,472원 · 09.08 합의」")
    public record Adjustment(
            @Schema(description = "차액(원) = 합의 금액 − 원래 금액(음수 가능)", example = "14472") long delta,
            @Schema(description = "합의 시각", example = "2026-09-08T16:40:00") LocalDateTime agreedAt,
            @Schema(description = "합의된 제안을 낸 쪽 — 「브랜드 제안」 | 「내 요청」 | 「합의」", example = "브랜드 제안") String agreedProposalLabel) {
    }

    @Schema(name = "CreatorPerformancePayout", description = "판매 리워드 실지급 — 정산 행의 인플루언서 실지급액 그대로(계산하지 않는다)")
    public record Payout(
            @Schema(description = "사업자 구분 — 정산 행 스냅샷 · 정산 전이면 현재 값", example = "INDIVIDUAL") CreatorBusinessType businessType,
            @Schema(description = "실지급액(원) — 비사업자는 3.3% 원천징수 후 · 사업자는 부가세 포함. CONFIRMED 에서만", example = "583682",
                    nullable = true)
            Long amount,
            @Schema(description = "보조줄 — 「비사업자 · 3.3% 원천징수는 SHOWROOMZ가 신고」 | 「사업자 · 부가세 포함 금액」",
                    example = "비사업자 · 3.3% 원천징수는 SHOWROOMZ가 신고")
            String note) {
    }

    @Schema(name = "CreatorPerformanceRecollaboration", description = "[이 브랜드와 재협업 제안] — 브랜드 1:1 스레드로 이동하는 링크 재료")
    public record Recollaboration(
            @Schema(description = "브랜드(마켓) ID", example = "4") Long marketId,
            @Schema(description = "브랜드 ↔ 인플루언서 1:1 스레드 ID — 없으면 null", example = "88", nullable = true) Long threadId) {
    }
}

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
 */
public final class CreatorPerformanceDto {

    private CreatorPerformanceDto() {
    }

    // ------------------------------------------------------------------ 목록 · 요약

    @Schema(description = "성과 목록 행 — 집계 중은 판매 지표 · 리워드가 null(「—」) · 조정 협의는 판매 지표만 값")
    public record ListItem(
            @Schema(example = "21") Long groupBuyId,
            @Schema(example = "GB-20260801-003") String groupBuyNumber,
            @Schema(example = "데일리 선크림 공구") String groupBuyTitle,
            @Schema(example = "데일리랩") String brandName,
            @Schema(example = "4") Long marketId,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            CreatorPerformanceStatus status,
            @Schema(example = "정산 완료") String statusLabel,
            PerformanceTone statusTone,
            @Schema(description = "확정 거래액 — 집계 중이면 null", example = "5480000", nullable = true)
            Long confirmedSalesAmount,
            @Schema(description = "판매 수량(확정) — 집계 중이면 null", example = "274", nullable = true)
            Long confirmedQuantity,
            @Schema(description = "구매전환율(%) = 구매확정 ÷ 쇼룸 방문(명) — 집계 중 · 방문 0 이면 null", example = "5.8",
                    nullable = true)
            Double conversionRate,
            @Schema(description = "CONFIRMED 면 myRewardAmount 가 값 · 그 외 「정산 후 확정」/「합의 후 확정」")
            RewardStage rewardStage,
            @Schema(description = "내 리워드 = 판매 리워드(합의 반영) + 고정 지급비 · 세전", example = "1003600", nullable = true)
            Long myRewardAmount) {
    }

    @Schema(description = "목록 머리 · KPI 8장 · 상태 칩")
    public record Summary(
            LocalDate periodFrom,
            LocalDate periodTo,
            @Schema(example = "최근 6개월") String periodLabel,
            Kpi kpi,
            @Schema(description = "상태 칩 건수 — 기간 안 · 검색어 · 칩 선택과 무관") Map<CreatorPerformanceStatus, Long> statusCounts,
            @Schema(description = "기간 안에 종료 공구가 하나라도 있는가 — false 면 빈 상태(L2)") boolean hasAnyEnded) {
    }

    @Schema(description = "KPI 8장 — 모집단은 정산 완료 공구만(§49-1). 값이 없으면 null(「—」) · 완료 공구만 0")
    public record Kpi(
            @Schema(description = "누적 리워드 = Σ(판매 리워드 + 고정 지급비)", example = "2536600", nullable = true) Long totalReward,
            @Schema(description = "평균 리워드 — 공구 1건당 · 1원 절사", example = "634150", nullable = true) Long averageReward,
            @Schema(description = "완료 공구(건)", example = "4") long settledCount,
            @Schema(description = "평균 구매전환율(%) — 공구별 산술평균", example = "5.4", nullable = true) Double averageConversionRate,
            @Schema(description = "평균 재구매율(%) — 이전 내 공구 구매자 ÷ 구매 소비자", example = "28.6", nullable = true)
            Double averageRepeatRate,
            @Schema(description = "평균 구매 소비자 중 팔로워(%)", example = "45.2", nullable = true) Double averageFollowerBuyerRate,
            @Schema(description = "평균 공구 게시물 조회수(회)", example = "4180", nullable = true) Long averagePostImpressions,
            @Schema(nullable = true) GenderRatio genderRatio) {
    }

    @Schema(description = "성비 — 미확인 제외 · 큰 쪽을 앞에(majorFirst)")
    public record GenderRatio(
            @Schema(description = "앞에 둘 쪽 — FEMALE | MALE", example = "FEMALE") String majorFirst,
            @Schema(description = "여성 %(정수)", example = "83") int female,
            @Schema(description = "남성 %(정수)", example = "17") int male) {
    }

    // ------------------------------------------------------------------ 상세

    @Schema(description = "성과 상세(D1 ~ D4) — 상태에 따라 블록이 값 · estimated · null 로 갈린다")
    public record Detail(
            Header groupBuy,
            @Schema(nullable = true) Notice notice,
            RewardBar rewardBar,
            DetailKpi kpi,
            Traffic traffic,
            Funnel funnel,
            Sales sales,
            Consumers consumers,
            Followers followers,
            Reward reward,
            Recollaboration recollaboration) {
    }

    public record Header(Long groupBuyId, String groupBuyNumber, String groupBuyTitle, String brandName, Long marketId,
                         Long contractId, LocalDateTime periodStartAt, LocalDateTime periodEndAt,
                         CreatorPerformanceStatus status, String statusLabel, PerformanceTone statusTone) {
    }

    @Schema(description = "상단 안내 — 집계 중(미종결) 중립 · 조정 협의 경고 · 합의 반영 중립. 해당 없으면 null")
    public record Notice(
            PerformanceTone tone,
            String text,
            @Schema(description = "ISSUE_THREAD(이슈 스레드 열기) | ISSUE_THREAD_HISTORY(기록 보기) | null", nullable = true)
            String link,
            @Schema(nullable = true) Long threadId) {
    }

    @Schema(description = "금액 칸 — stage 가 CONFIRMED 가 아니면 amount = null 이고 originalAmount 가 원래(또는 예정) 금액")
    public record Money(RewardStage stage, @Schema(nullable = true) Long amount,
                        @Schema(nullable = true) Long originalAmount, @Schema(nullable = true) String note) {
    }

    public record RewardBar(Money salesReward, Money fixedFee, Money totalReward) {
    }

    @Schema(description = "금액 지표 — estimated = 「예정」 태그")
    public record AmountFigure(@Schema(nullable = true) Long value, String basis, boolean estimated) {
    }

    @Schema(description = "비율 지표(%) — estimated = 「예정」 태그")
    public record RateFigure(@Schema(nullable = true) Double value, String basis, boolean estimated) {
    }

    public record DetailKpi(Money myReward, AmountFigure confirmedSales, RateFigure conversionRate,
                            RateFigure repeatRate, FollowerDelta followerDelta,
                            @Schema(nullable = true) GenderRatio genderRatio, boolean genderEstimated) {
    }

    @Schema(description = "팔로워 증감 — 확정값(예정 없음)")
    public record FollowerDelta(int delta, int countAtEnd) {
    }

    @Schema(description = "① 유입 — 확정값")
    public record Traffic(long visitorCount, List<SourceItem> sources, @Schema(nullable = true) PostBlock post) {
    }

    @Schema(description = "유입 경로 — 사람당 첫 방문의 경로 · 4종 합 = 쇼룸 방문(명)")
    public record SourceItem(String source, String label, long visitors, @Schema(nullable = true) Double ratio) {
    }

    @Schema(description = "공구 게시물 — 쇼룸 내부 지표 · 공구당 1건")
    public record PostBlock(Long postId, long impressions, long uniqueViewers, long productViews,
                            @Schema(nullable = true) Double productViewRate, long orders,
                            @Schema(nullable = true) Double orderRate, long likes,
                            @Schema(description = "공유 — 앱에 공유 이벤트가 없어 항상 null", nullable = true) Long shares) {
    }

    @Schema(description = "전환 퍼널 2구간 — 구간 1 기준 = 방문 · 구간 2 기준 = 주문 · 검산 주문 = 확정 + 취소 + 반품 + 배송 예외")
    public record Funnel(long visitorCount, long orderCount, long nonOrderVisitors, long confirmedOrderCount,
                         long cancelCount, long returnCount, long deliveryExceptionCount,
                         @Schema(nullable = true) Double confirmedRate, @Schema(nullable = true) Double finalConversionRate,
                         boolean estimated) {
    }

    @Schema(description = "② 판매 — 상품별 값은 계약 기준(합의 차액은 adjustmentDelta 한 줄)")
    public record Sales(boolean estimated, List<ProductRow> products, @Schema(nullable = true) Long contractRewardAmount,
                        @Schema(description = "합의 반영 차액 — D4 만 · 상품에 배분하지 않는다", nullable = true)
                        Long adjustmentDelta,
                        Highlights highlights) {
    }

    public record ProductRow(Long productId, String productName, long detailViews, long orders, long confirmedQuantity,
                             long confirmedAmount, BigDecimal rewardRate, long rewardAmount) {
    }

    @Schema(description = "하이라이트 3장 — 근거 수치와 분모를 basis 에 · 평가어 없음")
    public record Highlights(@Schema(nullable = true) Highlight mostViewed,
                             @Schema(nullable = true) Highlight bestConversion,
                             @Schema(nullable = true) Highlight topReward) {
    }

    public record Highlight(Long productId, String productName, String basis) {
    }

    @Schema(description = "③ 소비자 — 구매확정 소비자 기준")
    public record Consumers(boolean estimated, long buyerCount, long newBuyerCount, long repeatBuyerCount,
                            long followerBuyerCount, @Schema(nullable = true) Double followerBuyerRate,
                            Distribution distribution) {
    }

    @Schema(description = "연령 · 성별 분포 — 표본 30명 미만이면 suppressed(비율 비공개) · 구간 5명 미만은 「기타」로 합산")
    public record Distribution(boolean suppressed, long sampleSize, int minimumSampleSize,
                               @Schema(nullable = true) GenderSplit genders, List<AgeGroup> ageGroups) {
    }

    public record GenderSplit(@Schema(nullable = true) Double female, @Schema(nullable = true) Double male,
                              long femaleCount, long maleCount, long unknownCount) {
    }

    @Schema(description = "연령대 한 줄 — 비율은 전체 구매 소비자 대비 %, 인원도 함께")
    public record AgeGroup(String label, long count, Double total, Double female, Double male) {
    }

    @Schema(description = "④ 팔로워 — 비교표 + 스냅샷(종료 시점 · 기간 증감 · 방문 중 팔로워 비중)")
    public record Followers(Side follower, Side nonFollower, int countAtEnd, int delta,
                            @Schema(nullable = true) Double followerVisitorRate,
                            @Schema(description = "구매전환율 열만 예정") boolean conversionEstimated) {
    }

    public record Side(long visitors, long postViewers, long orders, @Schema(nullable = true) Double conversionRate) {
    }

    @Schema(description = "⑤ 리워드 — 돈은 정산 행이 정본")
    public record Reward(
            RewardStage stage,
            @Schema(nullable = true) Long confirmedSalesAmount,
            @Schema(description = "판매 리워드 원래 금액(계약 기준) — 집계 중(정산 전)은 예정액", nullable = true)
            Long originalRewardAmount,
            @Schema(description = "조정 협의 — 내 최신 제안", nullable = true) Offer myRequest,
            @Schema(description = "조정 협의 — 브랜드 최신 제안", nullable = true) Offer counterProposal,
            @Schema(description = "합의 반영 — 정산 완료 + 합의", nullable = true) Adjustment adjustment,
            @Schema(description = "확정 판매 리워드 — CONFIRMED 에서만", nullable = true) Long finalRewardAmount,
            long fixedFeeAmount,
            @Schema(description = "내 리워드 합계(세전 · 계약 기준) — CONFIRMED 에서만", nullable = true) Long totalRewardAmount,
            Payout payout,
            @Schema(nullable = true) Long settlementId,
            @Schema(nullable = true) String settlementStatusLabel,
            @Schema(nullable = true) Long adjustmentThreadId,
            @Schema(nullable = true) LocalDateTime adjustmentDeadlineAt) {
    }

    public record Offer(long amount, LocalDateTime at) {
    }

    public record Adjustment(long delta, LocalDateTime agreedAt,
                             @Schema(description = "합의된 제안을 낸 쪽 — 「브랜드 제안」 | 「내 요청」") String agreedProposalLabel) {
    }

    @Schema(description = "판매 리워드 실지급 — 정산 행의 creator_payout_amount 그대로(계산하지 않는다)")
    public record Payout(CreatorBusinessType businessType, @Schema(nullable = true) Long amount, String note) {
    }

    @Schema(description = "「이 브랜드와 재협업 제안」 — 브랜드 1:1 스레드로 이동하는 링크 재료")
    public record Recollaboration(Long marketId, @Schema(nullable = true) Long threadId) {
    }
}

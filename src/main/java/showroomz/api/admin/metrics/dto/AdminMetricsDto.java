package showroomz.api.admin.metrics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 어드민 운영 지표(1017 설계서 7절 · ui-admin-21-metrics rev.2). */
public final class AdminMetricsDto {

    private AdminMetricsDto() {
    }

    @Schema(name = "AdminMetrics")
    public record Response(
            @Schema(description = "적용된 프리셋 — 요청에서 생략하면 M1(한 달)", example = "M1") String preset,
            @Schema(example = "CORE") String tab,
            Period period,
            @Schema(description = "비교 기간 — 언제나 직전 같은 길이(M1 은 전월 1일 ~ 같은 일)") Period compare,
            Chart chart,
            @Schema(description = "핵심 탭만 — 다른 탭 대표 지표 4칸(활성 인플루언서 · 최소 물량 달성률 · 팔로워→구매 전환 · 브랜드 귀책 반품률)",
                    nullable = true) List<Row> cards,
            List<Row> rows,
            Breakdown breakdown,
            LocalDateTime asOf
    ) {
    }

    @Schema(name = "AdminMetricsPeriod")
    public record Period(
            @Schema(description = "시작(포함)") LocalDateTime from,
            @Schema(description = "끝(제외) — 오늘 00:00 기준(TODAY 는 지금)") LocalDateTime to,
            @Schema(example = "10.15 ~ 10.21 (7일)") String label
    ) {
    }

    @Schema(name = "AdminMetricsChart")
    public record Chart(
            @Schema(example = "GMV") String metricKey,
            @Schema(example = "공구 거래액") String label,
            @Schema(description = "HOUR · DAY · WEEK · MONTH", example = "DAY") String unit,
            @Schema(example = "8420000") double total,
            @Schema(description = "비교 기간 합계 대비 증감", example = "1250000") double delta,
            List<Point> points,
            @Schema(description = "비교 기간의 같은 지표 — 같은 단위 버킷 · 현재 버킷 수만큼. i 번째가 points[i] 와 짝이다(겹쳐 그리기용). "
                    + "비교 기간이 짧으면 개수가 적을 수 있다")
            List<Point> comparePoints
    ) {
    }

    @Schema(name = "AdminMetricsPoint")
    public record Point(
            @Schema(description = "버킷 시작") LocalDateTime bucket,
            @Schema(example = "10.15") String label,
            double value
    ) {
    }

    @Schema(name = "AdminMetricsRow")
    public record Row(
            @Schema(example = "RETURN_EXCHANGE_RATE") String key,
            @Schema(example = "반품 · 교환율") String label,
            @Schema(description = "지표 이름 아래 보조 설명 한 줄 — 이번 기간 값이 있으면 정의와 함께 적는다(「기간 중 공구 1건 이상 · 신규 1 · "
                    + "재공구 4」 · 「SLA 3영업일 · 초과 0건」 · 「기한 24시간 · 초과 2건」), 없으면 정의 문구만",
                    example = "기간 중 공구 1건 이상 · 신규 1 · 재공구 4") String note,
            @Schema(description = "COUNT · AMOUNT · RATE(%) · RATIO(배) · DAYS · HOURS", example = "RATE") String valueType,
            @Schema(description = "현재 값 — 계산할 수 없으면 null(분모 0)", nullable = true) Double current,
            @Schema(nullable = true) Double previous,
            @Schema(description = "증감 — 비율은 %p", nullable = true) Double delta,
            @Schema(description = "GOOD · BAD · NEUTRAL — 지표 방향(betterWhen)으로 판정", example = "GOOD") String deltaKind,
            @Schema(description = "비율 지표의 분자/분모(「4/6」)", nullable = true) String currentCount,
            @Schema(nullable = true) String previousCount,
            @Schema(description = "값을 나눠 적는 지표의 현재 기간 표시 문구 — 지금은 신규 입점(NEW_ONBOARDING) 「브랜드 N곳 · 인플루언서 M명」"
                    + "을 「1곳 · 3명」으로 내린다. 있으면 값 칸에 `current` 숫자 대신 쓴다(`current` 는 합계)",
                    example = "1곳 · 3명", nullable = true) String currentBreakdown,
            @Schema(description = "`currentBreakdown` 의 비교 기간 값 — 같은 형식", example = "2곳 · 1명", nullable = true)
            String previousBreakdown
    ) {
    }

    @Schema(name = "AdminMetricsBreakdown")
    public record Breakdown(
            @Schema(example = "공구별 거래액 상위 5") String title,
            List<String> columns,
            List<List<String>> rows
    ) {
    }

    @Schema(name = "AdminMetricsTabBadge")
    public record TabBadge(
            @Schema(example = "OPERATION") String tab,
            @Schema(example = "운영") String label,
            @Schema(description = "나빠진 지표 수(`deltaKind = BAD`) — 핵심 탭은 표 줄 + 4칸(같은 지표는 한 번) · 0 이면 배지 없음",
                    example = "2") long worsenedCount
    ) {
    }
}

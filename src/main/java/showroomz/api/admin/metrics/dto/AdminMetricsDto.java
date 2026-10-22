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
            @Schema(example = "D7") String preset,
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
            List<Point> points
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
            @Schema(description = "정의 · 보조 설명(「신규 2 · 재공구 3」 등)") String note,
            @Schema(description = "COUNT · AMOUNT · RATE(%) · RATIO(배) · DAYS · HOURS", example = "RATE") String valueType,
            @Schema(description = "현재 값 — 계산할 수 없으면 null(분모 0)", nullable = true) Double current,
            @Schema(nullable = true) Double previous,
            @Schema(description = "증감 — 비율은 %p", nullable = true) Double delta,
            @Schema(description = "GOOD · BAD · NEUTRAL — 지표 방향(betterWhen)으로 판정", example = "GOOD") String deltaKind,
            @Schema(description = "비율 지표의 분자/분모(「4/6」)", nullable = true) String currentCount,
            @Schema(nullable = true) String previousCount
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
            @Schema(description = "나빠진 지표 수 — 0 이면 배지 없음", example = "2") long worsenedCount
    ) {
    }
}

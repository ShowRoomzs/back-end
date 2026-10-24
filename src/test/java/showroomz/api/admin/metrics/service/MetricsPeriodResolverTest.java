package showroomz.api.admin.metrics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.metrics.type.MetricsPreset;
import showroomz.api.admin.metrics.type.MetricsUnit;
import showroomz.global.error.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영 지표 기간 규칙(1017 설계서 7-0 ② · 7-1) — 기준 시각 오늘 00:00(TODAY 만 지금) · 비교는 직전 같은 길이(M1 은 전월 1일 ~ 같은 일).
 */
@DisplayName("MetricsPeriodResolver — 프리셋 5종 · 비교 기간 · 버킷")
class MetricsPeriodResolverTest {

    private final MetricsPeriodResolver resolver = new MetricsPeriodResolver();
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-22T14:35:20");

    private static LocalDateTime d(String date) {
        return LocalDate.parse(date).atStartOfDay();
    }

    @Nested
    @DisplayName("프리셋")
    class Presets {

        @Test
        @DisplayName("TODAY — 오늘 00:00 ~ 지금 · 시간별 · 비교 = 어제 00:00 ~ 어제 이 시각")
        void today() {
            MetricsPeriodResolver.Resolved r = resolver.resolve(MetricsPreset.TODAY, null, null, NOW);

            assertThat(r.from()).isEqualTo(d("2026-10-22"));
            assertThat(r.to()).isEqualTo(NOW);
            assertThat(r.compareFrom()).isEqualTo(d("2026-10-21"));
            assertThat(r.compareTo()).isEqualTo(NOW.minusDays(1));
            assertThat(r.unit()).isEqualTo(MetricsUnit.HOUR);
        }

        @Test
        @DisplayName("D7 — 7일 전 ~ 어제(오늘 00:00 제외) · 일별 · 비교 = 직전 7일")
        void lastSevenDays() {
            // 기본 프리셋은 56 D7 에서 M1 로 바뀌었다 — D7 은 명시해서 본다
            MetricsPeriodResolver.Resolved r = resolver.resolve(MetricsPreset.D7, null, null, NOW);

            assertThat(r.preset()).isEqualTo(MetricsPreset.D7);
            assertThat(r.from()).isEqualTo(d("2026-10-15"));
            assertThat(r.to()).isEqualTo(d("2026-10-22"));
            assertThat(r.compareFrom()).isEqualTo(d("2026-10-08"));
            assertThat(r.compareTo()).isEqualTo(d("2026-10-15"));
            assertThat(r.unit()).isEqualTo(MetricsUnit.DAY);
            assertThat(r.label()).isEqualTo("10.15 ~ 10.21 (7일)");
            assertThat(r.days()).isEqualTo(7);
        }

        @Test
        @DisplayName("[56 D7] 기본 — 프리셋을 넘기지 않으면 M1(한 달 · 이번 달 1일 ~ 어제 · 비교 = 전월 같은 기간)")
        void thisMonthIsDefault() {
            MetricsPeriodResolver.Resolved r = resolver.resolve(null, null, null, NOW);

            assertThat(r).isEqualTo(resolver.resolve(MetricsPreset.M1, null, null, NOW));
            assertThat(r.preset()).isEqualTo(MetricsPreset.M1);
            assertThat(r.from()).isEqualTo(d("2026-10-01"));
            assertThat(r.to()).isEqualTo(d("2026-10-22"));
            assertThat(r.compareFrom()).isEqualTo(d("2026-09-01"));
            assertThat(r.compareTo()).isEqualTo(d("2026-09-22"));
            assertThat(r.unit()).isEqualTo(MetricsUnit.DAY);
            assertThat(r.label()).isEqualTo("10.01 ~ 10.21 (21일)");
        }

        @Test
        @DisplayName("M1 — 이번 달 1일 ~ 어제 · 비교 = 전월 1일 ~ 같은 일")
        void thisMonth() {
            MetricsPeriodResolver.Resolved r = resolver.resolve(MetricsPreset.M1, null, null, NOW);

            assertThat(r.from()).isEqualTo(d("2026-10-01"));
            assertThat(r.to()).isEqualTo(d("2026-10-22"));
            assertThat(r.compareFrom()).isEqualTo(d("2026-09-01"));
            assertThat(r.compareTo()).isEqualTo(d("2026-09-22"));
        }

        @Test
        @DisplayName("M1 · 1일 — 「1일 ~ 어제」가 비어 있어 지난달 전체를 본다")
        void firstDayOfMonthShowsLastMonth() {
            MetricsPeriodResolver.Resolved r = resolver.resolve(MetricsPreset.M1, null, null,
                    LocalDateTime.parse("2026-11-01T09:00:00"));

            assertThat(r.from()).isEqualTo(d("2026-10-01"));
            assertThat(r.to()).isEqualTo(d("2026-11-01"));
            assertThat(r.compareFrom()).isEqualTo(d("2026-09-01"));
            assertThat(r.compareTo()).isEqualTo(d("2026-10-01"));
        }

        /**
         * 기본(M1)으로 이번 달 1일에 들어오면 「1일 ~ 어제」의 어제가 전월이다 — 빈 기간([1일, 1일)) · 역전 기간이 아니라 지난달 전체를 보고,
         * 비교는 그 전달 전체다. 연 경계(1월 1일) · 짧은 달(3월 1일 — 비교가 2월) · 1일 자정 직후도 같은 규칙이다.
         */
        @Test
        @DisplayName("[56 D7-03] 기본(preset 생략) · 이번 달 1일 — 어제가 전월이어도 빈 기간 · 역전 기간이 아니다(지난달 전체 · 비교 = 그 전달 전체)")
        void defaultOnFirstDayOfMonthIsNotEmpty() {
            record Case(String now, String from, String to, String compareFrom, String compareTo, String label,
                        String compareLabel) {
            }
            List<Case> cases = List.of(
                    new Case("2026-11-01T09:00:00", "2026-10-01", "2026-11-01", "2026-09-01", "2026-10-01",
                            "10.01 ~ 10.31 (31일)", "전월 09.01 ~ 09.30 (30일)"),
                    new Case("2026-11-01T00:00:00", "2026-10-01", "2026-11-01", "2026-09-01", "2026-10-01",
                            "10.01 ~ 10.31 (31일)", "전월 09.01 ~ 09.30 (30일)"),
                    new Case("2027-01-01T08:00:00", "2026-12-01", "2027-01-01", "2026-11-01", "2026-12-01",
                            "12.01 ~ 12.31 (31일)", "전월 11.01 ~ 11.30 (30일)"),
                    new Case("2027-03-01T10:00:00", "2027-02-01", "2027-03-01", "2027-01-01", "2027-02-01",
                            "02.01 ~ 02.28 (28일)", "전월 01.01 ~ 01.31 (31일)"));

            for (Case c : cases) {
                LocalDateTime now = LocalDateTime.parse(c.now());
                MetricsPeriodResolver.Resolved r = resolver.resolve(null, null, null, now);

                assertThat(r).as(c.now()).isEqualTo(resolver.resolve(MetricsPreset.M1, null, null, now));
                assertThat(r.preset()).as(c.now()).isEqualTo(MetricsPreset.M1);
                assertThat(r.from()).as(c.now()).isEqualTo(d(c.from()));
                assertThat(r.to()).as(c.now()).isEqualTo(d(c.to()));
                assertThat(r.compareFrom()).as(c.now()).isEqualTo(d(c.compareFrom()));
                assertThat(r.compareTo()).as(c.now()).isEqualTo(d(c.compareTo()));
                // 빈 기간 · 역전 기간이 아니다 — 현재 · 비교 모두 시작 < 끝, 비교는 현재 앞에서 끝난다
                assertThat(r.from()).as(c.now()).isBefore(r.to());
                assertThat(r.compareFrom()).as(c.now()).isBefore(r.compareTo());
                assertThat(r.compareTo()).as(c.now()).isEqualTo(r.from());
                assertThat(r.to()).as(c.now()).isBeforeOrEqualTo(now);
                assertThat(r.label()).as(c.now()).isEqualTo(c.label());
                assertThat(r.compareLabel()).as(c.now()).isEqualTo(c.compareLabel());
                // 차트 칸도 비지 않는다 — 지난달 일수만큼 일별 칸
                assertThat(MetricsPeriodResolver.buckets(r.from(), r.to(), r.unit())).as(c.now()).hasSize((int) r.days());
            }
        }

        @Test
        @DisplayName("M1 · 3월 31일 — 비교 끝은 2월 말일로 맞춰진다")
        void monthEndClampsCompare() {
            MetricsPeriodResolver.Resolved r = resolver.resolve(MetricsPreset.M1, null, null,
                    LocalDateTime.parse("2027-03-31T09:00:00"));

            assertThat(r.compareFrom()).isEqualTo(d("2027-02-01"));
            assertThat(r.compareTo()).isEqualTo(d("2027-02-28"));
        }

        @Test
        @DisplayName("M6 — 5개월 전 1일 ~ 어제 · 월별 · 비교 = 직전 같은 길이")
        void sixMonths() {
            MetricsPeriodResolver.Resolved r = resolver.resolve(MetricsPreset.M6, null, null, NOW);

            assertThat(r.from()).isEqualTo(d("2026-05-01"));
            assertThat(r.to()).isEqualTo(d("2026-10-22"));
            assertThat(r.unit()).isEqualTo(MetricsUnit.MONTH);
            long days = java.time.temporal.ChronoUnit.DAYS.between(r.from(), r.to());
            assertThat(r.compareTo()).isEqualTo(r.from());
            assertThat(r.compareFrom()).isEqualTo(r.from().minusDays(days));
        }

        @Test
        @DisplayName("CUSTOM — 종료일 포함 · 90일 이하 일별 · 넘으면 월별 · 비교 = 직전 같은 길이")
        void custom() {
            MetricsPeriodResolver.Resolved daily = resolver.resolve(MetricsPreset.CUSTOM,
                    LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), NOW);
            assertThat(daily.from()).isEqualTo(d("2026-09-01"));
            assertThat(daily.to()).isEqualTo(d("2026-10-01"));
            assertThat(daily.compareFrom()).isEqualTo(d("2026-08-02"));
            assertThat(daily.unit()).isEqualTo(MetricsUnit.DAY);

            MetricsPeriodResolver.Resolved monthly = resolver.resolve(MetricsPreset.CUSTOM,
                    LocalDate.parse("2026-05-01"), LocalDate.parse("2026-09-30"), NOW);
            assertThat(monthly.unit()).isEqualTo(MetricsUnit.MONTH);
        }

        @Test
        @DisplayName("CUSTOM 오류 — 날짜 누락 · 역전 · 1년 초과는 400")
        void customInvalid() {
            assertThatThrownBy(() -> resolver.resolve(MetricsPreset.CUSTOM, null, LocalDate.parse("2026-09-30"), NOW))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> resolver.resolve(MetricsPreset.CUSTOM, LocalDate.parse("2026-10-02"),
                    LocalDate.parse("2026-10-01"), NOW)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> resolver.resolve(MetricsPreset.CUSTOM, LocalDate.parse("2025-01-01"),
                    LocalDate.parse("2026-01-02"), NOW)).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("버킷")
    class Buckets {

        @Test
        @DisplayName("시간별 — 00시 ~ 지금 시각의 칸까지 · 마지막 칸은 지금에서 잘린다")
        void hourly() {
            List<LocalDateTime[]> buckets = MetricsPeriodResolver.buckets(d("2026-10-22"), NOW, MetricsUnit.HOUR);

            assertThat(buckets).hasSize(15);
            assertThat(buckets.get(14)[0]).isEqualTo(LocalDateTime.parse("2026-10-22T14:00:00"));
            assertThat(buckets.get(14)[1]).isEqualTo(NOW);
        }

        @Test
        @DisplayName("주별 — 월요일 시작 · 첫 칸은 기간 시작에서 잘린다")
        void weeklyStartsOnMonday() {
            // 2026-10-01 은 목요일
            List<LocalDateTime[]> buckets = MetricsPeriodResolver.buckets(d("2026-10-01"), d("2026-10-22"), MetricsUnit.WEEK);

            assertThat(buckets.get(0)[0]).isEqualTo(d("2026-10-01"));
            assertThat(buckets.get(0)[1]).isEqualTo(d("2026-10-05"));
            assertThat(buckets.get(1)[0].getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
            assertThat(buckets.get(buckets.size() - 1)[1]).isEqualTo(d("2026-10-22"));
        }

        @Test
        @DisplayName("월별 · 일별 칸 수와 라벨")
        void monthlyAndDailyLabels() {
            List<LocalDateTime[]> monthly = MetricsPeriodResolver.buckets(d("2026-05-01"), d("2026-10-22"), MetricsUnit.MONTH);
            assertThat(monthly).hasSize(6);
            assertThat(MetricsPeriodResolver.bucketLabel(monthly.get(0)[0], MetricsUnit.MONTH)).isEqualTo("2026.05");

            List<LocalDateTime[]> daily = MetricsPeriodResolver.buckets(d("2026-10-15"), d("2026-10-22"), MetricsUnit.DAY);
            assertThat(daily).hasSize(7);
            assertThat(MetricsPeriodResolver.bucketLabel(daily.get(0)[0], MetricsUnit.DAY)).isEqualTo("10.15");
            assertThat(MetricsPeriodResolver.bucketLabel(LocalDateTime.parse("2026-10-22T09:00:00"), MetricsUnit.HOUR))
                    .isEqualTo("9시");
        }
    }
}

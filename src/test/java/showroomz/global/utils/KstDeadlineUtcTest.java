package showroomz.global.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.groupbuy.service.AdminSuspensionSchedule;
import showroomz.api.admin.metrics.service.MetricsPeriodResolver;
import showroomz.api.admin.metrics.type.MetricsPreset;
import showroomz.api.admin.metrics.type.MetricsUnit;
import showroomz.domain.member.common.service.SanctionTermPolicy;
import showroomz.domain.notification.service.NotificationSchedule;
import showroomz.domain.order.service.BusinessDayCalculator;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.config.properties.MemberProperties;
import showroomz.global.config.properties.NotificationProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영과 같은 UTC JVM 에서의 한국 달력 계산(58 계획 1-2 ~ 1-7 · 3-6). 로컬 테스트는 JVM 이 KST 라 {@link KstDates} 변환이 항등이어서
 * 「서버 날짜에 23:59:59 를 붙인」 버그가 드러나지 않는다 — 여기서만 기본 시간대를 UTC 로 바꿔 돌린다(끝나면 원복).
 *
 * <p>기대값은 서버 시각(UTC 벽시계)이다 — 한국 2026-10-30 23:59:59 = UTC 2026-10-30 14:59:59.
 */
@DisplayName("UTC JVM — 한국 날짜 기한 · 「오늘」 경계 · 시각 판정")
class KstDeadlineUtcTest {

    private TimeZone original;
    private final BusinessCalendar calendar = new BusinessCalendar(new String[0]);

    @BeforeEach
    void utc() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    /** UTC 벽시계로 쓴 서버 시각. */
    private static LocalDateTime utc(String time) {
        return LocalDateTime.parse(time);
    }

    @Test
    @DisplayName("헬퍼 — KST 00:00 ~ 09:00(UTC 전날)의 「오늘」은 한국 날짜 · 그날 0시 · 끝은 UTC 로 9시간 앞")
    void helpers() {
        LocalDateTime kstEarlyMorning = utc("2026-10-27T23:00:00"); // KST 10-28 08:00
        assertThat(KstDates.today(kstEarlyMorning)).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(KstDates.startOfDay(LocalDate.of(2026, 10, 28))).isEqualTo(utc("2026-10-27T15:00:00"));
        assertThat(KstDates.endOfDay(LocalDate.of(2026, 10, 30))).isEqualTo(utc("2026-10-30T14:59:59"));
        assertThat(KstDates.at(LocalDate.of(2026, 10, 27), LocalTime.of(23, 55))).isEqualTo(utc("2026-10-27T14:55:00"));
        assertThat(KstDates.toKst(utc("2026-10-27T15:00:00"))).isEqualTo(LocalDateTime.parse("2026-10-28T00:00:00"));
    }

    @Test
    @DisplayName("정지 이의 신청 기한 — 10-27(화) 통지 + 3영업일 = 한국 10-30 23:59:59(요청서 1-2 실화면 예 「10.31까지」 → 10.30)")
    void sanctionAppealDeadline() {
        SanctionTermPolicy policy = new SanctionTermPolicy(new MemberProperties(), calendar);

        assertThat(policy.appealDeadline(utc("2026-10-27T03:00:00"))).isEqualTo(utc("2026-10-30T14:59:59"));
        // KST 10-28 08:00 통지(UTC 로는 아직 10-27) — 기산일은 한국 날짜 10-28 → 11-02(월) 끝
        assertThat(policy.appealDeadline(utc("2026-10-27T23:00:00"))).isEqualTo(utc("2026-11-02T14:59:59"));
        assertThat(SanctionTermPolicy.autoReleaseAt(LocalDate.of(2026, 10, 30))).isEqualTo(utc("2026-10-30T15:00:00"));
    }

    @Test
    @DisplayName("클레임 · 발송 영업일 기한 · 달력일 기한 — 한국 날짜 기준 끝")
    void businessDayDue() {
        BusinessDayCalculator calculator = new BusinessDayCalculator(calendar);

        // 금요일(10-30) KST 18:00 기산 + 2영업일 = 화요일(11-03) 한국 23:59:59
        assertThat(calculator.dueAt(utc("2026-10-30T09:00:00"), 2)).isEqualTo(utc("2026-11-03T14:59:59"));
        // 토요일 새벽 KST 07:00(UTC 로는 금요일) — 한국은 토요일이라 다음 영업일(월)을 기산일로 + 2 = 수요일
        assertThat(calculator.dueAt(utc("2026-10-30T22:00:00"), 2)).isEqualTo(utc("2026-11-04T14:59:59"));
        assertThat(BusinessDayCalculator.endOfDayAfter(utc("2026-10-27T23:00:00"), 14))
                .isEqualTo(utc("2026-11-11T14:59:59"));
    }

    @Test
    @DisplayName("직권 중단 소명 기한 하한 — 서버 기본값도 UTC 순간(어드민 FE 가 보내는 값과 같은 기준 · 1-4)")
    void suspensionAppealDeadline() {
        AdminSuspensionSchedule schedule = new AdminSuspensionSchedule(calendar, new GroupBuyProperties());

        assertThat(schedule.minAppealDeadline(LocalDate.of(2026, 10, 27))).isEqualTo(utc("2026-10-30T14:59:59"));
    }

    @Test
    @DisplayName("운영 지표 기간 — 「오늘」은 한국 0시부터 · 일 버킷 경계 · 라벨은 한국 날짜")
    void metricsPeriod() {
        MetricsPeriodResolver resolver = new MetricsPeriodResolver();
        LocalDateTime now = utc("2026-10-27T23:00:00"); // KST 10-28 08:00

        MetricsPeriodResolver.Resolved today = resolver.resolve(MetricsPreset.TODAY, null, null, now);
        assertThat(today.from()).isEqualTo(utc("2026-10-27T15:00:00"));
        assertThat(today.to()).isEqualTo(now);
        assertThat(today.label()).isEqualTo("오늘 00:00 ~ 08:00");

        MetricsPeriodResolver.Resolved week = resolver.resolve(MetricsPreset.D7, null, null, now);
        assertThat(week.from()).isEqualTo(utc("2026-10-20T15:00:00"));
        assertThat(week.to()).isEqualTo(utc("2026-10-27T15:00:00"));
        assertThat(week.label()).isEqualTo("10.21 ~ 10.27 (7일)");

        List<LocalDateTime[]> days = MetricsPeriodResolver.buckets(week.from(), week.to(), MetricsUnit.DAY);
        assertThat(days).hasSize(7);
        assertThat(days.get(0)[0]).isEqualTo(utc("2026-10-20T15:00:00"));
        assertThat(days.get(0)[1]).isEqualTo(utc("2026-10-21T15:00:00"));
        assertThat(MetricsPeriodResolver.bucketLabel(days.get(0)[0], MetricsUnit.DAY)).isEqualTo("10.21");
        assertThat(MetricsPeriodResolver.bucketLabel(utc("2026-10-27T15:00:00"), MetricsUnit.HOUR)).isEqualTo("0시");
    }

    @Test
    @DisplayName("광고성 푸시 야간 — 한국 21 ~ 08시 · 다음 아침은 한국 08:00")
    void quietHours() {
        NotificationSchedule schedule = new NotificationSchedule(new NotificationProperties());

        assertThat(schedule.isQuiet(utc("2026-10-27T13:00:00"))).isTrue();   // KST 22:00
        assertThat(schedule.isQuiet(utc("2026-10-27T03:00:00"))).isFalse();  // KST 12:00
        assertThat(schedule.nextAllowed(utc("2026-10-27T13:00:00"))).isEqualTo(utc("2026-10-27T23:00:00")); // KST 10-28 08:00
        assertThat(schedule.digestSlot(utc("2026-10-27T03:00:00"))).isEqualTo(utc("2026-10-27T11:00:00"));  // KST 20:00
    }
}

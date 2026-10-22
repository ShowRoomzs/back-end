package showroomz.domain.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import showroomz.global.config.properties.NotificationProperties;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 광고성 푸시 발송 시각(1017 설계서 1-6 · C15) — 야간(21:00 ~ 07:59) 미발송 · 다음 아침 08:00 · 하루 묶음 20:00.
 */
@DisplayName("NotificationSchedule — 야간 미발송 · 하루 묶음 시각")
class NotificationScheduleTest {

    private final NotificationSchedule schedule = new NotificationSchedule(new NotificationProperties());

    private static LocalDateTime at(String time) {
        return LocalDateTime.parse("2026-10-22T" + time);
    }

    @Nested
    @DisplayName("야간 판정")
    class Quiet {

        @ParameterizedTest(name = "{0} → 야간 {1}")
        @CsvSource({
                "20:59:59, false",
                "21:00:00, true",
                "23:59:59, true",
                "00:00:00, true",
                "07:59:59, true",
                "08:00:00, false",
                "12:00:00, false"
        })
        @DisplayName("21시 정각부터 08시 직전까지가 야간이다")
        void boundaries(String time, boolean quiet) {
            assertThat(schedule.isQuiet(at(time))).isEqualTo(quiet);
        }
    }

    @Nested
    @DisplayName("다음 발송 가능 시각")
    class NextAllowed {

        @Test
        @DisplayName("낮이면 지금 바로")
        void daytimeIsNow() {
            assertThat(schedule.nextAllowed(at("14:05:00"))).isEqualTo(at("14:05:00"));
        }

        @Test
        @DisplayName("21:30 이면 다음 날 08:00")
        void eveningMovesToNextMorning() {
            assertThat(schedule.nextAllowed(at("21:30:00"))).isEqualTo(LocalDateTime.parse("2026-10-23T08:00:00"));
        }

        @Test
        @DisplayName("새벽 03:00 이면 같은 날 08:00")
        void dawnMovesToSameMorning() {
            assertThat(schedule.nextAllowed(at("03:00:00"))).isEqualTo(at("08:00:00"));
        }
    }

    @Nested
    @DisplayName("하루 묶음 시각")
    class DigestSlot {

        @Test
        @DisplayName("묶음 시각(20시) 전이면 오늘 20:00")
        void beforeSlot() {
            assertThat(schedule.digestSlot(at("10:00:00"))).isEqualTo(at("20:00:00"));
        }

        @Test
        @DisplayName("새벽에 생긴 묶음도 그날 20:00 에 나간다")
        void dawnStillSameDaySlot() {
            assertThat(schedule.digestSlot(at("06:30:00"))).isEqualTo(at("20:00:00"));
        }

        @Test
        @DisplayName("20시가 지났고 야간 전이면 지금")
        void afterSlotBeforeQuiet() {
            assertThat(schedule.digestSlot(at("20:30:00"))).isEqualTo(at("20:30:00"));
        }

        @Test
        @DisplayName("야간에 생기면 다음 날 08:00")
        void quietMovesToNextMorning() {
            assertThat(schedule.digestSlot(at("22:10:00"))).isEqualTo(LocalDateTime.parse("2026-10-23T08:00:00"));
        }
    }
}

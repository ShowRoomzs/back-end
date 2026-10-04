package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("서버 시각 → 한국 날짜 — 계약번호·공구번호의 날짜 부분")
class KstDatesTest {

    /** 서버 벽시계로 읽은 시각 — JVM 시간대가 무엇이든 같은 순간을 가리킨다. */
    private static LocalDateTime serverClockAt(String instant) {
        return LocalDateTime.ofInstant(Instant.parse(instant), ZoneId.systemDefault());
    }

    @Test
    @DisplayName("KST 00:35(UTC 전날 15:35) — 하루 전이 아니라 한국 날짜다")
    void earlyMorningKstIsSameKoreanDay() {
        // 2026.09.28 00:35 KST 검토 요청 → CTR-20260928-NNN 이어야 한다(UTC 날짜면 0927).
        assertThat(KstDates.toKstDate(serverClockAt("2026-09-27T15:35:00Z"))).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    @DisplayName("KST 08:59 / 09:00 경계 — UTC 날짜가 바뀌기 전후 모두 같은 한국 날짜")
    void aroundUtcMidnight() {
        assertThat(KstDates.toKstDate(serverClockAt("2026-09-27T23:59:00Z"))).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(KstDates.toKstDate(serverClockAt("2026-09-28T00:00:00Z"))).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    @DisplayName("KST 23:59(UTC 14:59) — 다음 날로 넘어가지 않는다")
    void lateNightKst() {
        assertThat(KstDates.toKstDate(serverClockAt("2026-09-28T14:59:00Z"))).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(KstDates.toKstDate(serverClockAt("2026-09-28T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 9, 29));
    }
}

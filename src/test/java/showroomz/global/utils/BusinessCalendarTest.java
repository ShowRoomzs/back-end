package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("영업일 달력 — 31 설계 4-4의 시안 기준식")
class BusinessCalendarTest {

    private final BusinessCalendar calendar = new BusinessCalendar(new String[0]);

    @Test
    @DisplayName("B2 — 08.18(화) 제출 · 영업일 3일 → 08.21(금) 예정. 제출일은 세지 않는다")
    void expectedReviewDate() {
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 8, 18), 3)).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    @DisplayName("주말을 건너뛴다 — 08.20(목) + 3 → 08.25(화)")
    void skipsWeekend() {
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 8, 20), 3)).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("등록 마감 — 시작일 08.24(월) · SLA 3 → 08.18(화). 시안 B1의 08.20은 이 식과 맞지 않는다(10-1 #5)")
    void registrationDeadline() {
        assertThat(calendar.latestStartToFinishBefore(LocalDate.of(2026, 8, 24), 3)).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    @DisplayName("32 설계 6-1 — 오픈 승인 SLA와 소명 기한이 같은 셈법이다: 08.16(일)+3 → 08.19 · 08.08(토)+3 → 08.12")
    void adminDeadlinesUseOneRule() {
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 8, 16), 3)).isEqualTo(LocalDate.of(2026, 8, 19));
        // 시안 M6의 08.13은 「토요일 발송 → 월요일 수신」 해석이다. 한 달력에 두 규칙을 두지 않는다(13-1 #6).
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 8, 8), 3)).isEqualTo(LocalDate.of(2026, 8, 12));
    }

    @Test
    @DisplayName("설정된 공휴일도 뺀다")
    void holidays() {
        BusinessCalendar withHoliday = new BusinessCalendar(new String[]{"2026-08-19", " "});
        assertThat(withHoliday.addBusinessDays(LocalDate.of(2026, 8, 18), 3)).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(withHoliday.businessDaysBetween(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 21))).isEqualTo(2);
    }

    @Test
    @DisplayName("집행까지 N영업일 — 끝이 앞이면 0")
    void businessDaysBetween() {
        assertThat(calendar.businessDaysBetween(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 26))).isEqualTo(3);
        assertThat(calendar.businessDaysBetween(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 21))).isZero();
    }
}

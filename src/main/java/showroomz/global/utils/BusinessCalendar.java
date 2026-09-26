package showroomz.global.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 영업일 달력 — 토·일과 설정된 공휴일을 뺀다(31 설계 4-4).
 *
 * <p>공용으로 두는 이유 — 스튜디오의 등록 마감일, 직권 중단 통지의 소명 기한(제17조②④)처럼 영업일을
 * 세는 곳이 여럿이다. 각자 세면 등록 마감과 소명 기한이 서로 다른 달력을 쓴다.
 *
 * <p>공휴일 출처는 미정이다(31 설계 10-2 #6) — 그때까지 {@code showroomz.business-calendar.holidays}에
 * ISO 날짜를 쉼표로 적는다. 비어 있으면 주말만 뺀다.
 */
@Component
public class BusinessCalendar {

    private final Set<LocalDate> holidays;

    public BusinessCalendar(@Value("${showroomz.business-calendar.holidays:}") String[] holidays) {
        this.holidays = Arrays.stream(holidays)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    /** {@code from} 다음 영업일부터 N번째 영업일 — 기준일은 세지 않는다(B2 「08.18 제출 · 영업일 3일 · 08.21 예정」). */
    public LocalDate addBusinessDays(LocalDate from, int days) {
        LocalDate date = from;
        int counted = 0;
        while (counted < days) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                counted++;
            }
        }
        return date;
    }

    /**
     * {@code addBusinessDays(D, days) < target}을 만족하는 가장 늦은 D — 「D에 시작하면 target 전날까지 끝난다」.
     * 스튜디오 등록 마감일(31 설계 4-4): 시작일 08.24(월) · 심사 영업일 3 → 08.18(화).
     */
    public LocalDate latestStartToFinishBefore(LocalDate target, int days) {
        LocalDate candidate = target.minusDays(1);
        while (!addBusinessDays(candidate, days).isBefore(target)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    /** {@code from} 다음 날부터 {@code to}까지 영업일 수 — {@code to}가 앞이면 0(B13 「집행까지 3영업일」). */
    public int businessDaysBetween(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate date = from.plusDays(1); !date.isAfter(to); date = date.plusDays(1)) {
            if (isBusinessDay(date)) {
                count++;
            }
        }
        return count;
    }
}

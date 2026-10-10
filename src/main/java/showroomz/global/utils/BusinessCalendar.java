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
 * <p>공휴일 출처 — {@code business_holiday} 테이블(1009 기획 수정본 Q1 · 어드민이 관리)을
 * {@code BusinessHolidayLoader}가 주기적으로 {@link #replaceRegisteredHolidays}로 넣는다. yml 의
 * {@code showroomz.business-calendar.holidays}(ISO 날짜 쉼표 구분)는 비상용으로 함께 더해진다. 둘 다 비면 주말만 뺀다.
 * 영업일 = 주말 · 공휴일 제외 — 발송 기한 · 취소 요청 응답 기한 · 검수 기한이 모두 같은 기준이다.
 */
@Component
public class BusinessCalendar {

    private final Set<LocalDate> configuredHolidays;
    /** 테이블에서 읽은 공휴일 — 통째로 갈아 끼운다(읽는 쪽은 잠금 없이 최신 집합을 본다). */
    private volatile Set<LocalDate> registeredHolidays = Set.of();

    public BusinessCalendar(@Value("${showroomz.business-calendar.holidays:}") String[] holidays) {
        this.configuredHolidays = Arrays.stream(holidays)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 등록 공휴일 교체 — {@code BusinessHolidayLoader}와 어드민 공휴일 변경이 부른다. */
    public void replaceRegisteredHolidays(Set<LocalDate> holidays) {
        this.registeredHolidays = Set.copyOf(holidays);
    }

    /** 설정된 공휴일인가 — 주말은 보지 않는다. 토요일에도 움직이는 택배 배송일 계산이 쓴다. */
    public boolean isHoliday(LocalDate date) {
        return configuredHolidays.contains(date) || registeredHolidays.contains(date);
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !isHoliday(date);
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

    /**
     * {@code date} 직전 영업일 — 하루씩 물러나 영업일인 첫 날. 정산 조정 협의의 D-1 통지일(44 이슈 스레드 설계서 1-6)이 쓴다 —
     * {@link #latestStartToFinishBefore}와 뜻이 다르다(그쪽은 「D에 시작하면 target 전날까지 끝나는 D」).
     */
    public LocalDate previousBusinessDay(LocalDate date) {
        LocalDate candidate = date.minusDays(1);
        while (!isBusinessDay(candidate)) {
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

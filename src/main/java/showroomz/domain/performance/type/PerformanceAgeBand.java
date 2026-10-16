package showroomz.domain.performance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;

/**
 * 성과 관리 연령대(48 파트너 설계서 2-5) — 시안 축(10대 ~ 50대+) 그대로다. 쇼룸 관리의 18–24세 구간과 다르다(11절 #8 · §55 D-10).
 * 나이는 <b>공구 시작일 기준 만 나이</b> — 해가 바뀌어도 지난 공구의 분포가 움직이지 않게 한다.
 */
@Getter
@RequiredArgsConstructor
public enum PerformanceAgeBand {

    TEENS("10대"),
    TWENTIES("20대"),
    THIRTIES("30대"),
    FORTIES("40대"),
    FIFTIES_PLUS("50대+"),
    UNKNOWN("미확인");

    private final String label;

    public static PerformanceAgeBand of(String birthday, LocalDate asOf) {
        if (birthday == null || birthday.isBlank()) {
            return UNKNOWN;
        }
        try {
            int age = Period.between(LocalDate.parse(birthday.trim()), asOf).getYears();
            if (age < 20) {
                return TEENS;
            }
            if (age < 30) {
                return TWENTIES;
            }
            if (age < 40) {
                return THIRTIES;
            }
            if (age < 50) {
                return FORTIES;
            }
            return FIFTIES_PLUS;
        } catch (DateTimeParseException e) {
            return UNKNOWN;
        }
    }
}

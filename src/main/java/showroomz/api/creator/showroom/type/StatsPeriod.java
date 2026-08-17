package showroomz.api.creator.showroom.type;

import java.time.LocalDateTime;
import java.time.Period;

/**
 * §22-4 쇼룸 현황 기간 — 화면 전체에 하나로 적용된다(카드별 기간 선택은 두지 않는다).
 * 기본값은 30일이며, 증감률은 언제나 <b>직전 동일 기간</b>과 비교한다.
 */
public enum StatsPeriod {

    DAYS_7("최근 7일", Period.ofDays(7)),
    DAYS_14("최근 14일", Period.ofDays(14)),
    DAYS_30("최근 30일", Period.ofDays(30)),
    DAYS_60("최근 60일", Period.ofDays(60)),
    DAYS_90("최근 90일", Period.ofDays(90)),
    MONTHS_6("최근 6개월", Period.ofMonths(6)),
    YEAR_1("최근 1년", Period.ofYears(1));

    public static final StatsPeriod DEFAULT = DAYS_30;

    private final String label;
    private final Period period;

    StatsPeriod(String label, Period period) {
        this.label = label;
        this.period = period;
    }

    public String getLabel() {
        return label;
    }

    /** 기간 시작 시각 — 종료 시각(보통 지금)에서 기간만큼 거슬러 올라간다. */
    public LocalDateTime startOf(LocalDateTime end) {
        return end.minus(period);
    }

    /** 직전 동일 기간의 시작 시각 — [previousStart, start) 구간이 비교 대상이다. */
    public LocalDateTime previousStartOf(LocalDateTime end) {
        return startOf(end).minus(period);
    }
}

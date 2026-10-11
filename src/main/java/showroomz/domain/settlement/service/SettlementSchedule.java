package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.calendar.entity.BusinessHoliday;
import showroomz.domain.calendar.repository.BusinessHolidayRepository;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 정산 날짜 규칙(44 어드민 설계서 0-10) — 지급 예정일 = 확정일 + N영업일 · 「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」 같은
 * 근거 문장. 문장은 서버가 만든다 — 파트너 {@code payoutBasis} · 스튜디오 {@code payoutDueNote} · 07b 이력이 같은 문장을 쓴다.
 * 영업일은 {@link BusinessCalendar} 하나로 센다.
 */
@Component
@RequiredArgsConstructor
public class SettlementSchedule {

    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");

    private final BusinessCalendar calendar;
    private final BusinessHolidayRepository holidayRepository;
    private final SettlementProperties properties;

    /** 지급 예정일 — 확정일(또는 증빙 확인일) + N영업일. 공휴일 · 주말이 되는 일은 없다(07a ⓔ 해소). */
    public LocalDate payoutDueDate(LocalDate base) {
        return calendar.addBusinessDays(base, properties.getPayoutBusinessDays());
    }

    /** 확정 근거 — 「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」. 그 사이 평일 공휴일이 없으면 뒤 꼬리가 없다. */
    public String payoutBasis(SettlementConfirmReason reason, LocalDate confirmedDate, LocalDate dueDate) {
        return basis(reason.getLabel(), confirmedDate, dueDate);
    }

    /** 「{앞말} {MM.DD} + N영업일 · {MM.DD 공휴일} 제외」. */
    public String basis(String prefix, LocalDate base, LocalDate dueDate) {
        StringBuilder text = new StringBuilder("%s %s + %d영업일".formatted(prefix, base.format(DAY),
                properties.getPayoutBusinessDays()));
        List<String> excluded = excludedHolidays(base, dueDate);
        if (!excluded.isEmpty()) {
            text.append(" · ").append(String.join(" · ", excluded)).append(" 제외");
        }
        return text.toString();
    }

    /** {@code (base, due]} 사이 평일 공휴일 — 「10.09 한글날」. 이름은 공휴일 테이블에서(설정 공휴일은 「공휴일」). */
    private List<String> excludedHolidays(LocalDate base, LocalDate due) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = base.plusDays(1); !date.isAfter(due); date = date.plusDays(1)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && calendar.isHoliday(date)) {
                dates.add(date);
            }
        }
        if (dates.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, String> names = holidayRepository.findAllById(dates).stream()
                .collect(Collectors.toMap(BusinessHoliday::getDate, BusinessHoliday::getName, (a, b) -> a));
        return dates.stream().map(date -> date.format(DAY) + " " + names.getOrDefault(date, "공휴일")).toList();
    }
}

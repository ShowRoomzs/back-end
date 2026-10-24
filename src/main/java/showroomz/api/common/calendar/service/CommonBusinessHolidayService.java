package showroomz.api.common.calendar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.calendar.dto.HolidayItem;
import showroomz.domain.calendar.entity.BusinessHoliday;
import showroomz.domain.calendar.repository.BusinessHolidayRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 공휴일 공개 조회(1017 설계서 4-5) — 파트너 · 스튜디오 홈 달력의 「주말 · 공휴일 회색」. 판정은 영업일 계산과 같은 {@link BusinessCalendar}
 * (등록 테이블 ∪ 설정값)이 하고, 이름은 테이블에서 붙인다 — 달력과 발송 기한 계산이 서로 다른 공휴일을 보면 안 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonBusinessHolidayService {

    /** 한 번에 내려 주는 최대 기간 — 월 달력은 앞뒤 주까지 6주면 되고, 연 단위 조회까지 받는다. */
    static final int MAX_RANGE_DAYS = 400;
    static final String DEFAULT_NAME = "공휴일";

    private final BusinessCalendar businessCalendar;
    private final BusinessHolidayRepository holidayRepository;

    public List<HolidayItem> getHolidays(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "조회 기간을 확인해 주세요(최대 400일).");
        }
        Map<LocalDate, String> names = holidayRepository.findBetween(from, to).stream()
                .collect(Collectors.toMap(BusinessHoliday::getDate, BusinessHoliday::getName, (a, b) -> a));
        List<HolidayItem> items = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (businessCalendar.isHoliday(date)) {
                items.add(new HolidayItem(date, names.getOrDefault(date, DEFAULT_NAME)));
            }
        }
        return items;
    }
}

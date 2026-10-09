package showroomz.domain.calendar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.calendar.repository.BusinessHolidayRepository;
import showroomz.global.utils.BusinessCalendar;

import java.util.HashSet;

/**
 * {@code business_holiday} → {@link BusinessCalendar} 반영. 달력은 영업일 판정마다 DB 를 읽지 않는다 — 기동 시 한 번,
 * 이후 매시 정각, 그리고 어드민이 공휴일을 바꾼 직후에 통째로 갈아 끼운다. 여러 인스턴스가 떠 있어도 1시간 안에 맞춰진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessHolidayLoader {

    private final BusinessHolidayRepository holidayRepository;
    private final BusinessCalendar businessCalendar;

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refresh();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void refresh() {
        try {
            businessCalendar.replaceRegisteredHolidays(new HashSet<>(holidayRepository.findAllDates()));
        } catch (RuntimeException e) {
            // 읽기 실패로 달력을 비우지 않는다 — 직전 값을 그대로 쓴다.
            log.warn("공휴일 적재 실패 — 직전 달력을 유지합니다: {}", e.getMessage());
        }
    }
}

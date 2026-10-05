package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 클레임 기한 계산(35 설계서 1-7) — 기산 시각이 비영업일이면 <b>다음 영업일을 기산일로</b> 보고 + N영업일, 그 날의 끝
 * (23:59:59)이 기한이다. 금요일 18시 신청 + 2영업일 = 다음 화요일 끝 · 토요일 입고 확인 + 2영업일 = 수요일 끝.
 *
 * <p>영업일 판정은 공용 {@link BusinessCalendar}를 쓴다(주말 + 설정된 공휴일). 규칙은 잠정이다(§35-9 #10) —
 * 기한은 발급 시점에 계산해 저장하므로, 규칙이 바뀌어도 이미 발급된 기한은 움직이지 않는다.
 */
@Component
@RequiredArgsConstructor
public class BusinessDayCalculator {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final BusinessCalendar businessCalendar;

    public LocalDateTime dueAt(LocalDateTime from, int businessDays) {
        LocalDate base = from.toLocalDate();
        while (!businessCalendar.isBusinessDay(base)) {
            base = base.plusDays(1);
        }
        return businessCalendar.addBusinessDays(base, businessDays).atTime(END_OF_DAY);
    }

    /** 달력일 기한 — {@code from}의 날짜 + N일의 끝. 회수 송장 등록 기한 · 재발송비 결제 기한이 쓴다. */
    public static LocalDateTime endOfDayAfter(LocalDateTime from, int days) {
        return from.toLocalDate().plusDays(days).atTime(END_OF_DAY);
    }
}

package showroomz.api.admin.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDto;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 직권 중단 사전 통지의 날짜 규칙(32 설계 6-1). 날짜 칩(M6)과 통지 검증이 <b>같은 식</b>을 쓴다 — 칩은 편의이고
 * 규칙은 POST가 집행한다. 영업일 계산은 {@link BusinessCalendar} 하나다(기준일 불산입).
 *
 * <pre>
 * 소명 기한 하한   addBusinessDays(통지일, appeal-business-days) 23:59:59        제17조④
 * 집행 예정 하한   날짜 ≥ addBusinessDays(통지일, notice-business-days)            제17조②
 *                 ∧ 소명 기한 + min-gap-after-appeal 보다 엄격히 뒤
 * 집행 예정 상한   end_at 보다 앞                                                 30 설계 7-2 #3
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AdminSuspensionSchedule {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final BusinessCalendar businessCalendar;
    private final GroupBuyProperties properties;

    public LocalDateTime minAppealDeadline(LocalDate noticeDate) {
        return businessCalendar.addBusinessDays(noticeDate, properties.getSuspension().getAppealBusinessDays())
                .atTime(END_OF_DAY);
    }

    public LocalDate minExecutionDate(LocalDate noticeDate) {
        return businessCalendar.addBusinessDays(noticeDate, properties.getSuspension().getNoticeBusinessDays());
    }

    /** 통지 요청값 검증 — 하나라도 어기면 false. */
    public boolean isValid(GroupBuy groupBuy, LocalDateTime appealDeadlineAt, LocalDateTime executeScheduledAt,
                           LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        return !appealDeadlineAt.isBefore(minAppealDeadline(today))
                && !executeScheduledAt.toLocalDate().isBefore(minExecutionDate(today))
                && executeScheduledAt.isAfter(appealDeadlineAt.plus(properties.getSuspension().getMinGapAfterAppeal()))
                && executeScheduledAt.isBefore(groupBuy.getEndAt());
    }

    /**
     * 기본 소명 기한으로 유효한 집행 시각이 있는가. 칩은 종료 전날까지만 나열하지만, 직접 입력은 종료일의
     * {@code end_at} 이전도 허용한다. 버튼 판정은 POST의 검증 범위와 같아야 한다.
     */
    public boolean hasWindow(GroupBuy groupBuy, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDateTime afterAppeal = minAppealDeadline(today)
                .plus(properties.getSuspension().getMinGapAfterAppeal())
                .plusNanos(1);
        LocalDateTime firstNoticeDate = minExecutionDate(today).atStartOfDay();
        LocalDateTime earliest = afterAppeal.isAfter(firstNoticeDate) ? afterAppeal : firstNoticeDate;
        return earliest.isBefore(groupBuy.getEndAt());
    }

    /**
     * M6 칩 — 통지 다음 영업일부터 종료 전날까지 영업일만. 「3영업일 이전 날짜를 회색 취소선으로 잠근다」 ·
     * 「주말이 영업일에서 빠지는 것이 칩에 그대로 보인다」(§32-3).
     *
     * <p>칩은 하루 단위라 보수적으로 판정한다 — 최소 간격이 날짜 중간에 걸치면 그날은 잠근다.
     */
    public List<AdminGroupBuyDto.ExecutionDate> executionDates(GroupBuy groupBuy, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate minDate = minExecutionDate(today);
        LocalDateTime earliestExecution = minAppealDeadline(today)
                .plus(properties.getSuspension().getMinGapAfterAppeal());
        LocalDate lastDate = groupBuy.getEndAt().toLocalDate().minusDays(1);

        List<AdminGroupBuyDto.ExecutionDate> dates = new ArrayList<>();
        for (LocalDate date = today.plusDays(1); !date.isAfter(lastDate); date = date.plusDays(1)) {
            if (!businessCalendar.isBusinessDay(date)) {
                continue;
            }
            boolean selectable = !date.isBefore(minDate) && date.isAfter(earliestExecution.toLocalDate());
            dates.add(new AdminGroupBuyDto.ExecutionDate(date, selectable));
        }
        return dates;
    }
}

package showroomz.api.admin.calendar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import showroomz.api.admin.calendar.dto.BusinessHolidayDto;
import showroomz.domain.calendar.entity.BusinessHoliday;
import showroomz.domain.calendar.repository.BusinessHolidayRepository;
import showroomz.domain.calendar.service.BusinessHolidayLoader;

import java.time.LocalDate;
import java.util.List;

/**
 * 공휴일 관리 — 바꾼 값은 커밋 직후 이 인스턴스의 달력에 바로 반영된다(다른 인스턴스는 매시 적재). 이미 발급된 기한
 * (발송 기한 · 취소 응답 기한 · 검수 기한)은 스냅샷이라 소급하지 않는다 — 이후 계산부터 적용된다.
 */
@Service
@RequiredArgsConstructor
public class AdminBusinessHolidayService {

    private final BusinessHolidayRepository holidayRepository;
    private final BusinessHolidayLoader holidayLoader;

    @Transactional(readOnly = true)
    public List<BusinessHolidayDto.Item> getHolidays(int year) {
        return holidayRepository.findBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .map(BusinessHolidayDto.Item::of)
                .toList();
    }

    @Transactional
    public BusinessHolidayDto.Item upsert(LocalDate date, String name, Long operatorId) {
        BusinessHoliday holiday = holidayRepository.findById(date)
                .map(found -> {
                    found.rename(name.trim());
                    return found;
                })
                .orElseGet(() -> holidayRepository.save(new BusinessHoliday(date, name.trim(), operatorId)));
        refreshAfterCommit();
        return BusinessHolidayDto.Item.of(holiday);
    }

    @Transactional
    public void delete(LocalDate date) {
        holidayRepository.deleteById(date);
        refreshAfterCommit();
    }

    private void refreshAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                holidayLoader.refresh();
            }
        });
    }
}

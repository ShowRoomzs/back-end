package showroomz.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.type.StoragePhase;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 클레임 기한·보관 기한 계산(35 설계서 1-7 · 1-9 · 6절 #14 · #20) — DB 없이 돈다. */
@DisplayName("클레임 기한 · 보관 기한 계산")
class ClaimPolicyTest {

    private final BusinessDayCalculator calculator = new BusinessDayCalculator(new BusinessCalendar(new String[0]));
    private final ClaimStoragePolicy storagePolicy = new ClaimStoragePolicy(new OrderProperties());

    @Test
    @DisplayName("금요일 18시 신청 + 2영업일 = 다음 화요일의 끝(#14)")
    void fridayRequest() {
        // 2026-08-21 은 금요일이다.
        assertThat(calculator.dueAt(LocalDateTime.of(2026, 8, 21, 18, 0), 2))
                .isEqualTo(LocalDateTime.of(2026, 8, 25, 23, 59, 59));
    }

    @Test
    @DisplayName("토요일 입고 확인 + 2영업일 = 수요일의 끝 — 비영업일은 다음 영업일을 기산일로 본다(#14 · 시안 B1)")
    void saturdayReceive() {
        assertThat(calculator.dueAt(LocalDateTime.of(2026, 8, 22, 10, 0), 2))
                .isEqualTo(LocalDateTime.of(2026, 8, 26, 23, 59, 59));
    }

    @Test
    @DisplayName("공휴일은 영업일에서 빠진다")
    void holidaySkipped() {
        BusinessDayCalculator withHoliday = new BusinessDayCalculator(new BusinessCalendar(new String[]{"2026-08-24"}));

        assertThat(withHoliday.dueAt(LocalDateTime.of(2026, 8, 21, 18, 0), 2))
                .isEqualTo(LocalDateTime.of(2026, 8, 26, 23, 59, 59));
    }

    @Test
    @DisplayName("달력일 기한 — 접수일 + 7일의 끝")
    void calendarDue() {
        assertThat(BusinessDayCalculator.endOfDayAfter(LocalDateTime.of(2026, 10, 5, 9, 30), 7))
                .isEqualTo(LocalDateTime.of(2026, 10, 12, 23, 59, 59));
    }

    @Test
    @DisplayName("보관 기한 — 고지 2회부터 생기고 최종 고지일 + 3개월의 끝, 고지가 더해지면 밀린다(#20)")
    void storageDue() {
        LocalDateTime second = LocalDateTime.of(2026, 8, 23, 14, 0);
        LocalDateTime third = LocalDateTime.of(2026, 9, 10, 9, 0);

        assertThat(storagePolicy.storageDueAt(0, null)).isNull();
        assertThat(storagePolicy.storageDueAt(1, second.minusDays(7))).isNull();
        assertThat(storagePolicy.storageDueAt(2, second)).isEqualTo(LocalDateTime.of(2026, 11, 23, 23, 59, 59));
        assertThat(storagePolicy.storageDueAt(3, third)).isEqualTo(LocalDateTime.of(2026, 12, 10, 23, 59, 59));
    }

    @Test
    @DisplayName("보관 단계 — 고지 부족 · 보관 중 · 기한 경과(#20)")
    void storagePhase() {
        LocalDateTime second = LocalDateTime.of(2026, 8, 23, 14, 0);

        assertThat(storagePolicy.phase(1, second, second.plusDays(1))).isEqualTo(StoragePhase.NOTICE_PENDING);
        assertThat(storagePolicy.phase(2, second, LocalDateTime.of(2026, 11, 23, 23, 0)))
                .isEqualTo(StoragePhase.STORING);
        assertThat(storagePolicy.phase(2, second, LocalDateTime.of(2026, 11, 24, 0, 0)))
                .isEqualTo(StoragePhase.EXPIRED);
    }
}

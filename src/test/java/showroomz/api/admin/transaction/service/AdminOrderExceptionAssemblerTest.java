package showroomz.api.admin.transaction.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 06d 「자동 알림 대기 · N회차 MM.DD」의 다음 회차(40 설계서 3-2) — 배치 시각표(영업일 10 · 15시 · 같은 영업일 1회)를 그대로 따르는지
 * 시각을 고정해 본다. 통합 테스트는 지금 시각에 묶여 시각표 조합을 다 못 만든다.
 *
 * <p>달력: 2026-09-17 목 · 18 금 · 19 토 · 21 월 · 24 목(공휴일로 둔다).
 */
class AdminOrderExceptionAssemblerTest {

    private final AdminOrderExceptionAssembler assembler = new AdminOrderExceptionAssembler(
            new BusinessCalendar(new String[]{"2026-09-24"}), null, null, null, null);

    @ParameterizedTest(name = "[{index}] 마지막 {0} · 지금 {1} → {2} — {3}")
    @CsvSource(nullValues = "-", value = {
            "-,                   2026-09-17T09:00:00, 2026-09-17T10:00:00, 아직 0회 · 오늘 10시 전",
            "-,                   2026-09-17T10:00:00, 2026-09-17T15:00:00, 10시 정각은 지난 회차",
            "-,                   2026-09-17T11:30:00, 2026-09-17T15:00:00, 오늘 15시 회차가 남았다",
            "-,                   2026-09-17T16:00:00, 2026-09-18T10:00:00, 오늘 회차가 끝났다",
            "2026-09-17T10:00:00, 2026-09-17T11:30:00, 2026-09-18T10:00:00, 오늘 이미 알렸다 → 다음 영업일",
            "2026-09-18T10:00:00, 2026-09-18T11:00:00, 2026-09-21T10:00:00, 금요일 알림 → 월요일",
            "2026-09-16T15:00:00, 2026-09-17T11:30:00, 2026-09-17T15:00:00, 어제 알림 · 오늘 회차가 남았다",
            "-,                   2026-09-19T11:00:00, 2026-09-21T10:00:00, 토요일 → 다음 영업일",
            "2026-09-23T10:00:00, 2026-09-24T11:30:00, 2026-09-25T10:00:00, 공휴일에는 회차가 없다",
    })
    void nextNoticeAtFollowsBatchSchedule(LocalDateTime lastNoticeAt, LocalDateTime now, LocalDateTime expected,
                                          String scenario) {
        assertThat(assembler.nextNoticeAt(lastNoticeAt, now)).as(scenario).isEqualTo(expected);
    }
}

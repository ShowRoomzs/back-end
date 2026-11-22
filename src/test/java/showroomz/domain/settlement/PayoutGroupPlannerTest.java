package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.service.PayoutGroupPlanner;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지급일 계산(60 이니시스 전환 계획서 D-2 · 9-1) — 이니시스(리드 1영업일 · 마감 15:00)와 시뮬레이터(리드 0 · 마감 없음). 날짜 계산은 PG 를 부르지 않는
 * 순수 계산이라 리포지토리 없이 본다.
 */
@DisplayName("지급일 계산 — 금요일 10:00 → 월요일 · 공휴일 건너뜀 · 15:00 뒤 → 다다음 영업일 · 토요일은 지시 창 밖 · 수정 마감")
class PayoutGroupPlannerTest {

    /** 2026-11-20 금 · 11-23 월 · 11-24 화(공휴일로 둔다) · 11-25 수. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 24);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 25);

    private final BusinessCalendar calendar = new BusinessCalendar(new String[]{TUESDAY.toString()});
    private final PayoutGroupPlanner inicis = new PayoutGroupPlanner(null, null, new InicisLike(), calendar);
    private final PayoutGroupPlanner simulated = new PayoutGroupPlanner(null, null, new SettlementPayoutGateway() {
        @Override
        public boolean groupsByPayee() {
            return true;
        }
    }, calendar);

    @Test
    @DisplayName("금요일 10:00 → 월요일 · 금요일 15:00 뒤 → 다다음 영업일 · 공휴일(화요일) 건너뜀 · 토요일 → 다음 영업일 기준 · 시뮬레이터는 오늘")
    void earliestPayDate() {
        assertThat(inicis.earliestPayDate(FRIDAY.atTime(10, 0))).isEqualTo(MONDAY);
        assertThat(inicis.earliestPayDate(FRIDAY.atTime(14, 59))).isEqualTo(MONDAY);
        // 금요일 15:00 뒤 — 업로드일이 월요일로 밀리고 지급일은 그 다음 영업일(화요일 공휴일 → 수요일).
        assertThat(inicis.earliestPayDate(FRIDAY.atTime(15, 0))).isEqualTo(WEDNESDAY);
        // 월요일 10:00 → 화요일이 공휴일이라 수요일.
        assertThat(inicis.earliestPayDate(MONDAY.atTime(10, 0))).isEqualTo(WEDNESDAY);
        // 토요일 — 다음 영업일(월요일)이 업로드일 → 지급일 수요일.
        assertThat(inicis.earliestPayDate(LocalDate.of(2026, 11, 21).atTime(10, 0))).isEqualTo(WEDNESDAY);
        // 시뮬레이터 — 오늘(예정일 당일 지급 그대로).
        assertThat(simulated.earliestPayDate(FRIDAY.atTime(16, 0))).isEqualTo(FRIDAY);
    }

    @Test
    @DisplayName("지시 창 — 영업일 15:00 전만(재분배 즉시 전송) · 토요일 · 15:00 뒤는 SCHEDULED 로만 · 시뮬레이터는 언제나")
    void uploadWindow() {
        assertThat(inicis.isUploadWindow(FRIDAY.atTime(14, 0))).isTrue();
        assertThat(inicis.isUploadWindow(FRIDAY.atTime(15, 0))).isFalse();
        assertThat(inicis.isUploadWindow(LocalDate.of(2026, 11, 21).atTime(10, 0))).isFalse();
        assertThat(inicis.isUploadWindow(TUESDAY.atTime(10, 0))).isFalse();   // 공휴일
        assertThat(simulated.isUploadWindow(LocalDate.of(2026, 11, 21).atTime(23, 0))).isTrue();
    }

    @Test
    @DisplayName("수정 마감 — 지급일 D 묶음은 전 영업일 15:00 까지(월요일 묶음 → 금요일 15:00 · 수요일 묶음 → 공휴일 건너 월요일 15:00) · 시뮬레이터는 마감 없음")
    void deadline() {
        assertThat(inicis.deadline(MONDAY)).contains(LocalDateTime.of(FRIDAY, LocalTime.of(15, 0)));
        assertThat(inicis.deadline(WEDNESDAY)).contains(LocalDateTime.of(MONDAY, LocalTime.of(15, 0)));
        assertThat(inicis.isEditable(MONDAY, FRIDAY.atTime(14, 59))).isTrue();
        assertThat(inicis.isEditable(MONDAY, FRIDAY.atTime(15, 0))).isFalse();
        assertThat(simulated.deadline(MONDAY)).isEmpty();
        assertThat(simulated.isEditable(MONDAY, FRIDAY.atTime(9, 0))).isFalse();
        assertThat(inicis.hasCutoff()).isTrue();
        assertThat(simulated.hasCutoff()).isFalse();
    }

    /** 이니시스와 같은 날짜 규칙 — 리드 1 · 마감 15:00. */
    private static final class InicisLike implements SettlementPayoutGateway {
        @Override
        public boolean groupsByPayee() {
            return true;
        }

        @Override
        public int leadBusinessDays() {
            return 1;
        }

        @Override
        public Optional<LocalTime> uploadCutoff() {
            return Optional.of(LocalTime.of(15, 0));
        }
    }
}

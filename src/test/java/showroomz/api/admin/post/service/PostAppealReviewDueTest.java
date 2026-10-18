package showroomz.api.admin.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 이의 신청 심사 기한 — 접수 + N영업일(주말 · 공휴일 제외 · 접수 시각 유지) (54 테스트 시나리오 17절 BD) */
class PostAppealReviewDueTest {

    private final BusinessCalendar calendar = new BusinessCalendar(new String[0]);
    private final PostProperties properties = new PostProperties();
    private final PostAppealReviewDue reviewDue = new PostAppealReviewDue(calendar, properties);

    @Test
    @DisplayName("[BD-01 · BD-06] 월요일 접수 + 3영업일 = 목요일 · 접수 시각을 그대로 둔다")
    void weekday() {
        assertThat(reviewDue.of(LocalDateTime.of(2026, 10, 12, 9, 12, 34)))
                .isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 12, 34));
    }

    @Test
    @DisplayName("[BD-02 · BD-03] 금요일 · 토요일 접수는 주말을 건너 수요일")
    void weekend() {
        assertThat(reviewDue.of(LocalDateTime.of(2026, 10, 16, 15, 0))).isEqualTo(LocalDateTime.of(2026, 10, 21, 15, 0));
        assertThat(reviewDue.of(LocalDateTime.of(2026, 10, 17, 10, 0))).isEqualTo(LocalDateTime.of(2026, 10, 21, 10, 0));
    }

    @Test
    @DisplayName("[BD-04] 등록 공휴일(10-09 한글날)도 건너뛴다 — 목요일 접수 → 다음 주 수요일")
    void holiday() {
        calendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 10, 9)));

        assertThat(reviewDue.of(LocalDateTime.of(2026, 10, 8, 9, 0))).isEqualTo(LocalDateTime.of(2026, 10, 14, 9, 0));
    }

    @Test
    @DisplayName("[BD-05] 영업일 수는 설정값을 따른다 — 5영업일이면 월요일 → 다음 주 월요일")
    void configurable() {
        properties.setAppealReviewBusinessDays(5);

        assertThat(reviewDue.of(LocalDateTime.of(2026, 10, 12, 9, 12))).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 12));
    }

    @Test
    @DisplayName("접수 시각이 없으면 기한도 없다")
    void nullSubmittedAt() {
        assertThat(reviewDue.of(null)).isNull();
    }
}

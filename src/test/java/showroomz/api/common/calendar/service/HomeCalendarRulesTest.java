package showroomz.api.common.calendar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.error.exception.BusinessException;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 파트너 · 스튜디오 홈 달력 공통 규칙(1017 설계서 4-3 · 5-3) — 월 파싱 · 막대 3색. */
@DisplayName("HomeCalendarRules — 월 파싱 · 막대 상태")
class HomeCalendarRulesTest {

    @Test
    @DisplayName("월 — yyyy-MM · 앞뒤 공백 허용 · 생략하면 이번 달 · 형식 오류는 400")
    void parseMonth() {
        assertThat(HomeCalendarRules.parseMonth("2026-02")).isEqualTo(YearMonth.of(2026, 2));
        assertThat(HomeCalendarRules.parseMonth(" 2026-10 ")).isEqualTo(YearMonth.of(2026, 10));
        assertThat(HomeCalendarRules.parseMonth(null)).isEqualTo(YearMonth.now());
        assertThat(HomeCalendarRules.parseMonth("  ")).isEqualTo(YearMonth.now());
        assertThatThrownBy(() -> HomeCalendarRules.parseMonth("2026-13")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> HomeCalendarRules.parseMonth("2026/10")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("막대 — 판매 중(진행중 · 중단 예정) · 오픈 전(준비중 · 준비완료) · 종결(종료 · 정산완료 · 중단)")
    void barStatus() {
        assertThat(HomeCalendarRules.barStatus(GroupBuyStatus.IN_PROGRESS)).isEqualTo("IN_PROGRESS");
        assertThat(HomeCalendarRules.barStatus(GroupBuyStatus.SUSPENSION_SCHEDULED)).isEqualTo("IN_PROGRESS");
        assertThat(HomeCalendarRules.barStatus(GroupBuyStatus.PREPARING)).isEqualTo("UPCOMING");
        assertThat(HomeCalendarRules.barStatus(GroupBuyStatus.READY)).isEqualTo("UPCOMING");
        assertThat(HomeCalendarRules.barStatus(GroupBuyStatus.ENDED)).isEqualTo("ENDED");
        assertThat(HomeCalendarRules.barStatus(GroupBuyStatus.SETTLED)).isEqualTo("ENDED");
        assertThat(HomeCalendarRules.barStatus(GroupBuyStatus.SUSPENDED)).isEqualTo("ENDED");
        assertThat(HomeCalendarRules.PAD_DAYS).isEqualTo(7);
    }
}

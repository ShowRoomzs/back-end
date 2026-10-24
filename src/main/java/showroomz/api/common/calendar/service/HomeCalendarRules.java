package showroomz.api.common.calendar.service;

import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** 파트너 · 스튜디오 홈 월 달력 공통 규칙(1017 설계서 4-3 · 5-3) — 두 달력이 같은 칸 · 같은 막대 색을 써야 한다. */
public final class HomeCalendarRules {

    /** 시안 그리드는 앞뒤 달 칸까지 그린다(9.27 ~ 11.1) — 요청 월 ±7일을 넉넉히 준다. */
    public static final int PAD_DAYS = 7;

    private HomeCalendarRules() {
    }

    /** {@code yyyy-MM} — 생략하면 이번 달. */
    public static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "month 는 yyyy-MM 형식이어야 합니다.");
        }
    }

    /** 막대 3색 — 판매 중(진행중 · 중단 예정) · 오픈 전(준비중 · 준비완료) · 종결. */
    public static String barStatus(GroupBuyStatus status) {
        if (status.isSelling()) {
            return "IN_PROGRESS";
        }
        return status.isTerminal() ? "ENDED" : "UPCOMING";
    }
}

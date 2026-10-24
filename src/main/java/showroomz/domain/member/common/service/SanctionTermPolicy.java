package showroomz.domain.member.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.MemberProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * 계정 이용 정지 기간 · 이의 신청 기한(1021 결정 노트 기-02 【잠정】 · 설계서 7-2) — 브랜드 · 인플루언서 · 소비자 공통.
 *
 * <ul>
 *   <li>시작일은 오늘 고정 · 종료일(포함)은 오늘 이상 — 다음 날 00:00 에 시스템이 해제한다. 종료일이 없으면 기간 없는 정지(수동 해제).</li>
 *   <li>이의 신청 기한 = 통지 + N영업일 23:59:59({@code member.sanction.appeal-business-days}) — 표시용 · 기한 뒤에도 접수한다(14절 Q20).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SanctionTermPolicy {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final MemberProperties properties;
    private final BusinessCalendar businessCalendar;

    /** 종료일 검사 — 비어 있으면 기간 없음(null). 오늘보다 앞이면 400. */
    public LocalDate requireEndsOn(LocalDate endsOn, LocalDateTime now) {
        if (endsOn != null && endsOn.isBefore(now.toLocalDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정지 종료일은 오늘 이후로 선택해 주세요.");
        }
        return endsOn;
    }

    /** 이의 신청 기한 — 통지가 비영업일이면 다음 영업일을 기산일로 + N영업일의 끝. */
    public LocalDateTime appealDeadline(LocalDateTime now) {
        LocalDate base = now.toLocalDate();
        while (!businessCalendar.isBusinessDay(base)) {
            base = base.plusDays(1);
        }
        return businessCalendar.addBusinessDays(base, properties.getSanction().getAppealBusinessDays()).atTime(END_OF_DAY);
    }

    /** 이의 신청 기한 영업일 수({@code member.sanction.appeal-business-days}) — 정지 모달 「통지 후 N영업일 이상」. */
    public int appealBusinessDays() {
        return properties.getSanction().getAppealBusinessDays();
    }

    /** 소명 검토 N영업일 — 운영자 처리 기한 · 표시만. 미정이면 null(FE 「[정책값 미정]」). */
    public Integer appealReviewBusinessDays() {
        return properties.getSanction().getAppealReviewBusinessDays();
    }

    /** 자동 해제 시각 — 종료일 다음 날 00:00. */
    public static LocalDateTime autoReleaseAt(LocalDate endsOn) {
        return endsOn == null ? null : endsOn.plusDays(1).atStartOfDay();
    }

    /** 정지 기간(일) — 시작일 · 종료일 포함. */
    public static Integer periodDays(LocalDateTime suspendedAt, LocalDate endsOn) {
        return endsOn == null || suspendedAt == null ? null
                : (int) ChronoUnit.DAYS.between(suspendedAt.toLocalDate(), endsOn) + 1;
    }
}

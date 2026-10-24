package showroomz.api.admin.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;

/**
 * 이의 신청 심사 기한 — 접수 + N영업일(기본 3 · 주말 · 공휴일 제외 · §54-5).
 *
 * <p>표시용이다. 넘겨도 자동 처리는 없다(§54-7 ⓒ 미결) — FE가 지난 건을 강조만 한다.
 * 영업일은 공용 {@link BusinessCalendar}로 센다 — 다른 기한들과 같은 달력을 써야 한다.
 */
@Component
@RequiredArgsConstructor
public class PostAppealReviewDue {

    private final BusinessCalendar businessCalendar;
    private final PostProperties postProperties;

    public LocalDateTime of(LocalDateTime submittedAt) {
        if (submittedAt == null) {
            return null;
        }
        return businessCalendar.addBusinessDays(submittedAt.toLocalDate(), postProperties.getAppealReviewBusinessDays())
                .atTime(submittedAt.toLocalTime());
    }
}

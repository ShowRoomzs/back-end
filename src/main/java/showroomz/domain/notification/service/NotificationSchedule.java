package showroomz.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.NotificationProperties;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 광고성 푸시의 발송 시각(C15 · 1017 설계서 1-6) — 야간(21~08시) 미발송 · 하루 묶음 시각. 시각은 전부 <b>한국 시각</b>으로 판정하고
 * 서버 시각으로 돌려준다(58 계획 3-3 — 운영 JVM 은 UTC 라 그대로 읽으면 야간이 KST 06~17시가 된다).
 */
@Component
@RequiredArgsConstructor
public class NotificationSchedule {

    private final NotificationProperties properties;

    public boolean isQuiet(LocalDateTime at) {
        int hour = KstDates.toKst(at).getHour();
        return hour >= properties.getQuietStartHour() || hour < properties.getQuietEndHour();
    }

    /** 지금 보낼 수 있으면 지금, 야간이면 다음 아침 끝 시각. */
    public LocalDateTime nextAllowed(LocalDateTime at) {
        if (!isQuiet(at)) {
            return at;
        }
        LocalDateTime kst = KstDates.toKst(at);
        LocalDate morningDate = kst.getHour() < properties.getQuietEndHour() ? kst.toLocalDate() : kst.toLocalDate().plusDays(1);
        return KstDates.at(morningDate, LocalTime.of(properties.getQuietEndHour(), 0));
    }

    /** 하루 묶음 — 오늘 묶음 시각이 남았으면 그 시각, 지났으면 지금(야간이면 다음 아침). */
    public LocalDateTime digestSlot(LocalDateTime at) {
        LocalDateTime slot = KstDates.at(KstDates.toKstDate(at), LocalTime.of(properties.getDigestHour(), 0));
        return at.isBefore(slot) ? nextAllowed(slot) : nextAllowed(at);
    }
}

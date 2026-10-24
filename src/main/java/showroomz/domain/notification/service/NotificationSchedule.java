package showroomz.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.NotificationProperties;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** 광고성 푸시의 발송 시각(C15 · 1017 설계서 1-6) — 야간(21~08시) 미발송 · 하루 묶음 시각. */
@Component
@RequiredArgsConstructor
public class NotificationSchedule {

    private final NotificationProperties properties;

    public boolean isQuiet(LocalDateTime at) {
        int hour = at.getHour();
        return hour >= properties.getQuietStartHour() || hour < properties.getQuietEndHour();
    }

    /** 지금 보낼 수 있으면 지금, 야간이면 다음 아침 끝 시각. */
    public LocalDateTime nextAllowed(LocalDateTime at) {
        if (!isQuiet(at)) {
            return at;
        }
        LocalDateTime morning = at.toLocalDate().atTime(LocalTime.of(properties.getQuietEndHour(), 0));
        return at.getHour() < properties.getQuietEndHour() ? morning : morning.plusDays(1);
    }

    /** 하루 묶음 — 오늘 묶음 시각이 남았으면 그 시각, 지났으면 지금(야간이면 다음 아침). */
    public LocalDateTime digestSlot(LocalDateTime at) {
        LocalDateTime slot = at.toLocalDate().atTime(LocalTime.of(properties.getDigestHour(), 0));
        return at.isBefore(slot) ? nextAllowed(slot) : nextAllowed(at);
    }
}

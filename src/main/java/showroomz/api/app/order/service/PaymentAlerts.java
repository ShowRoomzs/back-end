package showroomz.api.app.order.service;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결제 운영 경보 — 로그 + Sentry. 돈이 얽힌 어긋남(자동 취소 · 취소 실패 · 불변식 위반)은 예외가 아니라 <b>상태</b>라
 * 던져서 롤백시키지 않고 여기로 올린다(결제 계획서 2-3 ③ · 4-2 · 4-8).
 */
@Slf4j
@Component
public class PaymentAlerts {

    public void error(String message) {
        log.error("[결제 경보] {}", message);
        Sentry.captureMessage(message, SentryLevel.ERROR);
    }

    public void warning(String message) {
        log.warn("[결제 경고] {}", message);
        Sentry.captureMessage(message, SentryLevel.WARNING);
    }
}

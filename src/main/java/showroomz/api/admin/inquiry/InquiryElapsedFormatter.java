package showroomz.api.admin.inquiry;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * §17-6 경과 표기 전용 포매터 — {@code >= 24h → 3일 2h}, {@code < 24h → 19h 42m}, {@code < 1h → 42m}.
 * 클라이언트 시각에 흔들리지 않도록 서버에서 계산한 문자열을 내려준다.
 */
public final class InquiryElapsedFormatter {

    private InquiryElapsedFormatter() {
    }

    public static String format(LocalDateTime from, LocalDateTime to) {
        Duration duration = Duration.between(from, to);
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }

        long totalMinutes = duration.toMinutes();
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        if (days > 0) {
            return days + "일 " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}

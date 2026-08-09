package showroomz.api.admin.changerequest;

import java.time.Duration;
import java.time.LocalDateTime;

/** §16-1 경과 표기 전용 포매터 — {@code < 24h → 18h}, {@code >= 24h → 2일 3h}. */
public final class ChangeRequestElapsedFormatter {

    private ChangeRequestElapsedFormatter() {
    }

    public static String format(LocalDateTime requestedAt) {
        long totalHours = Duration.between(requestedAt, LocalDateTime.now()).toHours();
        if (totalHours < 0) {
            totalHours = 0;
        }
        if (totalHours < 24) {
            return totalHours + "h";
        }
        long days = totalHours / 24;
        long remainingHours = totalHours % 24;
        return days + "일 " + remainingHours + "h";
    }
}

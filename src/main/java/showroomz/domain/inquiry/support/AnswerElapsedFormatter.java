package showroomz.domain.inquiry.support;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 답변 소요 표기 (§23-3 우측 처리 메타) — {@code 3일 2시간} / {@code 1시간 43분} / {@code 42분}.
 * SLA 잔여 시간이 아니라 실제로 걸린 시간이다 (§23-2). 클라이언트 시각에 흔들리지 않도록
 * 서버에서 계산한 문자열을 내려준다.
 */
public final class AnswerElapsedFormatter {

    private AnswerElapsedFormatter() {
    }

    public static String format(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return null;
        }

        Duration duration = Duration.between(from, to);
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }

        long totalMinutes = duration.toMinutes();
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        if (days > 0) {
            return days + "일 " + hours + "시간";
        }
        if (hours > 0) {
            return hours + "시간 " + minutes + "분";
        }
        return minutes + "분";
    }
}

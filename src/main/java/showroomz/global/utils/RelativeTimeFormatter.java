package showroomz.global.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 시간을 '방금 전', '1일 전' 등 상대적 시간 포맷으로 변환하는 유틸리티
 */
public final class RelativeTimeFormatter {

    private RelativeTimeFormatter() {
    }

    public static String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        long seconds = ChronoUnit.SECONDS.between(dateTime, now);

        if (seconds < 0) {
            return "방금 전";
        }
        if (seconds < 60) {
            return "방금 전";
        }
        if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "분 전";
        }
        if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + "시간 전";
        }
        if (seconds < 604800) {
            long days = seconds / 86400;
            return (days == 1 ? "1일 전" : days + "일 전");
        }
        if (seconds < 2592000) {
            long weeks = seconds / 604800;
            return (weeks == 1 ? "1주 전" : weeks + "주 전");
        }
        if (seconds < 31536000) {
            long months = seconds / 2592000;
            return (months == 1 ? "1개월 전" : months + "개월 전");
        }
        long years = seconds / 31536000;
        return (years == 1 ? "1년 전" : years + "년 전");
    }
}

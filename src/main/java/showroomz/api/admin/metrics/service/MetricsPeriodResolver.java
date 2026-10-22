package showroomz.api.admin.metrics.service;

import org.springframework.stereotype.Component;
import showroomz.api.admin.metrics.type.MetricsPreset;
import showroomz.api.admin.metrics.type.MetricsUnit;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 운영 지표 기간 규칙(1017 설계서 7-0 ② · 7-1) — 서버가 확정해 응답에 적는다. 기준 시각은 오늘 00:00(TODAY 만 지금).
 * 비교는 언제나 직전 같은 길이다(M1 만 전월 1일 ~ 같은 일).
 */
@Component
public class MetricsPeriodResolver {

    static final int CUSTOM_MAX_DAYS = 366;
    static final int MONTHLY_OVER_DAYS = 90;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy.MM");

    public Resolved resolve(MetricsPreset preset, LocalDate from, LocalDate to, LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        return switch (preset == null ? MetricsPreset.D7 : preset) {
            case TODAY -> new Resolved(MetricsPreset.TODAY, today, now, today.minusDays(1), now.minusDays(1),
                    MetricsUnit.HOUR, "오늘 00:00 ~ " + now.toLocalTime().withNano(0).withSecond(0), "어제 같은 시간대");
            case D7 -> lastDays(MetricsPreset.D7, today.minusDays(7), today, MetricsUnit.DAY);
            case M1 -> {
                LocalDateTime start = today.withDayOfMonth(1);
                if (!start.isBefore(today)) {
                    // 1일이면 「1일 ~ 어제」가 비어 있다 — 지난달 전체를 본다.
                    start = start.minusMonths(1);
                }
                LocalDateTime compareFrom = start.minusMonths(1);
                LocalDateTime compareTo = today.minusMonths(1);
                yield new Resolved(MetricsPreset.M1, start, today, compareFrom, compareTo, MetricsUnit.DAY,
                        label(start, today), "전월 " + label(compareFrom, compareTo));
            }
            case M6 -> {
                LocalDateTime start = today.withDayOfMonth(1).minusMonths(5);
                if (!start.isBefore(today)) {
                    start = start.minusMonths(1);
                }
                long days = ChronoUnit.DAYS.between(start, today);
                yield new Resolved(MetricsPreset.M6, start, today, start.minusDays(days), start, MetricsUnit.MONTH,
                        start.format(YM) + " ~ " + today.minusDays(1).format(YM), "직전 같은 기간");
            }
            case CUSTOM -> custom(from, to);
        };
    }

    private Resolved lastDays(MetricsPreset preset, LocalDateTime from, LocalDateTime to, MetricsUnit unit) {
        long days = ChronoUnit.DAYS.between(from, to);
        return new Resolved(preset, from, to, from.minusDays(days), from, unit, label(from, to),
                "직전 " + label(from.minusDays(days), from));
    }

    private Resolved custom(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > CUSTOM_MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "직접 선택 기간은 from ≤ to · 최대 1년입니다.");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        long days = ChronoUnit.DAYS.between(start, end);
        MetricsUnit unit = days > MONTHLY_OVER_DAYS ? MetricsUnit.MONTH : MetricsUnit.DAY;
        return new Resolved(MetricsPreset.CUSTOM, start, end, start.minusDays(days), start, unit, label(start, end),
                "직전 " + label(start.minusDays(days), start));
    }

    private static String label(LocalDateTime from, LocalDateTime to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return from.format(MD) + " ~ " + to.minusDays(1).format(MD) + " (" + days + "일)";
    }

    /** 버킷 — [시작, 끝) 목록. 주는 월요일 시작이다. */
    public static List<LocalDateTime[]> buckets(LocalDateTime from, LocalDateTime to, MetricsUnit unit) {
        List<LocalDateTime[]> buckets = new ArrayList<>();
        LocalDateTime cursor = switch (unit) {
            case HOUR -> from.truncatedTo(ChronoUnit.HOURS);
            case DAY -> from.toLocalDate().atStartOfDay();
            case WEEK -> from.toLocalDate().minusDays(from.getDayOfWeek().getValue() - 1L).atStartOfDay();
            case MONTH -> from.toLocalDate().withDayOfMonth(1).atStartOfDay();
        };
        while (cursor.isBefore(to)) {
            LocalDateTime next = switch (unit) {
                case HOUR -> cursor.plusHours(1);
                case DAY -> cursor.plusDays(1);
                case WEEK -> cursor.plusWeeks(1);
                case MONTH -> cursor.plusMonths(1);
            };
            buckets.add(new LocalDateTime[]{cursor.isBefore(from) ? from : cursor, next.isAfter(to) ? to : next});
            cursor = next;
        }
        return buckets;
    }

    public static String bucketLabel(LocalDateTime start, MetricsUnit unit) {
        return switch (unit) {
            case HOUR -> start.getHour() + "시";
            case DAY -> start.format(MD);
            case WEEK -> start.format(MD) + "~";
            case MONTH -> start.format(YM);
        };
    }

    /**
     * @param from      시작(포함)
     * @param to        끝(제외)
     */
    public record Resolved(MetricsPreset preset, LocalDateTime from, LocalDateTime to, LocalDateTime compareFrom,
                           LocalDateTime compareTo, MetricsUnit unit, String label, String compareLabel) {

        public long days() {
            return Math.max(1, (long) Math.ceil(Duration.between(from, to).toMinutes() / 1440.0));
        }
    }
}

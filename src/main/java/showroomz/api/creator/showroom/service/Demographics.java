package showroomz.api.creator.showroom.service;

import showroomz.api.creator.showroom.dto.DistributionItem;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 연령대·성별 분포를 만드는 공용 규칙.
 *
 * <p>쇼룸 현황(§22-4)과 게시물 인사이트(§24-7)가 <b>같은 구간과 같은 라벨</b>을 써야 한다 —
 * §24-7이 "③의 규칙은 쇼룸 관리와 동일"이라고 못박았다. 두 화면이 각자 구간을 들고 있으면
 * 같은 사람이 한쪽에서는 "25–34세", 다른 쪽에서는 "20대"로 세어지는 일이 생긴다.
 *
 * <p>여기서도 개인 단위 정보는 다루지 않는다. 들어오는 것은 성별·생년월일 값뿐이고
 * 나가는 것은 비율뿐이다.
 */
public final class Demographics {

    /** 값이 없거나 형식이 깨진 표본 — 숨기지 않고 항목으로 드러낸다(§22-5 수집 한계) */
    public static final String UNKNOWN_LABEL = "미확인";

    public static final List<String> AGE_LABELS = List.of("18–24세", "25–34세", "35–44세", "45세 이상", UNKNOWN_LABEL);

    public static final List<String> GENDER_LABELS = List.of("여성", "남성", UNKNOWN_LABEL);

    private Demographics() {
    }

    public static String ageGroupOf(String birthday, LocalDate today) {
        if (birthday == null || birthday.isBlank()) {
            return UNKNOWN_LABEL;
        }
        try {
            int age = Period.between(LocalDate.parse(birthday), today).getYears();
            if (age <= 24) {
                // 만 14세 이상만 가입하므로 최저 구간 아래는 구간을 늘리지 않고 여기에 합친다.
                return "18–24세";
            }
            if (age <= 34) {
                return "25–34세";
            }
            if (age <= 44) {
                return "35–44세";
            }
            return "45세 이상";
        } catch (DateTimeParseException e) {
            return UNKNOWN_LABEL;
        }
    }

    public static String genderLabelOf(String gender) {
        if ("FEMALE".equalsIgnoreCase(gender)) {
            return "여성";
        }
        if ("MALE".equalsIgnoreCase(gender)) {
            return "남성";
        }
        return UNKNOWN_LABEL;
    }

    /** 빈 상태에서도 항목이 사라지지 않도록 라벨을 미리 깔아 둔다(화면 순서도 그대로 유지된다) */
    public static Map<String, Long> newCountMap(String... labels) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String label : labels) {
            counts.put(label, 0L);
        }
        return counts;
    }

    public static Map<String, Long> newCountMap(List<String> labels) {
        return newCountMap(labels.toArray(new String[0]));
    }

    public static void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    public static List<DistributionItem> toDistribution(Map<String, Long> counts, long total) {
        List<DistributionItem> items = new ArrayList<>(counts.size());
        counts.forEach((label, count) -> items.add(new DistributionItem(label, ratio(count, total))));
        return items;
    }

    public static Double ratio(long value, long total) {
        return total == 0 ? 0.0 : round(value * 100.0 / total);
    }

    public static Double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}

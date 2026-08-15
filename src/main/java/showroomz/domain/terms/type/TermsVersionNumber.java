package showroomz.domain.terms.type;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 버전 번호 (기획 §21-5) — 접두 {@code v} 없이 숫자와 점만 저장한다.
 *
 * <p>화면이 붙이는 {@code v}를 값에 함께 담으면 {@code v3.2}·{@code V3.2}·{@code 3.2}가 섞여
 * 같은 버전이 여러 표기로 남는다. 저장은 {@code 3.2}, 표시는 {@code v3.2}로 나눈다.
 *
 * <p>비교는 문자열이 아니라 <b>구간별 숫자</b>로 한다 — 문자열 비교는 {@code 3.10 < 3.9}로 뒤집힌다.
 */
public record TermsVersionNumber(List<Integer> segments) implements Comparable<TermsVersionNumber> {

    /** 숫자와 점만 — 최대 3구간(예: 1, 1.0, 1.0.1) */
    private static final Pattern FORMAT = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){0,2}");

    /** 최초 등록에 버전을 고르게 하면 v0.9 같은 값이 들어오므로 첫 버전은 고정한다 (기획 §21-5) */
    public static final String FIRST_VERSION = "1.0";

    public static boolean isValidFormat(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    public static TermsVersionNumber of(String value) {
        if (!isValidFormat(value)) {
            throw new IllegalArgumentException("버전 번호 형식이 올바르지 않습니다: " + value);
        }

        List<Integer> segments = new ArrayList<>();
        for (String segment : value.split("\\.")) {
            segments.add(Integer.parseInt(segment));
        }
        return new TermsVersionNumber(segments);
    }

    /** 구간 수가 달라도 비교할 수 있게 없는 구간은 0으로 본다 — {@code 3.1}과 {@code 3.1.0}은 같은 값이다. */
    @Override
    public int compareTo(TermsVersionNumber other) {
        int length = Math.max(this.segments.size(), other.segments.size());
        for (int i = 0; i < length; i++) {
            int compared = Integer.compare(segmentAt(i), other.segmentAt(i));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private int segmentAt(int index) {
        return index < segments.size() ? segments.get(index) : 0;
    }
}

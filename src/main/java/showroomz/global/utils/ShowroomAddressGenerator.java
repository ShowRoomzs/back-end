package showroomz.global.utils;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * §22-1 쇼룸 주소 생성 유틸 — 가입 시 쇼룸명을 기준으로 핸들(`showroomz.com/@{핸들}`의 뒷부분)을 만든다.
 *
 * <p>한 번 발급되면 쇼룸명을 바꿔도 따라 바뀌지 않는다. 링크가 바뀌면 인플루언서가 인스타그램
 * 프로필·스토리에 이미 뿌려둔 링크가 전부 죽기 때문이다(그래서 이 유틸은 가입 시점에만 호출한다).
 *
 * <p>쇼룸명은 한글이 허용되지만 URL 핸들은 ASCII로 제한한다 — 퍼센트 인코딩된 주소는 복사·공유 과정에서
 * 깨져 보이고, 소비자가 눈으로 읽고 옮겨 적을 수 없다. 한글만으로 된 쇼룸명은 남는 글자가 없으므로
 * 익명 접두사(`sr` + 랜덤)로 떨어진다.
 */
public final class ShowroomAddressGenerator {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 32;
    private static final String FALLBACK_PREFIX = "sr";
    private static final String FALLBACK_ALPHABET = "abcdefghijkmnopqrstuvwxyz23456789";
    private static final int FALLBACK_SUFFIX_LENGTH = 8;
    /** 숫자 꼬리표(`name_2`…)로 비켜갈 수 있는 횟수. 이 이상 겹치면 랜덤 핸들로 넘어간다. */
    private static final int MAX_NUMBERED_ATTEMPTS = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShowroomAddressGenerator() {
    }

    /** 쇼룸명에서 핸들 후보를 만들고, 이미 쓰이는 값이면 숫자 꼬리표 → 랜덤 순으로 비켜간다. */
    public static String generateUnique(String showroomName, Predicate<String> alreadyExists) {
        String base = toHandle(showroomName);

        if (base != null && !alreadyExists.test(base)) {
            return base;
        }
        if (base != null) {
            for (int suffix = 2; suffix <= MAX_NUMBERED_ATTEMPTS; suffix++) {
                String candidate = withSuffix(base, "_" + suffix);
                if (!alreadyExists.test(candidate)) {
                    return candidate;
                }
            }
        }

        String candidate;
        do {
            candidate = randomHandle();
        } while (alreadyExists.test(candidate));
        return candidate;
    }

    /**
     * 쇼룸명 → 핸들. 영문·숫자는 소문자로 남기고 공백은 `_`로 바꾼다.
     * 쓸 수 있는 글자가 {@value #MIN_LENGTH}자에 못 미치면(한글 전용 쇼룸명 등) null을 돌려준다.
     */
    private static String toHandle(String showroomName) {
        if (showroomName == null || showroomName.isBlank()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (char c : showroomName.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (c == ' ' || c == '_' || c == '-') {
                // 구분자가 연달아 붙거나 맨 앞에 오지 않게 한다.
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
            }
        }

        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.length() > MAX_LENGTH) {
            sb.setLength(MAX_LENGTH);
        }
        return sb.length() >= MIN_LENGTH ? sb.toString() : null;
    }

    /** 꼬리표를 붙여도 최대 길이를 넘지 않도록 앞부분을 잘라낸다. */
    private static String withSuffix(String base, String suffix) {
        int room = MAX_LENGTH - suffix.length();
        String head = base.length() > room ? base.substring(0, room) : base;
        return head + suffix;
    }

    private static String randomHandle() {
        StringBuilder sb = new StringBuilder(FALLBACK_PREFIX);
        for (int i = 0; i < FALLBACK_SUFFIX_LENGTH; i++) {
            sb.append(FALLBACK_ALPHABET.charAt(RANDOM.nextInt(FALLBACK_ALPHABET.length())));
        }
        return sb.toString();
    }
}

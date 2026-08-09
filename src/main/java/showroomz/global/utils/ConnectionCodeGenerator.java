package showroomz.global.utils;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * §13-6 연결코드 생성 유틸 — 대문자+숫자, 혼동 문자(0/O/1/I) 제외.
 */
public final class ConnectionCodeGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConnectionCodeGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 후보 32종 · 10자리라 실제 충돌 가능성은 거의 없지만, 유니크 제약 충돌 시 재시도한다. */
    public static String generateUnique(Predicate<String> alreadyExists) {
        String code;
        do {
            code = generate();
        } while (alreadyExists.test(code));
        return code;
    }
}

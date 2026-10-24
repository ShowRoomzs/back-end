package showroomz.api.app.auth.service;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 포트원 {@code identityVerificationId} 발급(57 설계서 R-4 · 1-2) — {@code idv} + ULID(26자 · Crockford base32 대문자 · 숫자) = 29자.
 * KCP 제약 「영문 대소문자 · 숫자만 · 40자 이하」를 지킨다 — 결제 {@code paymentId}(하이픈 포함) 형식을 따라가면 안 된다.
 * 앞 10자가 시각(ms)이라 정렬 가능하고, 뒤 16자는 80비트 난수다.
 */
final class IdentityVerificationIdGenerator {

    static final String PREFIX = "idv";
    static final Pattern KCP_ALLOWED = Pattern.compile("^[A-Za-z0-9]{1,40}$");

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdentityVerificationIdGenerator() {
    }

    static String next() {
        return next(System.currentTimeMillis());
    }

    static String next(long epochMillis) {
        char[] out = new char[26];
        long time = epochMillis;
        for (int i = 9; i >= 0; i--) {
            out[i] = ALPHABET[(int) (time & 0x1F)];
            time >>>= 5;
        }
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        // 80비트를 5비트씩 16조각으로
        long hi = 0;
        for (int i = 0; i < 5; i++) {
            hi = (hi << 8) | (random[i] & 0xFF);
        }
        long lo = 0;
        for (int i = 5; i < 10; i++) {
            lo = (lo << 8) | (random[i] & 0xFF);
        }
        for (int i = 17; i >= 10; i--) {
            out[i] = ALPHABET[(int) (hi & 0x1F)];
            hi >>>= 5;
        }
        for (int i = 25; i >= 18; i--) {
            out[i] = ALPHABET[(int) (lo & 0x1F)];
            lo >>>= 5;
        }
        return PREFIX + new String(out);
    }
}

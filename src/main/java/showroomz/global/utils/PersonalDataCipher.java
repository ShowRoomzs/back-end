package showroomz.global.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 고유식별정보(주민등록번호) 양방향 암호화 — AES-256-GCM(1009 기획 수정본 7-a). 원천징수 신고(지급명세서)에 원문이
 * 필요하므로 해시가 아니라 복호화 가능한 암호화다. 저장 형식은 {@code base64(IV 12바이트 ‖ 암호문 ‖ 태그)}.
 *
 * <p>키 — {@code showroomz.crypto.personal-data-key}(base64 32바이트 · 환경변수 {@code PERSONAL_DATA_KEY}). 비어 있으면
 * {@code jwt.secret}에서 SHA-256 으로 파생한다 — 기동은 되지만 운영에서는 전용 키를 넣는다. <b>키를 바꾸면 기존 행을
 * 다시 암호화해야 한다</b>(키 버전 컬럼은 두지 않았다 — 런칭 전 회원이 없다).
 *
 * <p>원문은 로그 · 예외 메시지 · API 응답 어디에도 남기지 않는다. 화면 표시는 별도로 저장하는 마스킹 값을 쓴다.
 */
@Slf4j
@Component
public class PersonalDataCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PersonalDataCipher(@Value("${showroomz.crypto.personal-data-key:}") String base64Key,
                              @Value("${jwt.secret:}") String fallbackSecret) {
        this.key = new SecretKeySpec(resolveKey(base64Key, fallbackSecret), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("고유식별정보 암호화 실패", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("고유식별정보 복호화 실패", e);
        }
    }

    private static byte[] resolveKey(String base64Key, String fallbackSecret) {
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
            if (decoded.length != 32) {
                throw new IllegalStateException("showroomz.crypto.personal-data-key 는 base64 32바이트여야 합니다.");
            }
            return decoded;
        }
        if (fallbackSecret == null || fallbackSecret.isBlank()) {
            throw new IllegalStateException("고유식별정보 암호화 키가 없습니다 — PERSONAL_DATA_KEY 를 설정하세요.");
        }
        log.warn("PERSONAL_DATA_KEY 가 없어 jwt.secret 에서 파생한 키로 고유식별정보를 암호화합니다 — 운영에서는 전용 키를 설정하세요.");
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("showroomz-personal-data:" + fallbackSecret).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

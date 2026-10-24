package showroomz.global.payment.settlement.toss;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;

import java.text.ParseException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 토스페이먼츠 지급대행 요청 본문 암·복호화(44 토스 설계서 1-2 · 6-2) — JWE {@code alg=dir · enc=A256GCM} · 커스텀 헤더 {@code iat}
 * ({@code yyyy-MM-dd'T'HH:mm:ss±hh:mm}) · {@code nonce}(UUID). 키는 보안 키 64자 hex → 32바이트. 순수 함수 — 네트워크 · 스프링 무관.
 */
public final class TossJwe {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter IAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final int KEY_HEX_LENGTH = 64;

    private final byte[] key;

    public TossJwe(String securityKeyHex) {
        this.key = parseKey(securityKeyHex);
    }

    /** 보안 키 검증 — 64자 hex 가 아니면 기동 검증이 잡는다(6-4). */
    public static byte[] parseKey(String securityKeyHex) {
        if (securityKeyHex == null || securityKeyHex.trim().length() != KEY_HEX_LENGTH) {
            throw new IllegalArgumentException("toss.payouts.security-key 는 64자 hex 여야 합니다.");
        }
        try {
            return HexFormat.of().parseHex(securityKeyHex.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("toss.payouts.security-key 가 hex 가 아닙니다.", e);
        }
    }

    public String encrypt(String json) {
        return encrypt(json, OffsetDateTime.now(KST));
    }

    /** 테스트가 시각을 고정한다. */
    public String encrypt(String json, OffsetDateTime issuedAt) {
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
                .customParam("iat", IAT.format(issuedAt))
                .customParam("nonce", UUID.randomUUID().toString())
                .build();
        JWEObject jwe = new JWEObject(header, new Payload(json));
        try {
            jwe.encrypt(new DirectEncrypter(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("토스 지급대행 요청 암호화 실패", e);
        }
        return jwe.serialize();
    }

    /** @throws IllegalArgumentException JWE 가 아니거나 키가 맞지 않는다 — 호출자가 「평문 JSON 인가」를 다시 본다(6-2) */
    public String decrypt(String compact) {
        try {
            JWEObject jwe = JWEObject.parse(compact == null ? "" : compact.trim());
            jwe.decrypt(new DirectDecrypter(key));
            return jwe.getPayload().toString();
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("토스 지급대행 응답 복호화 실패 — " + e.getMessage(), e);
        }
    }

    /** 헤더만 — 테스트가 {@code iat · nonce} 를 확인한다. */
    public static JWEHeader header(String compact) {
        try {
            return JWEObject.parse(compact).getHeader();
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}

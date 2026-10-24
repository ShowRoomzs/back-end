package showroomz.global.payment.settlement.toss;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 토스 지급대행 웹훅({@code payout.changed · seller.changed}) 서명 검증(44 토스 설계서 1-6 · 6-2) —
 * {@code HMAC-SHA256(보안 키, "{payload}:{tosspayments-webhook-transmission-time}")} 과 헤더 {@code tosspayments-webhook-signature}
 * 의 {@code v1:} 값(쉼표로 여러 개) 중 하나가 같으면 토스가 보낸 것이다.
 *
 * <p>「보안 키로 해싱」의 키 바이트가 hex 디코딩인지 문자열 UTF-8 인지 문서가 적지 않아 <b>둘 다</b> 받는다 — 8-2 T5 에서 하나로
 * 좁힌다. 본문은 <b>원문 바이트</b>로 받는다(파싱 뒤 다시 직렬화하면 서명이 어긋난다 · 결제 웹훅 관례).
 */
@Slf4j
public class TossPayoutsWebhookVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String VERSION_PREFIX = "v1:";

    private final List<byte[]> keys = new ArrayList<>(2);

    public TossPayoutsWebhookVerifier(String securityKeyHex) {
        if (securityKeyHex == null || securityKeyHex.isBlank()) {
            throw new IllegalArgumentException("토스 지급대행 보안 키가 비어 있습니다.");
        }
        keys.add(TossJwe.parseKey(securityKeyHex));
        keys.add(securityKeyHex.trim().getBytes(StandardCharsets.US_ASCII));
    }

    public boolean verify(byte[] body, String transmissionTime, String signatureHeader) {
        if (body == null || transmissionTime == null || transmissionTime.isBlank() || signatureHeader == null
                || signatureHeader.isBlank()) {
            return false;
        }
        List<byte[]> candidates = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String value = part.trim();
            if (value.startsWith(VERSION_PREFIX)) {
                value = value.substring(VERSION_PREFIX.length());
            }
            try {
                candidates.add(Base64.getDecoder().decode(value));
            } catch (IllegalArgumentException e) {
                log.debug("웹훅 서명 base64 해석 실패 - {}", value);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        for (byte[] key : keys) {
            byte[] expected = hmac(key, body, transmissionTime);
            for (byte[] candidate : candidates) {
                if (MessageDigest.isEqual(expected, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 테스트용 서명 — hex 디코딩 키로 {@code v1:} + base64. */
    public String sign(byte[] body, String transmissionTime) {
        return VERSION_PREFIX + Base64.getEncoder().encodeToString(hmac(keys.get(0), body, transmissionTime));
    }

    private static byte[] hmac(byte[] key, byte[] body, String transmissionTime) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            mac.update(body);
            mac.update((":" + transmissionTime).getBytes(StandardCharsets.UTF_8));
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("웹훅 서명 계산 실패", e);
        }
    }
}

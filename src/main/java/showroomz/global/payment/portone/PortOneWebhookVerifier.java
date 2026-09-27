package showroomz.global.payment.portone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * 포트원 V2 웹훅 서명 검증 — Standard Webhooks(HMAC-SHA256)(결제 계획서 5-7 ①).
 *
 * <p>서명 대상은 {@code {webhook-id}.{webhook-timestamp}.{원문 본문}}이다. 본문은 <b>바이트 그대로</b> 써야 한다 — Jackson으로
 * 먼저 파싱하면 공백·순서가 바뀌어 서명이 안 맞는다(6-3). 시크릿은 {@code whsec_} 접두 뒤 base64다.
 * 헤더 {@code webhook-signature}는 공백으로 구분된 {@code v1,<base64>} 목록이고 하나라도 맞으면 통과다(키 로테이션).
 */
public class PortOneWebhookVerifier {

    private static final String SECRET_PREFIX = "whsec_";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final byte[] secret;
    private final long toleranceSeconds;

    public PortOneWebhookVerifier(String webhookSecret, long toleranceSeconds) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("포트원 웹훅 시크릿이 비어 있습니다.");
        }
        String raw = webhookSecret.startsWith(SECRET_PREFIX) ? webhookSecret.substring(SECRET_PREFIX.length()) : webhookSecret;
        this.secret = Base64.getDecoder().decode(raw);
        this.toleranceSeconds = toleranceSeconds;
    }

    public boolean verify(byte[] body, String webhookId, String timestamp, String signatureHeader, Instant now) {
        if (body == null || webhookId == null || timestamp == null || signatureHeader == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - ts) > toleranceSeconds) {
            return false;
        }
        String expected = sign(body, webhookId, timestamp.trim());
        for (String candidate : signatureHeader.trim().split("\\s+")) {
            String[] parts = candidate.split(",", 2);
            if (parts.length != 2 || !"v1".equals(parts[0])) {
                continue;
            }
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /** 테스트가 고정 시크릿으로 서명을 만들 때도 쓴다(8절 「고정 시크릿으로 서명 생성」). */
    public String sign(byte[] body, String webhookId, String timestamp) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            mac.update((webhookId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return Base64.getEncoder().encodeToString(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("웹훅 서명 계산 실패", e);
        }
    }
}

package showroomz.global.payment.portone;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookVerifier;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 포트원 V2 웹훅 서명 검증 — SDK {@link WebhookVerifier}(Standard Webhooks, HMAC-SHA256)에 위임한다(결제 계획서 5-7 ①).
 *
 * <p>본문은 <b>원문 바이트</b>로 받아 UTF-8 문자열로 넘긴다 — Jackson 으로 먼저 파싱하면 공백·순서가 바뀌어 서명이 안 맞는다(6-3).
 * 시각 허용 오차(5분)는 SDK 가 정한다. {@link #sign}은 통합 테스트가 고정 시크릿으로 요청을 만들 때 쓴다 — SDK 와 같은 식이다.
 */
@Slf4j
public class PortOneWebhookVerifier {

    private static final String SECRET_PREFIX = "whsec_";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final WebhookVerifier delegate;
    private final byte[] secret;

    public PortOneWebhookVerifier(String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("포트원 웹훅 시크릿이 비어 있습니다.");
        }
        String raw = webhookSecret.startsWith(SECRET_PREFIX) ? webhookSecret.substring(SECRET_PREFIX.length()) : webhookSecret;
        this.secret = Base64.getDecoder().decode(raw);
        this.delegate = new WebhookVerifier(webhookSecret);
    }

    public boolean verify(byte[] body, String webhookId, String timestamp, String signatureHeader) {
        if (body == null) {
            return false;
        }
        try {
            delegate.verify(new String(body, StandardCharsets.UTF_8), webhookId, signatureHeader, timestamp);
            return true;
        } catch (WebhookVerificationException e) {
            log.debug("웹훅 서명 검증 실패 - {}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            // SDK 는 서명이 맞은 뒤에야 본문을 해석한다 — 여기서 터지면 서명은 맞고 본문 모양이 낯선 것이다. 처리 여부는 서비스가 정한다.
            log.warn("웹훅 본문 해석 실패(서명은 일치) - {}", e.toString());
            return true;
        }
    }

    /** 테스트용 서명 — {@code {id}.{timestamp}.{body}} 의 HMAC-SHA256 base64. */
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

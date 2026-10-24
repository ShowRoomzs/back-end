package showroomz.global.payment.settlement.toss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** 토스 지급대행 웹훅 서명(토스 설계서 1-6) — HMAC-SHA256(보안 키, "{payload}:{transmission-time}") · v1: · 다중 서명. */
@DisplayName("토스 지급대행 웹훅 서명 검증")
class TossPayoutsWebhookVerifierTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final byte[] BODY = "{\"eventType\":\"payout.changed\"}".getBytes(StandardCharsets.UTF_8);
    private static final String TIME = "2026-10-15T10:00:00+09:00";

    private final TossPayoutsWebhookVerifier verifier = new TossPayoutsWebhookVerifier(KEY);

    @Test
    @DisplayName("문서 절차대로 만든 서명(hex 키)과 문자열 키 서명 둘 다 통과 · 쉼표로 여러 개 중 하나만 맞아도 통과")
    void validSignatures() throws Exception {
        String hexKeySignature = verifier.sign(BODY, TIME);
        String stringKeySignature = "v1:" + Base64.getEncoder().encodeToString(hmac(KEY.getBytes(StandardCharsets.US_ASCII)));

        assertThat(verifier.verify(BODY, TIME, hexKeySignature)).isTrue();
        assertThat(verifier.verify(BODY, TIME, stringKeySignature)).isTrue();
        assertThat(verifier.verify(BODY, TIME, "v1:AAAA, " + hexKeySignature)).isTrue();
    }

    @Test
    @DisplayName("본문 · 시간 헤더 변조 · 헤더 누락 · base64 아님 → 거절")
    void tampered() {
        String signature = verifier.sign(BODY, TIME);

        assertThat(verifier.verify("{\"eventType\":\"seller.changed\"}".getBytes(StandardCharsets.UTF_8), TIME, signature)).isFalse();
        assertThat(verifier.verify(BODY, "2026-10-15T10:00:01+09:00", signature)).isFalse();
        assertThat(verifier.verify(BODY, null, signature)).isFalse();
        assertThat(verifier.verify(BODY, TIME, null)).isFalse();
        assertThat(verifier.verify(BODY, TIME, "v1:!!!")).isFalse();
        assertThat(verifier.verify(null, TIME, signature)).isFalse();
    }

    private static byte[] hmac(byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(BODY);
        mac.update((":" + TIME).getBytes(StandardCharsets.UTF_8));
        return mac.doFinal();
    }
}

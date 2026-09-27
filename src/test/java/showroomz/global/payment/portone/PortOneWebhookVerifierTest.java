package showroomz.global.payment.portone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 웹훅 서명 검증(결제 계획서 5-7 ① · 8절) — 정상 · 위조 · 시각 초과 · 여러 서명 중 하나 일치. */
class PortOneWebhookVerifierTest {

    private static final String SECRET = "whsec_aW50ZWdyYXRpb24tdGVzdC13ZWJob29rLXNlY3JldA==";
    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(SECRET, 300);
    private final byte[] body = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"20260927-000001-1\"}}"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("id·timestamp·원문 본문으로 만든 HMAC 이 맞으면 통과한다")
    void validSignaturePasses() {
        Instant now = Instant.now();
        String ts = String.valueOf(now.getEpochSecond());
        String signature = "v1," + verifier.sign(body, "msg_1", ts);

        assertThat(verifier.verify(body, "msg_1", ts, signature, now)).isTrue();
    }

    @Test
    @DisplayName("본문이 한 바이트라도 다르면 거절한다 — 파싱 전 원문으로 검증하는 이유")
    void tamperedBodyFails() {
        Instant now = Instant.now();
        String ts = String.valueOf(now.getEpochSecond());
        String signature = "v1," + verifier.sign(body, "msg_1", ts);
        byte[] tampered = new String(body, StandardCharsets.UTF_8).replace("000001", "000002").getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(tampered, "msg_1", ts, signature, now)).isFalse();
        assertThat(verifier.verify(body, "msg_2", ts, signature, now)).isFalse();
        assertThat(verifier.verify(body, "msg_1", ts, "v1,AAAA", now)).isFalse();
    }

    @Test
    @DisplayName("timestamp 가 허용 오차(5분) 밖이면 서명이 맞아도 거절한다")
    void staleTimestampFails() {
        Instant now = Instant.now();
        String ts = String.valueOf(now.minusSeconds(600).getEpochSecond());
        String signature = "v1," + verifier.sign(body, "msg_1", ts);

        assertThat(verifier.verify(body, "msg_1", ts, signature, now)).isFalse();
    }

    @Test
    @DisplayName("공백으로 구분된 여러 서명 중 하나가 맞으면 통과한다(키 로테이션)")
    void anyOfMultipleSignatures() {
        Instant now = Instant.now();
        String ts = String.valueOf(now.getEpochSecond());
        String signature = "v1,invalid v1," + verifier.sign(body, "msg_1", ts);

        assertThat(verifier.verify(body, "msg_1", ts, signature, now)).isTrue();
    }

    @Test
    @DisplayName("헤더가 하나라도 없으면 거절한다")
    void missingHeadersFail() {
        Instant now = Instant.now();
        assertThat(verifier.verify(body, null, "1", "v1,x", now)).isFalse();
        assertThat(verifier.verify(body, "msg_1", null, "v1,x", now)).isFalse();
        assertThat(verifier.verify(body, "msg_1", "1", null, now)).isFalse();
        assertThat(verifier.verify(body, "msg_1", "not-a-number", "v1,x", now)).isFalse();
    }
}

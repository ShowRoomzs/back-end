package showroomz.api.app.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import showroomz.global.config.properties.SocialLoginProperties;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("애플 토큰 철회 — client_secret 서명 · 키 파싱")
class AppleTokenRevokerTest {

    @Test
    @DisplayName("p8(PEM · 환경변수의 리터럴 \\n) 키로 ES256 client_secret 을 만든다 — iss=Team ID · sub=client_id · aud=appleid · kid=Key ID")
    void signsClientSecret() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded());
        // 환경변수에는 줄바꿈이 리터럴 「\n」으로 들어온다
        String envValue = ("-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----").replace("\n", "\\n");

        SocialLoginProperties.Apple apple = new SocialLoginProperties.Apple();
        apple.setTeamId("TEAM123456");
        apple.setKeyId("KEY1234567");
        apple.setPrivateKey(envValue);
        apple.setRevokeClientId("com.showroomz.app");

        String secret = AppleTokenRevoker.clientSecret(apple, "com.showroomz.app");

        Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(pair.getPublic()).build().parseClaimsJws(secret);
        assertThat(jws.getHeader().getKeyId()).isEqualTo("KEY1234567");
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(jws.getBody().getIssuer()).isEqualTo("TEAM123456");
        assertThat(jws.getBody().getSubject()).isEqualTo("com.showroomz.app");
        assertThat(jws.getBody().getAudience()).isEqualTo("https://appleid.apple.com");
    }

    @Test
    @DisplayName("키가 없거나 코드가 없으면 애플을 부르지 않고 false — 탈퇴는 막지 않는다")
    void skipsWithoutKeyOrCode() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SocialLoginProperties properties = new SocialLoginProperties();
        AppleTokenRevoker revoker = new AppleTokenRevoker(restTemplate, properties);

        assertThat(revoker.revoke(null)).isFalse();
        assertThat(revoker.revoke("code")).isFalse();
        verifyNoInteractions(restTemplate);
    }
}

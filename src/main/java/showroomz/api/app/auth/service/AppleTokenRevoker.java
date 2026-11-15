package showroomz.api.app.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import showroomz.global.config.properties.SocialLoginProperties;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * 탈퇴 시 애플 토큰 철회(App Store 심사 5.1.1(v)) — 앱이 보낸 authorizationCode 를 refresh_token 으로 바꾼 뒤 철회한다.
 *
 * <ol>
 *   <li>{@code POST https://appleid.apple.com/auth/token} — code → refresh_token(없으면 access_token)</li>
 *   <li>{@code POST https://appleid.apple.com/auth/revoke} — 그 토큰 철회</li>
 * </ol>
 *
 * <p><b>실패해도 예외를 던지지 않는다.</b> 탈퇴가 커밋된 뒤에 부르고, 철회 실패로 탈퇴를 되돌리면 회원이 탈퇴할 수 없게 된다.
 * 철회 키가 없으면 건너뛴다(기동 시 WARN).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleTokenRevoker {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String TOKEN_URL = APPLE_ISSUER + "/auth/token";
    private static final String REVOKE_URL = APPLE_ISSUER + "/auth/revoke";
    private static final long CLIENT_SECRET_TTL_SECONDS = 300;

    private final RestTemplate restTemplate;
    private final SocialLoginProperties socialLoginProperties;

    @PostConstruct
    void warnIfNotConfigured() {
        if (!socialLoginProperties.getApple().revokeConfigured()) {
            log.warn("social-login.apple.team-id · key-id · private-key(APPLE_TEAM_ID · APPLE_KEY_ID · APPLE_PRIVATE_KEY)가 "
                    + "비어 있어 탈퇴 시 애플 토큰 철회를 건너뛴다");
        }
    }

    /** @return 철회까지 성공했으면 true. 코드가 없거나 키가 없거나 애플이 거절하면 false(로그만 남긴다). */
    public boolean revoke(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            log.info("애플 토큰 철회 건너뜀 — authorizationCode 없음");
            return false;
        }
        SocialLoginProperties.Apple apple = socialLoginProperties.getApple();
        if (!apple.revokeConfigured()) {
            log.warn("애플 토큰 철회 건너뜀 — 철회 키 미설정");
            return false;
        }
        try {
            String clientId = apple.getRevokeClientId().trim();
            String clientSecret = clientSecret(apple, clientId);

            MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
            tokenForm.add("client_id", clientId);
            tokenForm.add("client_secret", clientSecret);
            tokenForm.add("code", authorizationCode.trim());
            tokenForm.add("grant_type", "authorization_code");
            Map<String, Object> tokens = post(TOKEN_URL, tokenForm);

            String token = tokens == null ? null : (String) tokens.get("refresh_token");
            String hint = "refresh_token";
            if (token == null) {
                token = tokens == null ? null : (String) tokens.get("access_token");
                hint = "access_token";
            }
            if (token == null) {
                log.warn("애플 토큰 철회 실패 — auth/token 응답에 토큰 없음");
                return false;
            }

            MultiValueMap<String, String> revokeForm = new LinkedMultiValueMap<>();
            revokeForm.add("client_id", clientId);
            revokeForm.add("client_secret", clientSecret);
            revokeForm.add("token", token);
            revokeForm.add("token_type_hint", hint);
            post(REVOKE_URL, revokeForm);
            log.info("애플 토큰 철회 완료");
            return true;
        } catch (Exception e) {
            log.warn("애플 토큰 철회 실패: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> post(String url, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }

    /** client_secret — ES256 JWT(iss=Team ID · sub=client_id · aud=https://appleid.apple.com · kid=Key ID · 5분). */
    static String clientSecret(SocialLoginProperties.Apple apple, String clientId) throws Exception {
        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam("kid", apple.getKeyId().trim())
                .setIssuer(apple.getTeamId().trim())
                .setSubject(clientId)
                .setAudience(APPLE_ISSUER)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(CLIENT_SECRET_TTL_SECONDS)))
                .signWith(privateKey(apple.getPrivateKey()), SignatureAlgorithm.ES256)
                .compact();
    }

    /** .p8(PKCS#8 PEM) → EC 개인키. 헤더 · 줄바꿈 유무 · 환경변수의 리터럴 「\n」을 모두 받는다. */
    static PrivateKey privateKey(String pem) throws Exception {
        String body = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    }
}

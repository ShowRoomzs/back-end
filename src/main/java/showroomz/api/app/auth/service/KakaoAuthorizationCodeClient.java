package showroomz.api.app.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import showroomz.global.config.properties.SocialLoginProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 카카오 인가 코드 → 액세스 토큰 교환(웹 로그인).
 *
 * <p>카카오 JS SDK 는 웹에서 액세스 토큰을 직접 주지 않고 인가 코드만 redirect 로 넘긴다. 교환에는 REST API 키(+ 켜 뒀다면 클라이언트
 * 시크릿)가 필요해 브라우저에서 할 수 없으므로 서버가 한다. 받은 액세스 토큰으로는 앱과 같은 경로({@code kapi /v2/user/me})를 탄다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoAuthorizationCodeClient {

    static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

    private final RestTemplate restTemplate;
    private final SocialLoginProperties properties;

    /**
     * @throws BusinessException INVALID_REDIRECT_URI(허용 목록 밖) · INVALID_AUTHORIZATION_CODE(카카오가 코드를 거절 — 만료 · 재사용 ·
     *                           redirect_uri 불일치) · INTERNAL_SERVER_ERROR(키 미설정 · 키 오류 · 카카오 장애)
     */
    public String exchange(String authorizationCode, String redirectUri) {
        SocialLoginProperties.Kakao kakao = properties.getKakao();
        String redirect = redirectUri == null ? "" : redirectUri.trim();
        if (redirect.isEmpty() || !kakao.allowedRedirectUris().contains(redirect)) {
            throw new BusinessException(ErrorCode.INVALID_REDIRECT_URI);
        }
        if (!StringUtils.hasText(kakao.getRestApiKey())) {
            log.error("카카오 인가 코드 로그인 요청이 왔지만 social-login.kakao.rest-api-key(KAKAO_REST_API_KEY)가 비어 있다");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakao.getRestApiKey().trim());
        form.add("redirect_uri", redirect);
        form.add("code", authorizationCode.trim());
        if (StringUtils.hasText(kakao.getClientSecret())) {
            form.add("client_secret", kakao.getClientSecret().trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8));

        Map<String, Object> body;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    TOKEN_URL, HttpMethod.POST, new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            body = response.getBody();
        } catch (HttpClientErrorException e) {
            String error = e.getResponseBodyAsString();
            if (error.contains("invalid_client")) {
                // 키 · 시크릿 설정 문제 — 사용자가 다시 시도해도 풀리지 않는다.
                log.error("카카오 인가 코드 교환 실패(클라이언트 설정) - status: {}, body: {}", e.getStatusCode(), error);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            log.warn("카카오 인가 코드 교환 거절 - status: {}, body: {}", e.getStatusCode(), error);
            throw new BusinessException(ErrorCode.INVALID_AUTHORIZATION_CODE);
        } catch (RestClientException e) {
            log.error("카카오 인가 코드 교환 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Object accessToken = body == null ? null : body.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            log.error("카카오 인가 코드 교환 응답에 access_token 이 없다");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return token;
    }
}

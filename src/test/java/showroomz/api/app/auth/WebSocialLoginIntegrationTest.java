package showroomz.api.app.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.refreshToken.UserRefreshTokenRepository;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.config.properties.SocialLoginProperties;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소비자 웹(showroomz.co.kr) 로그인 — 카카오 인가 코드 교환 · 리프레시 토큰 쿠키.
 *
 * <p>외부 호출(kauth · kapi)은 공용 {@link RestTemplate} 빈에 {@link MockRestServiceServer} 를 붙여 막는다. 같은 빈을 다른 테스트가
 * 쓰므로 끝나면 요청 팩토리를 되돌린다. 카카오 설정값도 공용 빈이라 테스트마다 넣고 되돌린다(컨텍스트를 새로 띄우지 않는다).
 */
@DisplayName("[통합] 소비자 웹 로그인 — 카카오 인가 코드 · 리프레시 쿠키")
class WebSocialLoginIntegrationTest extends IntegrationTestSupport {

    private static final String LOGIN = "/v1/user/auth/social/login";
    private static final String REFRESH = "/v1/user/auth/refresh";
    private static final String LOGOUT = "/v1/user/auth/logout";
    private static final String REDIRECT_URI = "https://showroomz.co.kr/auth/kakao/callback";
    private static final String KAKAO_ID = "4401234567";
    private static final String COOKIE = "showroomz_rt";

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private SocialLoginProperties socialLoginProperties;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserRefreshTokenRepository userRefreshTokenRepository;

    private ClientHttpRequestFactory originalFactory;
    private String originalRestApiKey;
    private List<String> originalRedirectUris;
    private MockRestServiceServer kakao;

    @BeforeEach
    void setUpKakao() {
        originalFactory = restTemplate.getRequestFactory();
        originalRestApiKey = socialLoginProperties.getKakao().getRestApiKey();
        originalRedirectUris = socialLoginProperties.getKakao().getAllowedRedirectUris();
        socialLoginProperties.getKakao().setRestApiKey("test-rest-api-key");
        socialLoginProperties.getKakao().setAllowedRedirectUris(List.of(REDIRECT_URI));
        kakao = MockRestServiceServer.bindTo(restTemplate).build();

        LocalDateTime now = LocalDateTime.now();
        userRepository.save(new Users(KAKAO_ID, "웹유저", "web@showroomz.test", "Y", null,
                ProviderType.KAKAO, RoleType.USER, now, now));
    }

    @AfterEach
    void restoreKakao() {
        restTemplate.setRequestFactory(originalFactory);
        socialLoginProperties.getKakao().setRestApiKey(originalRestApiKey);
        socialLoginProperties.getKakao().setAllowedRedirectUris(originalRedirectUris);
    }

    @Nested
    @DisplayName("카카오 인가 코드 로그인")
    class KakaoAuthorizationCode {

        @Test
        @DisplayName("서버가 인가 코드를 토큰으로 바꾼 뒤 앱과 같은 사용자 조회로 로그인한다")
        void exchangesCodeThenLogsIn() throws Exception {
            expectTokenExchange("kakao-access-token");
            kakao.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                    .andRespond(withSuccess("{\"id\": " + KAKAO_ID + "}", MediaType.APPLICATION_JSON));

            mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("providerType", "KAKAO", "authorizationCode", "auth-code",
                                    "redirectUri", REDIRECT_URI))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.role").value("USER"));
            kakao.verify();
        }

        @Test
        @DisplayName("카카오가 코드를 거절하면(만료 · 재사용) 401 INVALID_AUTHORIZATION_CODE")
        void rejectedCodeIs401() throws Exception {
            kakao.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\"}"));

            mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("providerType", "KAKAO", "authorizationCode", "used-code",
                                    "redirectUri", REDIRECT_URI))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_AUTHORIZATION_CODE"));
        }

        @Test
        @DisplayName("허용 목록 밖의 redirectUri 는 카카오로 보내지 않고 400")
        void unknownRedirectUriIsRejectedBeforeKakao() throws Exception {
            kakao.expect(never(), requestTo("https://kauth.kakao.com/oauth/token"));

            mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("providerType", "KAKAO", "authorizationCode", "auth-code",
                                    "redirectUri", "https://evil.example.com/callback"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REDIRECT_URI"));
            kakao.verify();
        }

        @Test
        @DisplayName("token 도 인가 코드도 없으면 400 — 카카오 외 공급자는 인가 코드를 받지 않는다")
        void missingTokenIs400() throws Exception {
            mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("providerType", "KAKAO"))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("providerType", "NAVER", "authorizationCode", "auth-code",
                                    "redirectUri", REDIRECT_URI))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("웹 리프레시 토큰 쿠키")
    class WebRefreshCookie {

        @Test
        @DisplayName("platform=WEB 로그인은 리프레시 토큰을 바디 대신 httpOnly 쿠키로 내린다")
        void webLoginSetsCookieInsteadOfBody() throws Exception {
            MvcResult result = webLogin();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly", "Secure", "SameSite=Lax",
                    "Path=/v1/user/auth");
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).has("refreshToken")).isFalse();
        }

        @Test
        @DisplayName("앱 로그인(platform 없음)은 지금처럼 바디로 받고 쿠키는 없다")
        void appLoginKeepsBody() throws Exception {
            expectKakaoUser("app-access-token");

            MvcResult result = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("providerType", "KAKAO", "token", "app-access-token"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }

        @Test
        @DisplayName("바디 없이 쿠키로 재발급하면 새 액세스 토큰과 함께 쿠키를 다시 내린다")
        void refreshWithCookie() throws Exception {
            String refreshToken = refreshTokenFromCookie(webLogin());

            MvcResult result = mockMvc.perform(post(REFRESH).cookie(new Cookie(COOKIE, refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=" + refreshToken);
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).has("refreshToken")).isFalse();
        }

        @Test
        @DisplayName("바디도 쿠키도 없으면 400")
        void refreshWithoutAnythingIs400() throws Exception {
            mockMvc.perform(post(REFRESH))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("쿠키로 로그아웃하면 저장된 리프레시 토큰을 지우고 쿠키도 지운다")
        void logoutWithCookie() throws Exception {
            MvcResult login = webLogin();
            String refreshToken = refreshTokenFromCookie(login);
            String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                    .get("accessToken").asText();

            MvcResult result = mockMvc.perform(post(LOGOUT)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .cookie(new Cookie(COOKIE, refreshToken)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=;")
                    .contains("Max-Age=0");
            assertThat(userRefreshTokenRepository.findByRefreshToken(refreshToken)).isNull();
        }

        private MvcResult webLogin() throws Exception {
            expectKakaoUser("web-access-token");
            return mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("providerType", "KAKAO", "token", "web-access-token",
                                    "platform", "WEB"))))
                    .andExpect(status().isOk())
                    .andReturn();
        }

        private String refreshTokenFromCookie(MvcResult result) {
            Cookie cookie = result.getResponse().getCookie(COOKIE);
            assertThat(cookie).isNotNull();
            return cookie.getValue();
        }
    }

    private void expectTokenExchange(String accessToken) {
        kakao.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "test-rest-api-key",
                        "redirect_uri", REDIRECT_URI,
                        "code", "auth-code")))
                .andRespond(withSuccess("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectKakaoUser(String accessToken) {
        kakao.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andRespond(withSuccess("{\"id\": " + KAKAO_ID + "}", MediaType.APPLICATION_JSON));
    }
}

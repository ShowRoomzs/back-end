package showroomz.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import showroomz.auth.DTO.RegisterRequest;
import showroomz.auth.DTO.SocialLoginRequest;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.config.properties.AppProperties;
import showroomz.user.entity.Users;
import showroomz.user.repository.UserRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 네이버 소셜 로그인 통합 테스트
 * 
 * 사용 방법 (Authorization Code 방식):
 * 1. 네이버 개발자 센터(https://developers.naver.com/)에서 앱을 등록하고 Client ID, Client Secret을 발급받습니다.
 * 2. .env 파일에 다음 환경 변수를 설정합니다:
 *    - NAVER_CLIENT_ID=your_client_id
 *    - NAVER_CLIENT_SECRET=your_client_secret
 *    - NAVER_REDIRECT_URI=http://localhost:8080/login/oauth2/code/naver (선택사항, 기본값 사용 가능)
 * 3. testNaverSocialLoginWithAuthorizationCode() 메서드의 authorizationCode 변수에 authorization code를 입력합니다.
 *    - Authorization URL: https://nid.naver.com/oauth2.0/authorize?client_id={CLIENT_ID}&response_type=code&redirect_uri={REDIRECT_URI}
 *    - 위 URL로 접속하여 로그인 후 리다이렉트된 URL의 code 파라미터 값을 복사합니다.
 * 4. @Disabled 어노테이션을 제거하고 테스트를 실행합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private UserRepository userRepository;

    private AuthTokenProvider tokenProvider;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        tokenProvider = new AuthTokenProvider(appProperties.getAuth().getTokenSecret());
        restTemplate = new RestTemplate();
    }

    /**
     * 네이버 Authorization Code를 Access Token으로 교환하는 헬퍼 메서드
     * @param authorizationCode 네이버에서 발급받은 authorization code
     * @return 네이버 access token
     */
    private String exchangeAuthorizationCodeForAccessToken(String authorizationCode) throws Exception {
        String clientId = appProperties.getNaverOAuth2().getClientId();
        String clientSecret = appProperties.getNaverOAuth2().getClientSecret();
        String redirectUri = appProperties.getNaverOAuth2().getRedirectUri();

        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException("네이버 OAuth2 설정이 없습니다. .env 파일에 NAVER_CLIENT_ID와 NAVER_CLIENT_SECRET을 설정해주세요.");
        }

        String tokenUrl = String.format(
                "https://nid.naver.com/oauth2.0/token?grant_type=authorization_code&client_id=%s&client_secret=%s&code=%s",
                URLEncoder.encode(clientId, StandardCharsets.UTF_8.toString()),
                URLEncoder.encode(clientSecret, StandardCharsets.UTF_8.toString()),
                URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8.toString())
        );

        String response = restTemplate.getForObject(tokenUrl, String.class);
        JsonNode jsonNode = objectMapper.readTree(response);

        if (jsonNode.has("error")) {
            throw new RuntimeException("네이버 토큰 발급 실패: " + jsonNode.get("error_description").asText());
        }

        return jsonNode.get("access_token").asText();
    }

    /**
     * 네이버 Authorization URL을 생성하는 헬퍼 메서드
     * @param redirectUri 리다이렉트 URI (null이면 설정값 사용)
     * @return Authorization URL
     */
    private String generateAuthorizationUrl(String redirectUri) throws UnsupportedEncodingException {
        String clientId = appProperties.getNaverOAuth2().getClientId();
        String redirect = redirectUri != null ? redirectUri : appProperties.getNaverOAuth2().getRedirectUri();

        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("네이버 OAuth2 설정이 없습니다. .env 파일에 NAVER_CLIENT_ID를 설정해주세요.");
        }

        return String.format(
                "https://nid.naver.com/oauth2.0/authorize?client_id=%s&response_type=code&redirect_uri=%s",
                URLEncoder.encode(clientId, StandardCharsets.UTF_8.toString()),
                URLEncoder.encode(redirect, StandardCharsets.UTF_8.toString())
        );
    }

    /**
     * 네이버 Authorization URL을 생성하는 헬퍼 메서드 (기본 redirectUri 사용)
     * @return Authorization URL
     */
    private String generateAuthorizationUrl() throws UnsupportedEncodingException {
        return generateAuthorizationUrl(null);
    }

    /**
     * 사용 가능한 포트를 찾는 헬퍼 메서드
     */
    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * 브라우저를 열어서 URL로 이동시키는 헬퍼 메서드
     */
    private void openBrowser(String url) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        Runtime runtime = Runtime.getRuntime();

        if (os.contains("win")) {
            // Windows
            runtime.exec("rundll32 url.dll,FileProtocolHandler " + url);
        } else if (os.contains("mac")) {
            // macOS
            runtime.exec("open " + url);
        } else if (os.contains("nix") || os.contains("nux")) {
            // Linux/Unix
            runtime.exec("xdg-open " + url);
        } else {
            throw new UnsupportedOperationException("지원하지 않는 운영체제입니다: " + os);
        }
    }

    /**
     * Authorization Code를 자동으로 받아오는 헬퍼 메서드
     * 로컬 HTTP 서버를 띄워서 리다이렉트를 받고, 브라우저를 자동으로 열어서 로그인을 유도합니다.
     * @param timeoutSeconds 타임아웃 (초)
     * @return Authorization Code
     */
    private String getAuthorizationCodeAutomatically(int timeoutSeconds) throws Exception {
        // 1. 사용 가능한 포트 찾기
        int port = findAvailablePort();
        String localRedirectUri = "http://localhost:" + port + "/callback";

        // 2. Authorization URL 생성
        String authUrl = generateAuthorizationUrl(localRedirectUri);

        // 3. CompletableFuture로 code를 받을 준비
        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        // 4. 로컬 HTTP 서버 시작
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/callback", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String response;

                if (query != null && query.contains("code=")) {
                    // code 파라미터 추출
                    String code = extractCodeFromQuery(query);
                    codeFuture.complete(code);

                    // 성공 응답
                    response = "<html><body><h1>인증 성공!</h1><p>이 창을 닫아도 됩니다.</p></body></html>";
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                } else if (query != null && query.contains("error=")) {
                    // 에러 응답
                    String error = extractErrorFromQuery(query);
                    codeFuture.completeExceptionally(new RuntimeException("네이버 인증 실패: " + error));

                    response = "<html><body><h1>인증 실패</h1><p>" + error + "</p></body></html>";
                    exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                } else {
                    response = "<html><body><h1>잘못된 요청</h1></body></html>";
                    exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            }
        });
        server.setExecutor(null); // 기본 executor 사용
        server.start();

        try {
            // 5. 브라우저 자동 열기
            System.out.println("\n=== 네이버 로그인 페이지를 열고 있습니다... ===");
            System.out.println("브라우저에서 로그인을 완료해주세요.");
            System.out.println("리다이렉트 URI: " + localRedirectUri + "\n");
            openBrowser(authUrl);

            // 6. code를 받을 때까지 대기 (타임아웃 설정)
            String code = codeFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            System.out.println("Authorization Code를 성공적으로 받았습니다: " + code.substring(0, Math.min(20, code.length())) + "...\n");
            return code;
        } catch (java.util.concurrent.TimeoutException e) {
            codeFuture.cancel(true);
            throw new RuntimeException("타임아웃: " + timeoutSeconds + "초 내에 인증을 완료하지 못했습니다.");
        } finally {
            // 7. 서버 종료
            server.stop(0);
        }
    }

    /**
     * Query string에서 code 파라미터 추출
     */
    private String extractCodeFromQuery(String query) {
        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("code=")) {
                return param.substring(5); // "code=" 제거
            }
        }
        throw new IllegalArgumentException("Query string에 code 파라미터가 없습니다: " + query);
    }

    /**
     * Query string에서 error 파라미터 추출
     */
    private String extractErrorFromQuery(String query) {
        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("error=")) {
                return param.substring(6); // "error=" 제거
            }
        }
        return "알 수 없는 오류";
    }

    @Test
    @Disabled("자동 Authorization Code 발급 테스트 - @Disabled를 제거하고 실행하세요")
    @DisplayName("네이버 소셜 로그인 통합 테스트 - 자동 Authorization Code 발급 및 access token 교환")
    void testNaverSocialLoginWithAutoAuthorizationCode() throws Exception {
        // Authorization Code를 자동으로 받아옵니다 (브라우저가 자동으로 열립니다)
        // 타임아웃: 120초 (2분)
        String authorizationCode = getAuthorizationCodeAutomatically(120);

        // Authorization Code를 Access Token으로 교환
        String naverAccessToken = exchangeAuthorizationCodeForAccessToken(authorizationCode);
        System.out.println("발급받은 네이버 Access Token: " + naverAccessToken.substring(0, Math.min(20, naverAccessToken.length())) + "...\n");

        SocialLoginRequest request = new SocialLoginRequest();
        request.setProviderType("NAVER");
        request.setToken(naverAccessToken);

        // 신규 회원인 경우: registerToken 반환
        // 기존 회원인 경우: accessToken, refreshToken 반환
        mockMvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                // 신규 회원인 경우 registerToken이 있을 수 있고, 기존 회원인 경우 accessToken이 있을 수 있음
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @Disabled("수동 Authorization Code 입력 테스트 - authorization code를 입력한 후 @Disabled를 제거하고 실행하세요")
    @DisplayName("네이버 소셜 로그인 통합 테스트 - 수동 Authorization Code 입력")
    void testNaverSocialLoginWithManualAuthorizationCode() throws Exception {
        // 수동으로 Authorization Code를 입력하는 경우
        String authorizationCode = "YOUR_AUTHORIZATION_CODE_HERE";

        // Authorization Code를 Access Token으로 교환
        String naverAccessToken = exchangeAuthorizationCodeForAccessToken(authorizationCode);
        System.out.println("발급받은 네이버 Access Token: " + naverAccessToken);

        SocialLoginRequest request = new SocialLoginRequest();
        request.setProviderType("NAVER");
        request.setToken(naverAccessToken);

        // 신규 회원인 경우: registerToken 반환
        // 기존 회원인 경우: accessToken, refreshToken 반환
        mockMvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                // 신규 회원인 경우 registerToken이 있을 수 있고, 기존 회원인 경우 accessToken이 있을 수 있음
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @DisplayName("네이버 Authorization URL 생성 테스트")
    void testGenerateNaverAuthorizationUrl() throws Exception {
        try {
            String authUrl = generateAuthorizationUrl();
            System.out.println("\n=== 네이버 Authorization URL ===");
            System.out.println(authUrl);
            System.out.println("위 URL로 접속하여 로그인 후 authorization code를 받으세요.\n");
        } catch (IllegalStateException e) {
            System.out.println("네이버 OAuth2 설정이 없습니다. .env 파일에 NAVER_CLIENT_ID를 설정해주세요.");
        }
    }

    @Test
    @DisplayName("네이버 소셜 로그인 테스트 - 유효하지 않은 토큰")
    void testNaverSocialLoginWithInvalidToken() throws Exception {
        String invalidToken = "invalid_token_12345";

        SocialLoginRequest request = new SocialLoginRequest();
        request.setProviderType("NAVER");
        request.setToken(invalidToken);

        // 유효하지 않은 토큰의 경우 네이버 API 호출 실패로 401 UNAUTHORIZED 반환
        mockMvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized()) // 401
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("네이버 소셜 로그인 테스트 - 토큰 누락")
    void testNaverSocialLoginWithoutToken() throws Exception {
        SocialLoginRequest request = new SocialLoginRequest();
        request.setProviderType("NAVER");
        // token을 설정하지 않음

        mockMvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("token은 필수 입력값입니다."));
    }

    @Test
    @DisplayName("네이버 소셜 로그인 테스트 - providerType 누락")
    void testNaverSocialLoginWithoutProviderType() throws Exception {
        SocialLoginRequest request = new SocialLoginRequest();
        request.setToken("some_token");
        // providerType을 설정하지 않음

        mockMvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("네이버 소셜 로그인 테스트 - 잘못된 providerType")
    void testNaverSocialLoginWithInvalidProviderType() throws Exception {
        SocialLoginRequest request = new SocialLoginRequest();
        request.setProviderType("INVALID_PROVIDER");
        request.setToken("some_token");

        mockMvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SOCIAL_PROVIDER"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 소셜 공급자입니다."));
    }

    @Test
    @Disabled
    @DisplayName("회원가입 완료 테스트 - register token 사용")
    void testRegisterWithValidToken() throws Exception {
        // 1. GUEST 권한의 사용자 생성 (소셜 로그인 후 상태)
        String username = "test_user_" + System.currentTimeMillis();
        Users guestUser = new Users(
                username,
                "임시닉네임",
                username + "@test.com",
                "Y",
                null,
                ProviderType.NAVER,
                RoleType.GUEST,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        userRepository.save(guestUser);

        // 2. register token 생성 (5분 유효)
        Date now = new Date();
        long registerTokenExpiry = 5 * 60 * 1000; // 5분
        AuthToken registerToken = tokenProvider.createAuthToken(
                username,
                new Date(now.getTime() + registerTokenExpiry)
        );

        // 3. RegisterRequest 생성
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setNickname("테스트유저");
        registerRequest.setGender("MALE");
        registerRequest.setBirthday("1990-01-15");
        registerRequest.setServiceAgree(true);
        registerRequest.setPrivacyAgree(true);
        registerRequest.setMarketingAgree(true);

        // 디버깅: JSON 직렬화 결과 확인
        String requestBody = objectMapper.writeValueAsString(registerRequest);
        System.out.println("Request JSON: " + requestBody);
        System.out.println("serviceAgree: " + registerRequest.isServiceAgree());
        System.out.println("privacyAgree: " + registerRequest.isPrivacyAgree());

        // 4. 회원가입 완료 요청
        String responseContent = mockMvc.perform(post("/v1/auth/register")
                        .header("Authorization", "Bearer " + registerToken.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        System.out.println("Response Content: " + responseContent);
        
        mockMvc.perform(post("/v1/auth/register")
                        .header("Authorization", "Bearer " + registerToken.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated()) // 201
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresIn").exists())
                .andExpect(jsonPath("$.refreshTokenExpiresIn").exists());

        // 5. 사용자가 USER 권한으로 변경되었는지 확인
        Optional<Users> optionalUser = userRepository.findByUsername(username);
        assertTrue(optionalUser.isPresent());
        Users updatedUser = optionalUser.get();
        assertEquals(RoleType.USER, updatedUser.getRoleType());
        assertEquals("테스트유저", updatedUser.getNickname());
        assertEquals("MALE", updatedUser.getGender());
        assertEquals("1990-01-15", updatedUser.getBirthday());
    }

    @Test
    @DisplayName("회원가입 완료 테스트 - register token 누락")
    void testRegisterWithoutToken() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setNickname("테스트유저");
        registerRequest.setServiceAgree(true);
        registerRequest.setPrivacyAgree(true);

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized()) // 401
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요."));
    }

    @Test
    @DisplayName("회원가입 완료 테스트 - 닉네임 중복")
    void testRegisterWithDuplicateNickname() throws Exception {
        // 1. 이미 사용 중인 닉네임으로 사용자 생성
        String existingNickname = "중복닉네임";
        Users existingUser = new Users(
                "existing_user_" + System.currentTimeMillis(),
                existingNickname,
                "existing@test.com",
                "Y",
                null,
                ProviderType.NAVER,
                RoleType.USER,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        userRepository.save(existingUser);

        // 2. GUEST 권한의 새 사용자 생성
        String username = "test_user_" + System.currentTimeMillis();
        Users guestUser = new Users(
                username,
                "임시닉네임",
                username + "@test.com",
                "Y",
                null,
                ProviderType.NAVER,
                RoleType.GUEST,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        userRepository.save(guestUser);

        // 3. register token 생성
        Date now = new Date();
        long registerTokenExpiry = 5 * 60 * 1000;
        AuthToken registerToken = tokenProvider.createAuthToken(
                username,
                new Date(now.getTime() + registerTokenExpiry)
        );

        // 4. 중복된 닉네임으로 회원가입 시도
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setNickname(existingNickname);
        registerRequest.setServiceAgree(true);
        registerRequest.setPrivacyAgree(true);

        mockMvc.perform(post("/v1/auth/register")
                        .header("Authorization", "Bearer " + registerToken.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andDo(print())
                .andExpect(status().isConflict()) // 409
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("회원가입 완료 테스트 - 생년월일 형식 오류")
    void testRegisterWithInvalidBirthdayFormat() throws Exception {
        // 1. GUEST 권한의 사용자 생성
        String username = "test_user_" + System.currentTimeMillis();
        Users guestUser = new Users(
                username,
                "임시닉네임",
                username + "@test.com",
                "Y",
                null,
                ProviderType.NAVER,
                RoleType.GUEST,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        userRepository.save(guestUser);

        // 2. register token 생성
        Date now = new Date();
        long registerTokenExpiry = 5 * 60 * 1000;
        AuthToken registerToken = tokenProvider.createAuthToken(
                username,
                new Date(now.getTime() + registerTokenExpiry)
        );

        // 3. 잘못된 형식의 생년월일로 회원가입 시도
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setNickname("테스트유저");
        registerRequest.setBirthday("1990/01/15"); // 잘못된 형식
        registerRequest.setServiceAgree(true);
        registerRequest.setPrivacyAgree(true);

        mockMvc.perform(post("/v1/auth/register")
                        .header("Authorization", "Bearer " + registerToken.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest()) // 400
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("회원가입 완료 테스트 - 이미 회원가입 완료된 사용자")
    void testRegisterAlreadyRegisteredUser() throws Exception {
        // 1. 이미 USER 권한을 가진 사용자 생성
        String username = "test_user_" + System.currentTimeMillis();
        Users registeredUser = new Users(
                username,
                "이미가입된유저",
                username + "@test.com",
                "Y",
                null,
                ProviderType.NAVER,
                RoleType.USER, // 이미 USER 권한
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        userRepository.save(registeredUser);

        // 2. register token 생성 (만약 있다고 가정)
        Date now = new Date();
        long registerTokenExpiry = 5 * 60 * 1000;
        AuthToken registerToken = tokenProvider.createAuthToken(
                username,
                new Date(now.getTime() + registerTokenExpiry)
        );

        // 3. 이미 회원가입 완료된 사용자가 다시 회원가입 시도
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setNickname("새닉네임");
        registerRequest.setServiceAgree(true);
        registerRequest.setPrivacyAgree(true);

        mockMvc.perform(post("/v1/auth/register")
                        .header("Authorization", "Bearer " + registerToken.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest()) // 400
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("이미 회원가입이 완료된 사용자입니다."));
    }
}


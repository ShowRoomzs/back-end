package showroomz.auth.service;
// package showroomz.oauthlogin.auth;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.context.annotation.Import;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;

// import showroomz.config.properties.AppProperties;
// import showroomz.oauthlogin.oauth.exception.GlobalExceptionHandler;
// import showroomz.oauthlogin.auth.DTO.SocialLoginRequest;
// import showroomz.oauthlogin.oauth.entity.ProviderType;
// import showroomz.oauthlogin.oauth.entity.RoleType;
// import showroomz.oauthlogin.oauth.service.SocialLoginService;
// import showroomz.oauthlogin.oauth.service.SocialLoginService.SocialLoginResult;
// import showroomz.oauthlogin.oauth.token.AuthToken;
// import showroomz.oauthlogin.oauth.token.AuthTokenProvider;
// import showroomz.oauthlogin.user.User;

// import java.time.LocalDateTime;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.when;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest(controllers = AuthController.class, 
//         excludeAutoConfiguration = {
//             org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
//         })
// @AutoConfigureMockMvc(addFilters = false)
// @Import(GlobalExceptionHandler.class)
// @DisplayName("네이버 소셜 로그인 테스트")
// class NaverSocialLoginTest {

//     @Autowired
//     private MockMvc mockMvc;

//     @Autowired
//     private ObjectMapper objectMapper;

//     @MockBean
//     private SocialLoginService socialLoginService;

//     @MockBean
//     private AppProperties appProperties;

//     @MockBean
//     private AuthTokenProvider tokenProvider;

//     @MockBean
//     private org.springframework.security.authentication.AuthenticationManager authenticationManager;

//     @MockBean
//     private showroomz.oauthlogin.auth.UserRefreshTokenRepository userRefreshTokenRepository;

//     @MockBean
//     private showroomz.oauthlogin.auth.UserRepository userRepository;

//     @MockBean
//     private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

//     @BeforeEach
//     void setUp() {
//         setupAppProperties();
//         setupTokenProvider();
//         setupUserRefreshTokenRepository();
//     }

//     private void setupAppProperties() {
//         AppProperties.Auth auth = new AppProperties.Auth();
//         auth.setTokenSecret("test-secret-key-for-jwt-token-generation-minimum-32-characters");
//         auth.setTokenExpiry(1800000L); // 30분
//         auth.setRefreshTokenExpiry(604800000L); // 7일
//         when(appProperties.getAuth()).thenReturn(auth);
//     }

//     private void setupTokenProvider() {
//         when(tokenProvider.createAuthToken(anyString(), anyString(), any(java.util.Date.class)))
//                 .thenAnswer(invocation -> {
//                     String token = "mock_access_token_" + System.currentTimeMillis();
//                     AuthToken authToken = org.mockito.Mockito.mock(AuthToken.class);
//                     org.mockito.Mockito.when(authToken.getToken()).thenReturn(token);
//                     return authToken;
//                 });
//         when(tokenProvider.createAuthToken(anyString(), any(java.util.Date.class)))
//                 .thenAnswer(invocation -> {
//                     String token = "mock_refresh_token_" + System.currentTimeMillis();
//                     AuthToken authToken = org.mockito.Mockito.mock(AuthToken.class);
//                     org.mockito.Mockito.when(authToken.getToken()).thenReturn(token);
//                     return authToken;
//                 });
//     }

//     private void setupUserRefreshTokenRepository() {
//         when(userRefreshTokenRepository.findByUserId(anyString()))
//                 .thenReturn(null);
//         when(userRefreshTokenRepository.saveAndFlush(any(showroomz.oauthlogin.auth.UserRefreshToken.class)))
//                 .thenAnswer(invocation -> invocation.getArgument(0));
//     }

//     @Test
//     @DisplayName("기존 회원 네이버 로그인 성공")
//     void 기존_회원_네이버_로그인_성공() throws Exception {
//         // given
//         String accessToken = "naver_access_token_12345";
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setProviderType("NAVER");
//         request.setToken(accessToken);

//         User existingUser = createUser("naver_user_123", "테스트유저", "test@naver.com", ProviderType.NAVER);
//         SocialLoginResult result = new SocialLoginResult(existingUser, false);

//         when(socialLoginService.loginOrSignup(eq(ProviderType.NAVER), eq(accessToken)))
//                 .thenReturn(result);

//         // when & then
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.tokenType").value("Bearer"))
//                 .andExpect(jsonPath("$.accessToken").exists())
//                 .andExpect(jsonPath("$.refreshToken").exists())
//                 .andExpect(jsonPath("$.accessTokenExpiresIn").exists())
//                 .andExpect(jsonPath("$.refreshTokenExpiresIn").exists())
//                 .andExpect(jsonPath("$.isNewMember").value(false));
//     }

//     @Test
//     @DisplayName("신규 회원 네이버 로그인 성공 - registerToken 반환")
//     void 신규_회원_네이버_로그인_성공() throws Exception {
//         // given
//         String accessToken = "naver_access_token_new_user";
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setProviderType("NAVER");
//         request.setToken(accessToken);

//         User newUser = createUser("naver_new_user_456", "신규유저", "new@naver.com", ProviderType.NAVER);
//         SocialLoginResult result = new SocialLoginResult(newUser, true);

//         when(socialLoginService.loginOrSignup(eq(ProviderType.NAVER), eq(accessToken)))
//                 .thenReturn(result);

//         // when & then
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.isNewMember").value(true))
//                 .andExpect(jsonPath("$.registerToken").exists())
//                 .andExpect(jsonPath("$.accessToken").doesNotExist())
//                 .andExpect(jsonPath("$.refreshToken").doesNotExist());
//     }

//     @Test
//     @DisplayName("토큰 누락 시 400 에러")
//     void 토큰_누락_시_400_에러() throws Exception {
//         // given
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setProviderType("NAVER");
//         // token 미설정 (null)

//         // when & then
//         // @Valid가 실패하면 Spring이 자동으로 에러를 반환하지만, 
//         // 컨트롤러 내부의 null 체크가 먼저 실행되므로 우리의 에러 응답이 반환됨
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isBadRequest())
//                 .andExpect(jsonPath("$.code").exists())
//                 .andExpect(jsonPath("$.message").exists());
//     }

//     @Test
//     @DisplayName("providerType 누락 시 400 에러")
//     void providerType_누락_시_400_에러() throws Exception {
//         // given
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setToken("naver_access_token");
//         // providerType 미설정 (null)

//         // when & then
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isBadRequest())
//                 .andExpect(jsonPath("$.code").exists())
//                 .andExpect(jsonPath("$.message").exists());
//     }

//     @Test
//     @DisplayName("잘못된 providerType 시 400 에러")
//     void 잘못된_providerType_시_400_에러() throws Exception {
//         // given
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setProviderType("INVALID_PROVIDER");
//         request.setToken("naver_access_token");

//         // when & then
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isBadRequest())
//                 .andExpect(jsonPath("$.code").value("INVALID_SOCIAL_PROVIDER"))
//                 .andExpect(jsonPath("$.message").value("지원하지 않는 소셜 공급자입니다."));
//     }

//     @Test
//     @DisplayName("유효하지 않은 토큰 시 401 에러")
//     void 유효하지_않은_토큰_시_401_에러() throws Exception {
//         // given
//         String invalidToken = "invalid_naver_token";
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setProviderType("NAVER");
//         request.setToken(invalidToken);

//         when(socialLoginService.loginOrSignup(eq(ProviderType.NAVER), eq(invalidToken)))
//                 .thenThrow(new IllegalArgumentException("유효하지 않은 액세스 토큰입니다."));

//         // when & then
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isUnauthorized())
//                 .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
//                 .andExpect(jsonPath("$.message").value("유효하지 않은 액세스 토큰입니다."));
//     }

//     @Test
//     @DisplayName("FCM 토큰 포함 요청 성공")
//     void FCM_토큰_포함_요청_성공() throws Exception {
//         // given
//         String accessToken = "naver_access_token_with_fcm";
//         String fcmToken = "fcm_token_12345";
        
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setProviderType("NAVER");
//         request.setToken(accessToken);
//         request.setFcmToken(fcmToken);

//         User user = createUser("naver_user_fcm", "FCM유저", "fcm@naver.com", ProviderType.NAVER);
//         SocialLoginResult result = new SocialLoginResult(user, false);

//         when(socialLoginService.loginOrSignup(eq(ProviderType.NAVER), eq(accessToken)))
//                 .thenReturn(result);

//         // when & then
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.tokenType").value("Bearer"))
//                 .andExpect(jsonPath("$.isNewMember").value(false));
//     }

//     @Test
//     @DisplayName("소문자 providerType도 정상 처리")
//     void 소문자_providerType_도_정상_처리() throws Exception {
//         // given
//         String accessToken = "naver_access_token";
//         SocialLoginRequest request = new SocialLoginRequest();
//         request.setProviderType("naver"); // 소문자
//         request.setToken(accessToken);

//         User user = createUser("naver_user_lower", "소문자유저", "lower@naver.com", ProviderType.NAVER);
//         SocialLoginResult result = new SocialLoginResult(user, false);

//         when(socialLoginService.loginOrSignup(eq(ProviderType.NAVER), eq(accessToken)))
//                 .thenReturn(result);

//         // when & then
//         mockMvc.perform(post("/v1/auth/social/login")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.tokenType").value("Bearer"));
//     }

//     // Helper method
//     private User createUser(String userId, String nickname, String email, ProviderType providerType) {
//         LocalDateTime now = LocalDateTime.now();
//         return new User(
//                 userId,
//                 nickname,
//                 email,
//                 "Y",
//                 "",
//                 providerType,
//                 RoleType.USER,
//                 now,
//                 now
//         );
//     }
// }


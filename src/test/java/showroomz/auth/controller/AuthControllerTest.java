package showroomz.auth.controller;

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

import showroomz.auth.DTO.RegisterRequest;
import showroomz.auth.DTO.SocialLoginRequest;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.config.properties.AppProperties;
import showroomz.user.entity.Users;
import showroomz.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 네이버 소셜 로그인 통합 테스트
 * 
 * 사용 방법:
 * 1. 네이버 개발자 센터(https://developers.naver.com/)에서 앱을 등록하고 Client ID, Client Secret을 발급받습니다.
 * 2. 네이버 로그인 API를 사용하여 access token을 발급받습니다.
 *    - 테스트용 URL: https://nid.naver.com/oauth2.0/authorize?response_type=token&client_id={YOUR_CLIENT_ID}&redirect_uri={YOUR_REDIRECT_URI}&state=test
 *    - 또는 Postman 등을 사용하여 access token을 발급받습니다.
 * 3. 아래 testNaverSocialLoginWithRealToken() 메서드의 naverAccessToken 변수에 발급받은 토큰을 입력합니다.
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

    @BeforeEach
    void setUp() {
        tokenProvider = new AuthTokenProvider(appProperties.getAuth().getTokenSecret());
    }

    @Test
    @Disabled("실제 네이버 access token을 입력한 후 @Disabled를 제거하고 실행하세요")
    @DisplayName("네이버 소셜 로그인 통합 테스트 - 실제 access token 사용")
    void testNaverSocialLoginWithRealToken() throws Exception {
        // TODO: 아래 값을 실제 네이버 access token으로 변경하세요
        // 네이버 개발자센터에서 발급받은 access token을 여기에 입력하세요
        // 
        // 네이버 access token 발급 방법:
        // 1. 네이버 개발자 센터(https://developers.naver.com/) 접속
        // 2. 애플리케이션 등록 및 로그인 오픈 API 서비스 환경 설정
        // 3. 다음 URL로 접속하여 로그인 후 access token 발급
        //    https://nid.naver.com/oauth2.0/authorize?response_type=token&client_id={YOUR_CLIENT_ID}&redirect_uri={YOUR_REDIRECT_URI}&state=test
        // 4. 리다이렉트된 URL의 hash fragment(#access_token=...)에서 access_token 값을 복사
        String naverAccessToken = "AAAAOA8sE03fxm1ZJb_Irmm7148z2XLUetmCbfLYDQhPBc4D1FUvFPhqpyPZZuFC0vM6gnmwNZE0--8oXxMK84w5rMc";

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

        // 4. 회원가입 완료 요청
        mockMvc.perform(post("/v1/auth/register")
                        .header("Authorization", "Bearer " + registerToken.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
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


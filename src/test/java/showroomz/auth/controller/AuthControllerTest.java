package showroomz.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

        // 3. RegisterRequest 생성 (고유한 닉네임 사용)
        String uniqueNickname = "홍길동" + (System.currentTimeMillis() % 1000000);
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setNickname(uniqueNickname);
        registerRequest.setGender("MALE");
        registerRequest.setBirthday("1990-01-15");
        registerRequest.setServiceAgree(true);
        registerRequest.setPrivacyAgree(true);
        registerRequest.setMarketingAgree(true);

        // 4. 회원가입 완료 요청
        String requestBody = objectMapper.writeValueAsString(registerRequest);
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
        assertEquals(uniqueNickname, updatedUser.getNickname());
        assertEquals("MALE", updatedUser.getGender());
        assertEquals("1990-01-15", updatedUser.getBirthday());
    }

    @Test
    @DisplayName("회원가입 완료 테스트 - register token 누락")
    void testRegisterWithoutToken() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setNickname("홍길동" + (System.currentTimeMillis() % 1000000));
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
        registerRequest.setNickname("홍길동" + (System.currentTimeMillis() % 1000000));
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


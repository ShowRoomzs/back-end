package showroomz.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import showroomz.auth.DTO.RefreshTokenRequest;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.refreshToken.UserRefreshToken;
import showroomz.auth.refreshToken.UserRefreshTokenRepository;
import showroomz.auth.service.AuthService;
import showroomz.auth.service.SocialLoginService;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.config.properties.AppProperties;
import showroomz.user.entity.Users;
import showroomz.user.repository.UserRepository;
import showroomz.user.service.UserService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter Chain 비활성화
@DisplayName("AuthController 로그아웃/탈퇴 테스트")
class AuthControllerTest2 {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthTokenProvider tokenProvider;

    @MockBean
    private UserRefreshTokenRepository userRefreshTokenRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private SocialLoginService socialLoginService;

    @MockBean
    private AuthService authService;

    @MockBean
    private AppProperties appProperties; 

    @Test
    @DisplayName("로그아웃 성공")
    void logout_Success() throws Exception {
        // given
        String accessToken = "valid_access_token";
        String refreshToken = "valid_refresh_token";
        
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        // Access Token 검증 Mocking
        AuthToken mockAuthToken = mock(AuthToken.class);
        given(tokenProvider.convertAuthToken(accessToken)).willReturn(mockAuthToken);
        given(mockAuthToken.validate()).willReturn(true);

        // when & then
        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다."));

        // 리포지토리 삭제 호출 검증
        verify(userRefreshTokenRepository).deleteByRefreshToken(refreshToken);
    }

    @Test
    @DisplayName("로그아웃 실패 - Access Token 누락")
    void logout_Fail_NoAccessToken() throws Exception {
        // given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("some_token");

        // when & then
        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("로그아웃 실패 - 유효하지 않은 Access Token")
    void logout_Fail_InvalidAccessToken() throws Exception {
        // given
        String accessToken = "invalid_token";
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid_refresh_token");

        AuthToken mockAuthToken = mock(AuthToken.class);
        given(tokenProvider.convertAuthToken(accessToken)).willReturn(mockAuthToken);
        given(mockAuthToken.validate()).willReturn(false); // 유효성 검증 실패

        // when & then
        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("로그아웃 실패 - Refresh Token 누락")
    void logout_Fail_NoRefreshToken() throws Exception {
        // given
        String accessToken = "valid_access_token";
        RefreshTokenRequest request = new RefreshTokenRequest(); // RefreshToken null

        AuthToken mockAuthToken = mock(AuthToken.class);
        given(tokenProvider.convertAuthToken(accessToken)).willReturn(mockAuthToken);
        given(mockAuthToken.validate()).willReturn(true);

        // when & then
        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    void withdraw_Success() throws Exception {
        // given
        String accessToken = "valid_access_token";
        String username = "testUser";

        // Access Token & Claims Mocking
        AuthToken mockAuthToken = mock(AuthToken.class);
        Claims mockClaims = mock(Claims.class);
        
        given(tokenProvider.convertAuthToken(accessToken)).willReturn(mockAuthToken);
        given(mockAuthToken.validate()).willReturn(true);
        given(mockAuthToken.getTokenClaims()).willReturn(mockClaims);
        given(mockClaims.getSubject()).willReturn(username);

        // User Mocking
        Users user = new Users(
                username, "nickname", "email@test.com", "Y", null,
                ProviderType.NAVER, RoleType.USER, LocalDateTime.now(), LocalDateTime.now()
        );
        given(userRepository.findByUsername(username)).willReturn(Optional.of(user));

        // Refresh Token Mocking
        UserRefreshToken userRefreshToken = new UserRefreshToken(username, "refreshToken");
        given(userRefreshTokenRepository.findByUserId(username)).willReturn(userRefreshToken);

        // when & then
        mockMvc.perform(delete("/v1/auth/withdraw")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));

        // 삭제 로직 호출 검증
        verify(userRefreshTokenRepository).delete(userRefreshToken);
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 사용자 정보 없음")
    void withdraw_Fail_UserNotFound() throws Exception {
        // given
        String accessToken = "valid_access_token";
        String username = "unknownUser";

        AuthToken mockAuthToken = mock(AuthToken.class);
        Claims mockClaims = mock(Claims.class);

        given(tokenProvider.convertAuthToken(accessToken)).willReturn(mockAuthToken);
        given(mockAuthToken.validate()).willReturn(true);
        given(mockAuthToken.getTokenClaims()).willReturn(mockClaims);
        given(mockClaims.getSubject()).willReturn(username);

        // User Not Found Mocking
        given(userRepository.findByUsername(username)).willReturn(Optional.empty());

        // when & then
        mockMvc.perform(delete("/v1/auth/withdraw")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
    
    @Test
    @DisplayName("회원 탈퇴 실패 - 토큰 검증 실패")
    void withdraw_Fail_InvalidToken() throws Exception {
        // given
        String accessToken = "invalid_token";

        AuthToken mockAuthToken = mock(AuthToken.class);
        given(tokenProvider.convertAuthToken(accessToken)).willReturn(mockAuthToken);
        given(mockAuthToken.validate()).willReturn(false);

        // when & then
        mockMvc.perform(delete("/v1/auth/withdraw")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
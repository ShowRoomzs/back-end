package showroomz.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import showroomz.admin.DTO.AdminLoginRequest;
import showroomz.admin.DTO.AdminSignUpRequest;
import showroomz.admin.service.AdminService;
import showroomz.auth.DTO.RefreshTokenRequest;
import showroomz.auth.DTO.TokenResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminAuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter Chain 비활성화 (순수 컨트롤러 로직만 테스트)
@DisplayName("AdminController 단위 테스트")
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    @Test
    @DisplayName("관리자 회원가입 성공")
    void registerAdmin_Success() throws Exception {
        // given
        AdminSignUpRequest request = new AdminSignUpRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setSellerName("김담당");
        request.setSellerContact("010-1234-5678");
        request.setMarketName("테스트마켓");
        request.setCsNumber("02-1234-5678");

        // when & then
        mockMvc.perform(post("/v1/admin/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("관리자 회원가입이 완료되었습니다."));

        verify(adminService).registerAdmin(any(AdminSignUpRequest.class));
    }

    @Test
    @DisplayName("이메일 중복 체크 - 중복인 경우 true 반환")
    void checkEmail_Duplicate() throws Exception {
        // given
        String email = "duplicate@test.com";
        given(adminService.checkEmailDuplicate(email)).willReturn(true);

        // when & then
        mockMvc.perform(get("/v1/admin/check-email")
                        .param("email", email))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("마켓명 중복 체크 - 사용 가능한 경우 false 반환")
    void checkMarketName_Available() throws Exception {
        // given
        String marketName = "newMarket";
        given(adminService.checkMarketNameDuplicate(marketName)).willReturn(false);

        // when & then
        mockMvc.perform(get("/v1/admin/check-market-name")
                        .param("marketName", marketName))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("로그인 성공")
    void login_Success() throws Exception {
        // given
        AdminLoginRequest request = new AdminLoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Password123!");

        TokenResponse tokenResponse = new TokenResponse(
                "accessToken", "refreshToken", 3600L, 1209600L, false
        );

        given(adminService.login(any(AdminLoginRequest.class))).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken"));
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void refreshToken_Success() throws Exception {
        // given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("validRefreshToken");

        TokenResponse tokenResponse = new TokenResponse(
                "newAccessToken", "validRefreshToken", 3600L, 1209600L, false
        );

        given(adminService.refreshToken(any(RefreshTokenRequest.class))).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/v1/admin/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccessToken"));
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_Success() throws Exception {
        // given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refreshToken");
        
        String accessToken = "Bearer validAccessToken";

        // when & then
        mockMvc.perform(post("/v1/admin/logout")
                        .header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다."));

        // HeaderUtil이 "Bearer "를 떼어내고 토큰만 서비스에 전달한다고 가정할 때의 검증
        // 실제 HeaderUtil 구현에 따라 두 번째 인자 값은 달라질 수 있습니다.
        // 여기서는 서비스 메서드가 호출되었는지만 확인합니다.
        verify(adminService).logout(any(), eq("refreshToken"));
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    void withdraw_Success() throws Exception {
        // given
        String accessToken = "Bearer validAccessToken";

        // when & then
        mockMvc.perform(delete("/v1/admin/withdraw")
                        .header("Authorization", accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("관리자 회원 탈퇴가 완료되었습니다."));

        verify(adminService).withdraw(any());
    }
}
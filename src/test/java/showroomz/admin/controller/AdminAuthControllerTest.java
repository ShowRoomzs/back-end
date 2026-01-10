package showroomz.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import showroomz.api.app.auth.DTO.RefreshTokenRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.DTO.SellerLoginRequest;
import showroomz.api.seller.auth.DTO.SellerSignUpRequest;
import showroomz.api.seller.auth.controller.SellerAuthController;
import showroomz.api.seller.auth.service.SellerService;
import showroomz.global.error.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SellerAuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter Chain 비활성화 (순수 컨트롤러 로직만 테스트)
@Import(GlobalExceptionHandler.class) // GlobalExceptionHandler를 import하여 validation 에러 처리 활성화
    @DisplayName("SellerAuthController 단위 테스트")
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SellerService adminService;

    @Test
    @DisplayName("관리자 회원가입 성공 - 승인 대기 메시지 반환")
    void registerAdmin_Success() throws Exception {
        // given
        SellerSignUpRequest request = new SellerSignUpRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setSellerName("김담당");
        request.setSellerContact("010-1234-5678");
        request.setMarketName("테스트마켓");
        request.setCsNumber("02-1234-5678");

        java.util.Map<String, String> responseBody = java.util.Map.of(
                "message", "회원가입 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다."
        );

        given(adminService.registerAdmin(any(SellerSignUpRequest.class))).willReturn(responseBody);

        // when & then
        mockMvc.perform(post("/v1/seller/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다."));

        verify(adminService).registerAdmin(any(SellerSignUpRequest.class));
    }

    @Test
    @DisplayName("이메일 중복 체크 - 사용 가능한 경우")
    void checkEmail_Available() throws Exception {
        // given
        String email = "available@test.com";
        SellerDto.CheckEmailResponse response = new SellerDto.CheckEmailResponse(true, "AVAILABLE", "사용 가능한 이메일입니다.");
        given(adminService.checkEmailDuplicate(email)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/seller/auth/check-email")
                        .param("email", email))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true)) // Jackson이 isAvailable을 available로 직렬화
                .andExpect(jsonPath("$.code").value("AVAILABLE"))
                .andExpect(jsonPath("$.message").value("사용 가능한 이메일입니다."));

        verify(adminService).checkEmailDuplicate(email);
    }

    @Test
    @DisplayName("이메일 중복 체크 - 중복인 경우")
    void checkEmail_Duplicate() throws Exception {
        // given
        String email = "duplicate@test.com";
        SellerDto.CheckEmailResponse response = new SellerDto.CheckEmailResponse(false, "DUPLICATE", "이미 사용 중인 이메일입니다.");
        given(adminService.checkEmailDuplicate(email)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/seller/auth/check-email")
                        .param("email", email))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false)) // Jackson이 isAvailable을 available로 직렬화
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));

        verify(adminService).checkEmailDuplicate(email);
    }


    @Test
    @DisplayName("로그인 성공")
    void login_Success() throws Exception {
        // given
        SellerLoginRequest request = new SellerLoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Password123!");

        TokenResponse tokenResponse = new TokenResponse(
                "accessToken", "refreshToken", 3600L, 1209600L, false, "SELLER"
        );

        given(adminService.login(any(SellerLoginRequest.class))).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/v1/seller/auth/login")
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
                "newAccessToken", "validRefreshToken", 3600L, 1209600L, false, "ADMIN"
        );

        given(adminService.refreshToken(any(RefreshTokenRequest.class))).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/v1/seller/auth/refresh")
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
        mockMvc.perform(post("/v1/seller/auth/logout")
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
        mockMvc.perform(delete("/v1/seller/auth/withdraw")
                        .header("Authorization", accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("관리자 회원 탈퇴가 완료되었습니다."));

        verify(adminService).withdraw(any());
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 형식 오류")
    void registerAdmin_InvalidEmail() throws Exception {
        // given
        SellerSignUpRequest request = new SellerSignUpRequest();
        request.setEmail("invalid-email"); // 유효하지 않은 이메일 형식
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setSellerName("김담당");
        request.setSellerContact("010-1234-5678");
        request.setMarketName("테스트마켓");
        request.setCsNumber("02-1234-5678");

        // when & then
        mockMvc.perform(post("/v1/seller/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).registerAdmin(any());
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호 형식 오류")
    void registerAdmin_InvalidPassword() throws Exception {
        // given
        SellerSignUpRequest request = new SellerSignUpRequest();
        request.setEmail("admin@test.com");
        request.setPassword("1234"); // 8자 미만, 영문/특수문자 없음
        request.setPasswordConfirm("1234");
        request.setSellerName("김담당");
        request.setSellerContact("010-1234-5678");
        request.setMarketName("테스트마켓");
        request.setCsNumber("02-1234-5678");

        // when & then
        mockMvc.perform(post("/v1/seller/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).registerAdmin(any());
    }

    @Test
    @DisplayName("회원가입 실패 - 필수값 누락 (이메일)")
    void registerAdmin_MissingEmail() throws Exception {
        // given
        SellerSignUpRequest request = new SellerSignUpRequest();
        // email 누락
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setSellerName("김담당");
        request.setSellerContact("010-1234-5678");
        request.setMarketName("테스트마켓");
        request.setCsNumber("02-1234-5678");

        // when & then
        mockMvc.perform(post("/v1/seller/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).registerAdmin(any());
    }

    @Test
    @DisplayName("회원가입 실패 - 연락처 형식 오류")
    void registerAdmin_InvalidContact() throws Exception {
        // given
        SellerSignUpRequest request = new SellerSignUpRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setSellerName("김담당");
        request.setSellerContact("123-456-789"); // 잘못된 형식
        request.setMarketName("테스트마켓");
        request.setCsNumber("02-1234-5678");

        // when & then
        mockMvc.perform(post("/v1/seller/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).registerAdmin(any());
    }

    @Test
    @DisplayName("회원가입 실패 - 마켓명 형식 오류 (공백 포함)")
    void registerAdmin_InvalidMarketName() throws Exception {
        // given
        SellerSignUpRequest request = new SellerSignUpRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setSellerName("김담당");
        request.setSellerContact("010-1234-5678");
        request.setMarketName("테스트 마켓"); // 공백 포함
        request.setCsNumber("02-1234-5678");

        // when & then
        mockMvc.perform(post("/v1/seller/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).registerAdmin(any());
    }

    @Test
    @DisplayName("회원가입 실패 - 고객센터 번호 형식 오류")
    void registerAdmin_InvalidCsNumber() throws Exception {
        // given
        SellerSignUpRequest request = new SellerSignUpRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setSellerName("김담당");
        request.setSellerContact("010-1234-5678");
        request.setMarketName("테스트마켓");
        request.setCsNumber("12345678"); // 잘못된 형식

        // when & then
        mockMvc.perform(post("/v1/seller/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).registerAdmin(any());
    }

    @Test
    @DisplayName("로그인 실패 - 이메일 누락")
    void login_MissingEmail() throws Exception {
        // given
        SellerLoginRequest request = new SellerLoginRequest();
        // email 누락
        request.setPassword("Password123!");

        // when & then
        mockMvc.perform(post("/v1/seller/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).login(any());
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 누락")
    void login_MissingPassword() throws Exception {
        // given
        SellerLoginRequest request = new SellerLoginRequest();
        request.setEmail("admin@test.com");
        // password 누락

        // when & then
        mockMvc.perform(post("/v1/seller/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(adminService, never()).login(any());
    }
}
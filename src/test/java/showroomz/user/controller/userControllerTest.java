package showroomz.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import showroomz.auth.DTO.ValidationErrorResponse;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.user.DTO.NicknameCheckResponse;
import showroomz.user.DTO.UpdateUserProfileRequest;
import showroomz.user.controller.UserController;
import showroomz.user.entity.Users;
import showroomz.user.service.UserService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter 비활성화 (순수 컨트롤러 로직 테스트)
@DisplayName("UserController 단위 테스트")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    // SecurityContext Mocking을 위한 객체들
    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        // 매 테스트마다 SecurityContext Mocking 초기화
        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 인증된 사용자 설정 헬퍼 메소드
    private void mockAuthenticatedUser(String username) {
        // org.springframework.security.core.userdetails.User 객체 생성
        User principal = new User(username, "password", Collections.emptyList());
        
        given(securityContext.getAuthentication()).willReturn(authentication);
        given(authentication.getPrincipal()).willReturn(principal);
    }

    private Users createMockUser(String username, String nickname) {
        return new Users(
                username, nickname, username + "@test.com", "Y", "http://profile.url",
                ProviderType.NAVER, RoleType.USER, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getCurrentUser_Success() throws Exception {
        // given
        String username = "testUser";
        mockAuthenticatedUser(username);

        Users user = createMockUser(username, "테스트닉네임");
        given(userService.getUser(username)).willReturn(Optional.of(user));

        // when & then
        mockMvc.perform(get("/v1/user/me"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.nickname").value(user.getNickname()))
                .andExpect(jsonPath("$.roleType").value("USER"));
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 인증 정보 없음")
    void getCurrentUser_Fail_NoAuth() throws Exception {
        // given
        // Principal이 User 타입이 아닌 경우 (예: "anonymousUser" 문자열)
        given(securityContext.getAuthentication()).willReturn(authentication);
        given(authentication.getPrincipal()).willReturn("anonymousUser");

        // when & then
        mockMvc.perform(get("/v1/user/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED")); // ErrorCode.INVALID_AUTH_INFO
    }

    @Test
    @DisplayName("닉네임 중복 체크 성공")
    void checkNickname_Success() throws Exception {
        // given
        String nickname = "newNickname";
        NicknameCheckResponse response = new NicknameCheckResponse(true, "AVAILABLE", "사용 가능한 닉네임입니다.");
        given(userService.checkNickname(nickname)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/user/check-nickname")
                        .param("nickname", nickname))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    @DisplayName("프로필 수정 성공")
    void updateCurrentUser_Success() throws Exception {
        // given
        String username = "testUser";
        mockAuthenticatedUser(username);

        // 기존 유저 정보
        Users currentUser = createMockUser(username, "oldNickname");
        given(userService.getUser(username)).willReturn(Optional.of(currentUser));

        // 변경 요청 정보 (검증 통과 데이터)
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setNickname("newNick");
        request.setBirthday("2000-01-01");
        request.setGender("MALE");

        // 검증 로직 Mocking
        given(userService.isValidNicknameLength("newNick")).willReturn(true);
        given(userService.checkNickname("newNick")).willReturn(new NicknameCheckResponse(true, "AVAILABLE", "사용 가능"));

        // 업데이트 후 반환될 유저 정보
        Users updatedUser = new Users(username, "newNick", "email", "Y", "img", ProviderType.NAVER, RoleType.USER, LocalDateTime.now(), LocalDateTime.now());
        given(userService.updateProfile(eq(username), any(UpdateUserProfileRequest.class))).willReturn(updatedUser);

        // when & then
        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("newNick"));
    }

    @Test
    @DisplayName("프로필 수정 실패 - 검증 오류 수집 (형식 오류)")
    void updateCurrentUser_Fail_ValidationErrors() throws Exception {
        // given
        String username = "testUser";
        mockAuthenticatedUser(username);

        Users currentUser = createMockUser(username, "oldNickname");
        given(userService.getUser(username)).willReturn(Optional.of(currentUser));

        // 잘못된 요청 데이터
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setNickname("a"); // 너무 짧음
        request.setBirthday("2000/01/01"); // 잘못된 형식
        request.setGender("UNKNOWN"); // 잘못된 값

        // 닉네임 길이 검증 실패 Mocking
        given(userService.isValidNicknameLength("a")).willReturn(false);

        // when & then
        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest()) // 400 Bad Request
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                // 에러 리스트에 각 필드 에러가 포함되어 있는지 확인
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field == 'nickname')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'birthday')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'gender')]").exists());
    }

    @Test
    @DisplayName("프로필 수정 실패 - 닉네임 중복 (Fail-fast)")
    void updateCurrentUser_Fail_DuplicateNickname() throws Exception {
        // given
        String username = "testUser";
        mockAuthenticatedUser(username);

        Users currentUser = createMockUser(username, "oldNickname");
        given(userService.getUser(username)).willReturn(Optional.of(currentUser));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setNickname("duplicateNick");

        // 길이 검증은 통과하지만
        given(userService.isValidNicknameLength("duplicateNick")).willReturn(true);
        
        // 중복 체크에서 실패 (코드: DUPLICATE)
        NicknameCheckResponse duplicateResponse = new NicknameCheckResponse(false, "DUPLICATE", "중복");
        given(userService.checkNickname("duplicateNick")).willReturn(duplicateResponse);

        // when & then
        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict()) // 409 Conflict (ErrorCode.DUPLICATE_NICKNAME)
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
    }
    
    @Test
    @DisplayName("프로필 수정 실패 - 닉네임 비속어 포함 (오류 수집)")
    void updateCurrentUser_Fail_Profanity() throws Exception {
        // given
        String username = "testUser";
        mockAuthenticatedUser(username);
        
        Users currentUser = createMockUser(username, "oldNickname");
        given(userService.getUser(username)).willReturn(Optional.of(currentUser));
        
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setNickname("badWord");
        
        given(userService.isValidNicknameLength("badWord")).willReturn(true);
        // 비속어 응답
        NicknameCheckResponse profanityResponse = new NicknameCheckResponse(false, "PROFANITY", "비속어");
        given(userService.checkNickname("badWord")).willReturn(profanityResponse);
        
        // when & then
        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest()) // 비속어는 400 Bad Request + fieldError
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].reason").value("부적절한 단어가 포함되어 있습니다."));
    }
}
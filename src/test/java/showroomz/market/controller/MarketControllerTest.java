package showroomz.market.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import showroomz.global.error.exception.GlobalExceptionHandler;
import showroomz.market.DTO.MarketDto;
import showroomz.market.controller.MarketController;
import showroomz.market.service.MarketService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MarketController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter Chain 비활성화 (순수 컨트롤러 로직만 테스트)
@Import(GlobalExceptionHandler.class) // GlobalExceptionHandler를 import하여 validation 에러 처리 활성화
@DisplayName("MarketController 단위 테스트")
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MarketService marketService;

    private static final String TEST_ADMIN_EMAIL = "admin@test.com";

    @BeforeEach
    void setUp() {
        // SecurityContext 초기화
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("마켓명 중복 확인 - 사용 가능한 경우")
    void checkMarketName_Available() throws Exception {
        // given
        String marketName = "테스트마켓";
        MarketDto.CheckMarketNameResponse response = new MarketDto.CheckMarketNameResponse(
                true, "AVAILABLE", "사용 가능한 마켓명입니다."
        );
        given(marketService.checkMarketName(marketName)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/seller/markets/check-name")
                        .param("marketName", marketName))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true)) // Jackson이 isAvailable을 available로 직렬화
                .andExpect(jsonPath("$.code").value("AVAILABLE"))
                .andExpect(jsonPath("$.message").value("사용 가능한 마켓명입니다."));

        verify(marketService).checkMarketName(marketName);
    }

    @Test
    @DisplayName("마켓명 중복 확인 - 중복인 경우")
    void checkMarketName_Duplicate() throws Exception {
        // given
        String marketName = "중복마켓";
        MarketDto.CheckMarketNameResponse response = new MarketDto.CheckMarketNameResponse(
                false, "DUPLICATE", "이미 사용 중인 마켓명입니다."
        );
        given(marketService.checkMarketName(marketName)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/seller/markets/check-name")
                        .param("marketName", marketName))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false)) // Jackson이 isAvailable을 available로 직렬화
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 마켓명입니다."));

        verify(marketService).checkMarketName(marketName);
    }

    @Test
    @DisplayName("내 마켓 정보 조회 성공")
    void getMyMarket_Success() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        List<MarketDto.SnsLinkRequest> snsLinks = new ArrayList<>();
        snsLinks.add(new MarketDto.SnsLinkRequest("INSTAGRAM", "https://instagram.com/test"));
        snsLinks.add(new MarketDto.SnsLinkRequest("YOUTUBE", "https://youtube.com/test"));

        MarketDto.MarketProfileResponse response = MarketDto.MarketProfileResponse.builder()
                .marketId(1L)
                .marketName("테스트마켓")
                .csNumber("02-1234-5678")
                .marketImageUrl("https://example.com/image.jpg")
                .marketImageStatus("APPROVED")
                .marketDescription("테스트 마켓입니다")
                .marketUrl("https://showroomz.shop/market/1")
                .mainCategory("패션")
                .snsLinks(snsLinks)
                .build();

        given(marketService.getMyMarket(TEST_ADMIN_EMAIL)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/seller/markets/me"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketId").value(1))
                .andExpect(jsonPath("$.marketName").value("테스트마켓"))
                .andExpect(jsonPath("$.csNumber").value("02-1234-5678"))
                .andExpect(jsonPath("$.marketImageUrl").value("https://example.com/image.jpg"))
                .andExpect(jsonPath("$.marketImageStatus").value("APPROVED"))
                .andExpect(jsonPath("$.marketDescription").value("테스트 마켓입니다"))
                .andExpect(jsonPath("$.marketUrl").value("https://showroomz.shop/market/1"))
                .andExpect(jsonPath("$.mainCategory").value("패션"))
                .andExpect(jsonPath("$.snsLinks[0].snsType").value("INSTAGRAM"))
                .andExpect(jsonPath("$.snsLinks[0].snsUrl").value("https://instagram.com/test"))
                .andExpect(jsonPath("$.snsLinks[1].snsType").value("YOUTUBE"))
                .andExpect(jsonPath("$.snsLinks[1].snsUrl").value("https://youtube.com/test"));

        verify(marketService).getMyMarket(TEST_ADMIN_EMAIL);
    }

    @Test
    @DisplayName("마켓 프로필 수정 성공")
    void updateMarketProfile_Success() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        MarketDto.UpdateMarketProfileRequest request = new MarketDto.UpdateMarketProfileRequest();
        request.setMarketName("수정된마켓명");
        request.setMarketDescription("수정된 마켓 소개");
        request.setMarketImageUrl("https://example.com/new-image.jpg");
        request.setMainCategory("전자제품");

        List<MarketDto.SnsLinkRequest> snsLinks = new ArrayList<>();
        snsLinks.add(new MarketDto.SnsLinkRequest("INSTAGRAM", "https://instagram.com/new"));
        request.setSnsLinks(snsLinks);

        doNothing().when(marketService).updateMarketProfile(eq(TEST_ADMIN_EMAIL), any(MarketDto.UpdateMarketProfileRequest.class));

        // when & then
        mockMvc.perform(patch("/v1/seller/markets/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(marketService).updateMarketProfile(eq(TEST_ADMIN_EMAIL), any(MarketDto.UpdateMarketProfileRequest.class));
    }

    @Test
    @DisplayName("마켓 프로필 수정 실패 - 유효하지 않은 마켓명 (공백 포함)")
    void updateMarketProfile_InvalidMarketName() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        MarketDto.UpdateMarketProfileRequest request = new MarketDto.UpdateMarketProfileRequest();
        request.setMarketName("테스트 마켓"); // 공백 포함 (한글만 허용, 공백 불가)

        // when & then
        mockMvc.perform(patch("/v1/seller/markets/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(marketService, never()).updateMarketProfile(any(), any());
    }

    @Test
    @DisplayName("마켓 프로필 수정 실패 - 마켓 소개 30자 초과")
    void updateMarketProfile_DescriptionTooLong() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        MarketDto.UpdateMarketProfileRequest request = new MarketDto.UpdateMarketProfileRequest();
        request.setMarketDescription("가".repeat(31)); // 31자 (최대 30자)

        // when & then
        mockMvc.perform(patch("/v1/seller/markets/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(marketService, never()).updateMarketProfile(any(), any());
    }

    @Test
    @DisplayName("마켓 프로필 수정 실패 - SNS 링크 4개 이상")
    void updateMarketProfile_TooManySnsLinks() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        MarketDto.UpdateMarketProfileRequest request = new MarketDto.UpdateMarketProfileRequest();
        List<MarketDto.SnsLinkRequest> snsLinks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            snsLinks.add(new MarketDto.SnsLinkRequest("INSTAGRAM", "https://instagram.com/test" + i));
        }
        request.setSnsLinks(snsLinks); // 4개 (최대 3개)

        // when & then
        mockMvc.perform(patch("/v1/seller/markets/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(marketService, never()).updateMarketProfile(any(), any());
    }

    @Test
    @DisplayName("마켓 프로필 수정 실패 - 유효하지 않은 SNS URL 형식 (http/https 없음)")
    void updateMarketProfile_InvalidSnsUrl_NoProtocol() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        MarketDto.UpdateMarketProfileRequest request = new MarketDto.UpdateMarketProfileRequest();
        List<MarketDto.SnsLinkRequest> snsLinks = new ArrayList<>();
        snsLinks.add(new MarketDto.SnsLinkRequest("INSTAGRAM", "instagram.com/test")); // http:// 또는 https:// 없음
        request.setSnsLinks(snsLinks);

        // when & then
        mockMvc.perform(patch("/v1/seller/markets/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(marketService, never()).updateMarketProfile(any(), any());
    }

    @Test
    @DisplayName("마켓 프로필 수정 실패 - 유효하지 않은 SNS URL 형식 (잘못된 프로토콜)")
    void updateMarketProfile_InvalidSnsUrl_WrongProtocol() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        MarketDto.UpdateMarketProfileRequest request = new MarketDto.UpdateMarketProfileRequest();
        List<MarketDto.SnsLinkRequest> snsLinks = new ArrayList<>();
        snsLinks.add(new MarketDto.SnsLinkRequest("INSTAGRAM", "ftp://instagram.com/test")); // http/https가 아님
        request.setSnsLinks(snsLinks);

        // when & then
        mockMvc.perform(patch("/v1/seller/markets/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(marketService, never()).updateMarketProfile(any(), any());
    }

    @Test
    @DisplayName("마켓 프로필 수정 성공 - 유효한 SNS URL 형식")
    void updateMarketProfile_ValidSnsUrl() throws Exception {
        // given
        mockSecurityContext(TEST_ADMIN_EMAIL);

        MarketDto.UpdateMarketProfileRequest request = new MarketDto.UpdateMarketProfileRequest();
        List<MarketDto.SnsLinkRequest> snsLinks = new ArrayList<>();
        snsLinks.add(new MarketDto.SnsLinkRequest("INSTAGRAM", "https://instagram.com/test")); // 유효한 https URL
        snsLinks.add(new MarketDto.SnsLinkRequest("YOUTUBE", "http://youtube.com/test")); // 유효한 http URL
        request.setSnsLinks(snsLinks);

        doNothing().when(marketService).updateMarketProfile(eq(TEST_ADMIN_EMAIL), any(MarketDto.UpdateMarketProfileRequest.class));

        // when & then
        mockMvc.perform(patch("/v1/seller/markets/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(marketService).updateMarketProfile(eq(TEST_ADMIN_EMAIL), any(MarketDto.UpdateMarketProfileRequest.class));
    }


    @Test
    @DisplayName("내 마켓 정보 조회 실패 - 인증 정보 없음")
    void getMyMarket_NoAuthentication() throws Exception {
        // given
        SecurityContextHolder.clearContext(); // 인증 정보 없음
        
        // SecurityContext에 null Authentication 설정
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        // when & then
        // getCurrentAdminEmail()에서 NullPointerException 발생 -> GlobalExceptionHandler가 500으로 처리
        mockMvc.perform(get("/v1/seller/markets/me"))
                .andDo(print())
                .andExpect(status().isInternalServerError()) // NullPointerException -> 500 Internal Server Error
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        verify(marketService, never()).getMyMarket(any());
    }

    /**
     * SecurityContext를 모킹하여 인증된 사용자로 설정
     */
    private void mockSecurityContext(String email) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        User user = new User(email, "", List.of());

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        SecurityContextHolder.setContext(securityContext);
    }
}


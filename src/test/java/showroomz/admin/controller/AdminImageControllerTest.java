package showroomz.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.service.ImageService;
import showroomz.api.app.image.type.ImageType;
import showroomz.api.app.image.type.UploadContext;
import showroomz.api.seller.image.controller.SellerImageController;
import showroomz.global.config.SecurityConfig;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellerImageController.class)
// Security 필터를 비활성화하여 컨트롤러 로직만 집중 테스트 (필요 시 SecurityConfig 포함 후 @WithMockUser 사용)
@AutoConfigureMockMvc(addFilters = false)
class AdminImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    // SecurityConfig 등에서 빈으로 주입받는 컴포넌트가 있다면 MockitoBean 처리
    @MockitoBean
    private AuthTokenProvider tokenProvider; 
    
    @MockitoBean
    private SecurityConfig securityConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("관리자 이미지 업로드 성공 - MARKET 타입")
    void uploadMarketImage_Success() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "market.jpg", 
                "image/jpeg", 
                "test image content".getBytes()
        );
        String type = "MARKET";
        String expectedUrl = "https://s3.ap-northeast-2.amazonaws.com/bucket/uploads/market/market.jpg";

        given(imageService.uploadImage(any(), eq(ImageType.MARKET), eq(UploadContext.SELLER)))
                .willReturn(new ImageUploadResponse(expectedUrl));

        // when
        ResultActions result = mockMvc.perform(
                multipart("/v1/seller/images")
                        .file(file)
                        .param("type", type)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(expectedUrl))
                .andDo(print());
    }

    @Test
    @DisplayName("관리자 이미지 업로드 성공 - PRODUCT 타입")
    void uploadProductImage_Success() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.png",
                "image/png",
                "product image content".getBytes()
        );
        String type = "PRODUCT";
        String expectedUrl = "https://s3.ap-northeast-2.amazonaws.com/bucket/uploads/product/product.png";

        given(imageService.uploadImage(any(), eq(ImageType.PRODUCT), eq(UploadContext.SELLER)))
                .willReturn(new ImageUploadResponse(expectedUrl));

        // when
        ResultActions result = mockMvc.perform(
                multipart("/v1/seller/images")
                        .file(file)
                        .param("type", type)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(expectedUrl))
                .andDo(print());
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 허용되지 않은 타입 (PROFILE)")
    void uploadImage_Fail_InvalidType_Profile() throws Exception {
        // AdminImageController 로직상 MARKET, PRODUCT 외에는 예외 발생해야 함
        
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "content".getBytes()
        );
        String type = "PROFILE"; // 관리자 API에서는 허용되지 않음

        // when
        ResultActions result = mockMvc.perform(
                multipart("/v1/seller/images")
                        .file(file)
                        .param("type", type)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_IMAGE_TYPE.getCode()))
                .andExpect(jsonPath("$.message").exists())
                .andDo(print());
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 잘못된 타입 문자열")
    void uploadImage_Fail_UnknownType() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "content".getBytes()
        );
        String type = "INVALID_TYPE_STRING";

        // when
        ResultActions result = mockMvc.perform(
                multipart("/v1/seller/images")
                        .file(file)
                        .param("type", type)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        );

        // then
        // ImageType.valueOf()에서 IllegalArgumentException 발생 -> BusinessException 변환 로직 확인
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_IMAGE_TYPE.getCode()))
                .andDo(print());
    }

    @Test
    @DisplayName("이미지 업로드 실패 - Service 계층 예외 (빈 파일)")
    void uploadImage_Fail_ServiceException() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]
        );
        String type = "MARKET";

        // Service에서 BusinessException 발생시킴
        given(imageService.uploadImage(any(), eq(ImageType.MARKET), eq(UploadContext.SELLER)))
                .willThrow(new BusinessException(ErrorCode.EMPTY_FILE_EXCEPTION));

        // when
        ResultActions result = mockMvc.perform(
                multipart("/v1/seller/images")
                        .file(file)
                        .param("type", type)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMPTY_FILE_EXCEPTION.getCode()))
                .andDo(print());
    }
}
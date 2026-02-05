package showroomz.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.controller.ImageController;
import showroomz.api.app.image.service.ImageService;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.error.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ImageController.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("이미지 업로드 API 테스트")
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private AuthTokenProvider tokenProvider;

    private MockMultipartFile validImageFile;
    private AuthToken validAuthToken;

    @BeforeEach
    void setUp() {
        // 유효한 이미지 파일 생성 (1KB 크기)
        validImageFile = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        // 유효한 AuthToken Mock 설정
        validAuthToken = org.mockito.Mockito.mock(AuthToken.class);
        when(validAuthToken.validate()).thenReturn(true);
    }

    @Test
    @DisplayName("정상적인 이미지 업로드 성공")
    void 이미지_업로드_성공() throws Exception {
        // given
        String accessToken = "valid_access_token";
        String imageUrl = "https://d1234567890.cloudfront.net/uploads/profile/uuid-test.jpg";

        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);
        when(imageService.uploadImage(any(), any(ImageType.class)))
                .thenReturn(new ImageUploadResponse(imageUrl));

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(imageUrl));
    }

    @Test
    @DisplayName("Authorization 헤더 없음 - 401 에러")
    void Authorization_헤더_없음_401_에러() throws Exception {
        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "PROFILE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증 정보가 유효하지 않습니다. 다시 로그인해주세요."));
    }

    @Test
    @DisplayName("유효하지 않은 토큰 - 401 에러")
    void 유효하지_않은_토큰_401_에러() throws Exception {
        // given
        String invalidToken = "invalid_token";
        AuthToken invalidAuthToken = org.mockito.Mockito.mock(AuthToken.class);
        when(invalidAuthToken.validate()).thenReturn(false);
        when(tokenProvider.convertAuthToken(invalidToken)).thenReturn(invalidAuthToken);

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + invalidToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증 정보가 유효하지 않습니다. 다시 로그인해주세요."));
    }

    @Test
    @DisplayName("잘못된 이미지 타입 - 400 에러")
    void 잘못된_이미지_타입_400_에러() throws Exception {
        // given
        String accessToken = "valid_access_token";
        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "INVALID_TYPE")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, PRODUCT, MARKET)"));
    }

    @Test
    @DisplayName("파일 형식 오류 - 400 에러")
    void 파일_형식_오류_400_에러() throws Exception {
        // given
        String accessToken = "valid_access_token";
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "not an image".getBytes()
        );

        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);
        when(imageService.uploadImage(any(), any(ImageType.class)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_FILE_EXTENSION));

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(invalidFile)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 이미지 형식입니다"));
    }

    @Test
    @DisplayName("파일 크기 초과 - 413 에러")
    void 파일_크기_초과_413_에러() throws Exception {
        // given
        String accessToken = "valid_access_token";
        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);
        when(imageService.uploadImage(any(), any(ImageType.class)))
                .thenThrow(new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED));

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_SIZE_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("이미지 용량은 최대 20MB까지 등록 가능합니다."));
    }

    @Test
    @DisplayName("빈 파일 - 400 에러")
    void 빈_파일_400_에러() throws Exception {
        // given
        String accessToken = "valid_access_token";
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);
        when(imageService.uploadImage(any(), any(ImageType.class)))
                .thenThrow(new BusinessException(ErrorCode.EMPTY_FILE_EXCEPTION));

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(emptyFile)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.message").value("업로드할 파일이 존재하지 않습니다."));
    }

    @Test
    @DisplayName("REVIEW 타입 이미지 업로드 성공")
    void REVIEW_타입_이미지_업로드_성공() throws Exception {
        // given
        String accessToken = "valid_access_token";
        String imageUrl = "https://d1234567890.cloudfront.net/uploads/review/uuid-review.jpg";

        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);
        when(imageService.uploadImage(any(), any(ImageType.class)))
                .thenReturn(new ImageUploadResponse(imageUrl));

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "REVIEW")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(imageUrl));
    }

    @Test
    @DisplayName("PRODUCT 타입 이미지 업로드 권한 없음 - 403 에러")
    void PRODUCT_타입_이미지_업로드_권한_없음_403_에러() throws Exception {
        // given
        String accessToken = "valid_access_token";
        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "PRODUCT")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("MARKET 타입 이미지 업로드 권한 없음 - 403 에러")
    void MARKET_타입_이미지_업로드_권한_없음_403_에러() throws Exception {
        // given
        String accessToken = "valid_access_token";
        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "MARKET")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("소문자 type 파라미터도 정상 처리")
    void 소문자_type_파라미터_정상_처리() throws Exception {
        // given
        String accessToken = "valid_access_token";
        String imageUrl = "https://d1234567890.cloudfront.net/uploads/profile/uuid-test.jpg";

        when(tokenProvider.convertAuthToken(accessToken)).thenReturn(validAuthToken);
        when(imageService.uploadImage(any(), any(ImageType.class)))
                .thenReturn(new ImageUploadResponse(imageUrl));

        // when & then
        mockMvc.perform(multipart("/v1/user/images")
                        .file(validImageFile)
                        .param("type", "profile") // 소문자
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(imageUrl));
    }
}


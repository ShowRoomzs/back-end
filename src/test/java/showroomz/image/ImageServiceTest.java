package showroomz.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.service.ImageService;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("이미지 서비스 테스트")
class ImageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private ImageService imageService;

    private MockMultipartFile validImageFile;
    private MockMultipartFile largeImageFile;
    private MockMultipartFile invalidFormatFile;
    private MockMultipartFile emptyFile;

    @BeforeEach
    void setUp() {
        // 유효한 이미지 파일 (1KB)
        validImageFile = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                new byte[1024]
        );

        // 큰 이미지 파일 (21MB - 제한 초과, 현재 제한은 20MB)
        largeImageFile = new MockMultipartFile(
                "file",
                "large-image.jpg",
                "image/jpeg",
                new byte[21 * 1024 * 1024]
        );

        // 잘못된 형식 파일
        invalidFormatFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "not an image".getBytes()
        );

        // 빈 파일
        emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        // S3Properties 기본 설정 (lenient로 설정하여 일부 테스트에서 사용되지 않아도 에러가 나지 않도록)
        lenient().when(s3Properties.getBucket()).thenReturn("test-bucket");
        lenient().when(s3Properties.getRegion()).thenReturn("ap-northeast-2");
        lenient().when(s3Properties.getCloudFrontDomain()).thenReturn("d1234567890.cloudfront.net");
    }

    @Test
    @DisplayName("정상적인 이미지 업로드 성공 - CloudFront URL 반환")
    void 정상적인_이미지_업로드_성공_CloudFront() throws Exception {
        // given
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        ImageUploadResponse response = imageService.uploadImage(validImageFile, ImageType.PROFILE);

        // then
        assertThat(response.getImageUrl()).isNotNull();
        assertThat(response.getImageUrl()).contains("d1234567890.cloudfront.net");
        assertThat(response.getImageUrl()).contains("uploads/profile/");
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("정상적인 이미지 업로드 성공 - S3 직접 URL 반환 (CloudFront 없음)")
    void 정상적인_이미지_업로드_성공_S3() throws Exception {
        // given
        when(s3Properties.getCloudFrontDomain()).thenReturn(null);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        ImageUploadResponse response = imageService.uploadImage(validImageFile, ImageType.REVIEW);

        // then
        assertThat(response.getImageUrl()).isNotNull();
        assertThat(response.getImageUrl()).contains("test-bucket.s3.ap-northeast-2.amazonaws.com");
        assertThat(response.getImageUrl()).contains("uploads/review/");
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("빈 파일 업로드 시 예외 발생")
    void 빈_파일_업로드_예외() {
        // when & then
        assertThatThrownBy(() -> imageService.uploadImage(emptyFile, ImageType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException ex = (BusinessException) exception;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE_EXCEPTION);
                    assertThat(ex.getMessage()).isEqualTo("업로드할 파일이 존재하지 않습니다.");
                });
    }

    @Test
    @DisplayName("null 파일 업로드 시 예외 발생")
    void null_파일_업로드_예외() {
        // when & then
        assertThatThrownBy(() -> imageService.uploadImage(null, ImageType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException ex = (BusinessException) exception;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE_EXCEPTION);
                    assertThat(ex.getMessage()).isEqualTo("업로드할 파일이 존재하지 않습니다.");
                });
    }

    @Test
    @DisplayName("파일 크기 초과 시 예외 발생")
    void 파일_크기_초과_예외() {
        // when & then
        assertThatThrownBy(() -> imageService.uploadImage(largeImageFile, ImageType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException ex = (BusinessException) exception;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
                    assertThat(ex.getMessage()).isEqualTo("이미지 용량은 최대 20MB까지 등록 가능합니다.");
                });
    }

    @Test
    @DisplayName("잘못된 파일 형식 시 예외 발생")
    void 잘못된_파일_형식_예외() {
        // when & then
        assertThatThrownBy(() -> imageService.uploadImage(invalidFormatFile, ImageType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException ex = (BusinessException) exception;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_EXTENSION);
                    assertThat(ex.getMessage()).isEqualTo("지원하지 않는 이미지 형식입니다");
                });
    }

    @Test
    @DisplayName("파일명이 null인 경우 예외 발생")
    void 파일명_null_예외() {
        // given
        MockMultipartFile fileWithNullName = new MockMultipartFile(
                "file",
                null,
                "image/jpeg",
                new byte[1024]
        );

        // when & then
        assertThatThrownBy(() -> imageService.uploadImage(fileWithNullName, ImageType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException ex = (BusinessException) exception;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_NAME);
                    assertThat(ex.getMessage()).isEqualTo("파일명이 올바르지 않습니다.");
                });
    }

    @Test
    @DisplayName("PNG 파일 업로드 성공")
    void PNG_파일_업로드_성공() throws Exception {
        // given
        MockMultipartFile pngFile = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                new byte[1024]
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        ImageUploadResponse response = imageService.uploadImage(pngFile, ImageType.PRODUCT);

        // then
        assertThat(response.getImageUrl()).isNotNull();
        assertThat(response.getImageUrl()).contains("uploads/product/");
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("GIF 파일 업로드 성공")
    void GIF_파일_업로드_성공() throws Exception {
        // given
        MockMultipartFile gifFile = new MockMultipartFile(
                "file",
                "test.gif",
                "image/gif",
                new byte[1024]
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        ImageUploadResponse response = imageService.uploadImage(gifFile, ImageType.REVIEW);

        // then
        assertThat(response.getImageUrl()).isNotNull();
        assertThat(response.getImageUrl()).contains("uploads/review/");
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("S3 업로드 실패 시 예외 발생")
    void S3_업로드_실패_예외() {
        // given
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder()
                        .message("S3 upload failed")
                        .statusCode(500)
                        .build());

        // when & then
        assertThatThrownBy(() -> imageService.uploadImage(validImageFile, ImageType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException ex = (BusinessException) exception;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("파일 업로드 중 오류가 발생했습니다.");
                });
    }

    @Test
    @DisplayName("IOException 발생 시 예외 처리")
    void IOException_발생_예외() throws Exception {
        // given
        MultipartFile fileWithIOException = mock(MultipartFile.class);
        when(fileWithIOException.isEmpty()).thenReturn(false);
        when(fileWithIOException.getSize()).thenReturn(1024L);
        when(fileWithIOException.getOriginalFilename()).thenReturn("test.jpg");
        when(fileWithIOException.getInputStream()).thenThrow(new IOException("IO error"));

        // when & then
        assertThatThrownBy(() -> imageService.uploadImage(fileWithIOException, ImageType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException ex = (BusinessException) exception;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("파일 업로드 중 오류가 발생했습니다.");
                });
    }
}


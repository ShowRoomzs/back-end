package showroomz.api.app.image.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 스튜디오 온보딩(ui-studio-03 rev.3) 서류 업로드 — 통장 사본 · 사업자등록증은 최대 10MB · jpg, png, pdf. 화면이 먼저 막고 서버는 2차
 * 검증이며, 문구는 화면의 형식 · 용량 오류 문구 그대로다. 브랜드 서류(20MB)는 바뀌지 않는다.
 */
@DisplayName("이미지 업로드 — 크리에이터 서류 10MB · 문구")
class ImageServiceDocumentLimitTest {

    private static final int MB = 1024 * 1024;

    private final S3Client s3Client = mock(S3Client.class);
    private final ImageService imageService = new ImageService(s3Client, mock(S3Properties.class));

    @Test
    @DisplayName("크리에이터 서류가 10MB를 넘으면 화면과 같은 용량 문구로 거절한다")
    void creatorDocumentOver10MbIsRejected() {
        MockMultipartFile file = file("license.png", 10 * MB + 1);

        assertThatThrownBy(() -> imageService.uploadImage(file, ImageType.CREATOR_DOCUMENT))
                .isInstanceOf(BusinessException.class)
                .hasMessage("파일 크기가 10MB를 초과했습니다. 10MB 이하의 파일을 선택해 주세요.")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("크리에이터 서류가 jpg · png · pdf 가 아니면 화면과 같은 형식 문구로 거절한다")
    void creatorDocumentWithUnsupportedExtensionIsRejected() {
        MockMultipartFile file = file("통장사본.hwp", 1024);

        assertThatThrownBy(() -> imageService.uploadImage(file, ImageType.CREATOR_DOCUMENT))
                .isInstanceOf(BusinessException.class)
                .hasMessage("jpg, png, pdf 파일만 업로드할 수 있습니다.")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_EXTENSION);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("브랜드 서류는 10MB를 넘어도 용량으로 막히지 않는다 — 20MB 규칙 그대로(형식 검사까지 간다)")
    void brandDocumentKeeps20MbLimit() {
        MockMultipartFile file = file("license.hwp", 12 * MB);

        assertThatThrownBy(() -> imageService.uploadImage(file, ImageType.SIGNUP_DOCUMENT))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_FILE_EXTENSION.getMessage());
    }

    @Test
    @DisplayName("[DU-01 · DU-02] jpg · jpeg · png · pdf(대문자 포함)와 정확히 10MB 는 올라간다")
    void allowedCreatorDocumentsAreUploaded() {
        S3Properties properties = mock(S3Properties.class);
        when(properties.getBucket()).thenReturn("showroomz-test");
        when(properties.getRegion()).thenReturn("ap-northeast-2");
        when(properties.getCloudFrontDomain()).thenReturn("cdn.showroomz.test");
        ImageService service = new ImageService(s3Client, properties);

        for (String name : new String[]{"bankbook.jpg", "bankbook.jpeg", "bankbook.png", "license.pdf", "license.PDF"}) {
            ImageUploadResponse response = service.uploadImage(file(name, 1024), ImageType.CREATOR_DOCUMENT);
            assertThat(response.getImageUrl()).as(name).startsWith("https://cdn.showroomz.test/uploads/creator_document/");
        }
        ImageUploadResponse limit = service.uploadImage(file("bankbook.pdf", 10 * MB), ImageType.CREATOR_DOCUMENT);

        assertThat(limit.getImageUrl()).endsWith(".pdf");
        verify(s3Client, times(6)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("[DU-05] 빈 파일은 EMPTY_FILE · 확장자 없음 · 점으로 끝나는 이름은 화면 형식 문구")
    void emptyOrExtensionlessCreatorDocumentsAreRejected() {
        assertThatThrownBy(() -> imageService.uploadImage(file("bankbook.jpg", 0), ImageType.CREATOR_DOCUMENT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_FILE_EXCEPTION);

        for (String name : new String[]{"bankbook", "bankbook."}) {
            assertThatThrownBy(() -> imageService.uploadImage(file(name, 1024), ImageType.CREATOR_DOCUMENT))
                    .as(name)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("jpg, png, pdf 파일만 업로드할 수 있습니다.");
        }
        verifyNoInteractions(s3Client);
    }

    private static MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[size]);
    }
}

package showroomz.api.app.image.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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

    private static MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[size]);
    }
}

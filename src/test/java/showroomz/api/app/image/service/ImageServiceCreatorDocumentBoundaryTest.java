package showroomz.api.app.image.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.config.properties.S3Properties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 스튜디오 서류(`CREATOR_DOCUMENT`) 업로드 경계(50 테스트 시나리오 UP-02) — 정확히 10MB 는 받는다(`size > 10MB` 만 거절) · jpg · jpeg · png ·
 * pdf 는 모두 S3 까지 간다. 거절 쪽 문구는 {@link ImageServiceDocumentLimitTest}가 본다.
 */
@DisplayName("이미지 업로드 — 크리에이터 서류 10MB 경계 · 허용 확장자")
class ImageServiceCreatorDocumentBoundaryTest {

    private static final int TEN_MB = 10 * 1024 * 1024;

    private final S3Client s3Client = mock(S3Client.class);
    private final ImageService imageService = new ImageService(s3Client, s3Properties());

    @Test
    @DisplayName("정확히 10MB pdf 는 용량으로 막히지 않고 업로드된다")
    void exactlyTenMegabytesIsAccepted() {
        ImageUploadResponse response = imageService.uploadImage(file("통장사본.pdf", TEN_MB), ImageType.CREATOR_DOCUMENT);

        assertThat(response.getImageUrl()).startsWith("https://cdn.showroomz.test/").endsWith(".pdf");
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("jpg · jpeg · png · pdf(대문자 포함)는 모두 받는다")
    void allowedExtensions() {
        for (String name : new String[]{"a.jpg", "a.jpeg", "a.png", "a.pdf", "A.PDF"}) {
            imageService.uploadImage(file(name, 1024), ImageType.CREATOR_DOCUMENT);
        }
        verify(s3Client, times(5)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private static S3Properties s3Properties() {
        S3Properties properties = mock(S3Properties.class);
        when(properties.getBucket()).thenReturn("showroomz-test");
        when(properties.getRegion()).thenReturn("ap-northeast-2");
        when(properties.getCloudFrontDomain()).thenReturn("cdn.showroomz.test");
        return properties;
    }

    private static MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[size]);
    }
}

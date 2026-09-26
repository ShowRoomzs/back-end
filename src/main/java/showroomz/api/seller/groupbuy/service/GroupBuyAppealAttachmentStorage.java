package showroomz.api.seller.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.S3Properties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 소명 증빙의 S3 입출력(C9). 연결·소통 첨부와 같은 presign → HeadObject 재검증 흐름이다.
 * 제출 시점에 실제로 올라간 객체의 크기·타입을 다시 본다 — presign 요청의 값은 클라이언트가 말한 것일 뿐이다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyAppealAttachmentStorage {

    public static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(15);

    /** 다운로드 URL 유효기간 — 계약 문서와 같은 5분(32 설계 4-6). 상세에 싣지 않고 클릭 시 발급한다. */
    public static final Duration DOWNLOAD_EXPIRY = Duration.ofMinutes(5);

    /** C9 — PNG · JPG · PDF. 키 확장자는 타입에서 정한다(원본 파일명을 키에 쓰지 않는다). */
    public static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "application/pdf", "pdf");

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_CONTENT_TYPES.containsKey(contentType);
    }

    public String newKey(Long adminSuspensionId, String contentType) {
        return "uploads/group-buy/appeal/" + adminSuspensionId + "/" + UUID.randomUUID()
                + "." + ALLOWED_CONTENT_TYPES.get(contentType);
    }

    public String presignUpload(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .build();
        return s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(PRESIGN_EXPIRY)
                        .putObjectRequest(putObjectRequest)
                        .build())
                .url()
                .toString();
    }

    /** 어드민 소명 첨부 열람 — 원본 파일명으로 내려받게 한다. */
    public String presignDownload(String key, String originalName) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .responseContentDisposition(ContentDisposition.attachment()
                        .filename(originalName, StandardCharsets.UTF_8).build().toString())
                .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(DOWNLOAD_EXPIRY)
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url()
                .toString();
    }

    /** 실제로 올라간 객체 — 없으면 empty. */
    public Optional<UploadedObject> head(String key) {
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());
            return Optional.of(new UploadedObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    public record UploadedObject(Long contentLength, String contentType) {
    }
}

package showroomz.api.seller.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.S3Properties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

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

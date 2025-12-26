package showroomz.oauthlogin.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import showroomz.config.properties.S3Properties;
import showroomz.oauthlogin.image.DTO.ImageUploadResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif");

    public ImageUploadResponse uploadImage(MultipartFile file, ImageType type) {
        // 1. 파일 존재 여부 확인
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "업로드할 파일이 존재하지 않습니다."
            );
        }

        // 2. 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "이미지 파일은 최대 10MB까지만 업로드 가능합니다."
            );
        }

        // 3. 파일 형식 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일명이 올바르지 않습니다."
            );
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일(jpg, png, jpeg, gif)만 업로드 가능합니다."
            );
        }

        // 4. S3에 업로드
        try {
            String fileName = generateFileName(type, extension);
            String s3Key = getS3Key(type, fileName);
            String imageUrl = uploadToS3(file, s3Key);

            return new ImageUploadResponse(imageUrl);
        } catch (IOException e) {
            log.error("파일 업로드 중 오류 발생", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "파일 업로드 중 오류가 발생했습니다."
            );
        } catch (S3Exception e) {
            log.error("S3 업로드 중 오류 발생", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "파일 업로드 중 오류가 발생했습니다."
            );
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    private String generateFileName(ImageType type, String extension) {
        String uuid = UUID.randomUUID().toString();
        return uuid + "." + extension;
    }

    private String getS3Key(ImageType type, String fileName) {
        String folder = type.name().toLowerCase();
        return "uploads/" + folder + "/" + fileName;
    }

    private String uploadToS3(MultipartFile file, String s3Key) throws IOException {
        try {
            // 1. S3에 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));

            // 2. URL 인코딩 처리
            // 한글, 공백, 특수문자가 있어도 브라우저가 인식할 수 있게 변환합니다.
            // UUID만 쓴다면 당장 필요 없지만, 나중을 위해 필수입니다.
            String encodedKey = URLEncoder.encode(s3Key, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20") // 공백(+)을 %20으로 치환
                    .replaceAll("%2F", "/");  // 경로 구분자(/)는 인코딩 하지 않음

            // 3. CloudFront URL 사용 (버킷 이름 숨김)
            if (s3Properties.getCloudFrontDomain() != null && !s3Properties.getCloudFrontDomain().isEmpty()) {
                return "https://" + s3Properties.getCloudFrontDomain() + "/" + encodedKey;
            }
            
            // 4. Fallback: CloudFront가 없으면 S3 기본 URL 사용
            // (SDK v2에서는 getUrl이 복잡하므로, 인코딩된 키를 사용하여 직접 조합하는 것이 확실합니다)
            return String.format("https://%s.s3.%s.amazonaws.com/%s",
                    s3Properties.getBucket(),
                    s3Properties.getRegion(),
                    encodedKey); // s3Key 대신 encodedKey 사용

        } catch (S3Exception e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
}


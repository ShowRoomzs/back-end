package showroomz.image.service;

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
import showroomz.image.type.ImageType;
import showroomz.image.DTO.ImageUploadResponse;

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
            // 1. S3 설정 검증
            String bucket = s3Properties.getBucket();
            if (bucket == null || bucket.isEmpty()) {
                log.error("S3 버킷 이름이 설정되지 않았습니다. AWS_S3_BUCKET 환경변수를 확인하세요.");
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "S3 버킷 설정이 올바르지 않습니다. 관리자에게 문의하세요."
                );
            }

            String region = s3Properties.getRegion();
            if (region == null || region.isEmpty()) {
                log.error("S3 리전이 설정되지 않았습니다. AWS_S3_REGION 환경변수를 확인하세요.");
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "S3 리전 설정이 올바르지 않습니다. 관리자에게 문의하세요."
                );
            }

            log.debug("S3 업로드 시도 - Bucket: {}, Region: {}, Key: {}", bucket, region, s3Key);

            // 2. S3에 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));

            log.debug("S3 업로드 성공 - Key: {}", s3Key);

            // 3. URL 인코딩 처리
            String encodedKey = URLEncoder.encode(s3Key, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20") // 공백(+)을 %20으로 치환
                    .replaceAll("%2F", "/");  // 경로 구분자(/)는 인코딩 하지 않음

            // 4. CloudFront URL 사용 (버킷 이름 숨김)
            if (s3Properties.getCloudFrontDomain() != null && !s3Properties.getCloudFrontDomain().isEmpty()) {
                return "https://" + s3Properties.getCloudFrontDomain() + "/" + encodedKey;
            }
            
            // 5. Fallback: CloudFront가 없으면 S3 기본 URL 사용
            return String.format("https://%s.s3.%s.amazonaws.com/%s",
                    bucket,
                    region,
                    encodedKey); // s3Key 대신 encodedKey 사용

        } catch (S3Exception e) {
            log.error("S3 업로드 실패 - Bucket: {}, Region: {}, Error: {}, StatusCode: {}, ErrorCode: {}", 
                    s3Properties.getBucket(), 
                    s3Properties.getRegion(),
                    e.getMessage(), 
                    e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "N/A");
            
            // 더 명확한 에러 메시지 제공
            if (e.statusCode() == 400) {
                String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "";
                if ("InvalidBucketName".equals(errorCode)) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "S3 버킷 이름이 올바르지 않습니다. 버킷 이름은 소문자, 숫자, 하이픈(-), 점(.)만 사용 가능하며 3-63자 사이여야 합니다."
                    );
                } else if ("NoSuchBucket".equals(errorCode)) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "S3 버킷이 존재하지 않습니다. 버킷 이름과 리전을 확인하세요."
                    );
                } else {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "S3 버킷 설정이 올바르지 않습니다. 버킷 이름과 리전을 확인하세요."
                    );
                }
            } else if (e.statusCode() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "S3 접근 권한이 없습니다. IAM 사용자에게 s3:PutObject 권한이 필요합니다."
                );
            } else {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()
                );
            }
        }
    }
}


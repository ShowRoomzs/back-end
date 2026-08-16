package showroomz.api.app.image.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif");
    /** 브랜드·인플루언서 가입/추가정보 서류 */
    private static final List<String> DOCUMENT_ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "pdf");
    /**
     * 쇼룸 게시물 사진 — <b>JPG·PNG만</b>이다(§24-2). 공통 목록과 달리 gif가 빠져 있다.
     *
     * <p>영상을 받지 않는 것과 같은 이유다 — 릴스·영상 콘텐츠는 인스타그램에 직접 올리는 것이
     * 계약상 콘텐츠 의무이고, 쇼룸은 사진만 받는다.
     */
    private static final List<String> POST_ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png");

    public ImageUploadResponse uploadImage(MultipartFile file, ImageType type) {
        // 1. 파일 존재 여부 확인
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE_EXCEPTION);
        }

        // 2. 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 3. 파일 형식 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_NAME);
        }

        String extension = getFileExtension(originalFilename);
        if (!getAllowedExtensions(type).contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        // 4. 대표 이미지(마켓·쇼룸 프로필)인 경우 해상도 및 비율 검증
        //    §22-1 쇼룸 프로필 이미지 규칙(최소 160×160 · 정비율 · 최대 20MB · JPG·PNG·GIF)이
        //    마켓 대표 이미지와 같으므로 같은 검증을 태운다.
        if (type == ImageType.MARKET || type == ImageType.SHOWROOM_PROFILE) {
            validateSquareThumbnail(file);
        }

        // 4-1. 게시물 사진은 실제 이미지인지 내용으로 확인하고 크기를 함께 돌려준다(§24-2)
        Dimension dimension = type == ImageType.POST ? readImageSize(file) : null;

        // 5. S3에 업로드
        try {
            String fileName = generateFileName(type, extension);
            String s3Key = getS3Key(type, fileName);
            String imageUrl = uploadToS3(file, s3Key);

            return dimension == null
                    ? new ImageUploadResponse(imageUrl)
                    : new ImageUploadResponse(imageUrl, dimension.width(), dimension.height());
        } catch (IOException e) {
            log.error("파일 업로드 중 IO 오류 발생", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
        // uploadToS3 내부에서 발생하는 BusinessException(S3 관련)은 그대로 전파됨
    }

    private List<String> getAllowedExtensions(ImageType type) {
        if (type == ImageType.SIGNUP_DOCUMENT || type == ImageType.CREATOR_DOCUMENT || type == ImageType.CHANGE_REQUEST_DOCUMENT) {
            return DOCUMENT_ALLOWED_EXTENSIONS;
        }
        if (type == ImageType.POST) {
            return POST_ALLOWED_EXTENSIONS;
        }
        return ALLOWED_EXTENSIONS;
    }

    /**
     * 게시물 사진 — <b>내용으로</b> 이미지인지 판정하고 크기를 읽는다.
     *
     * <p>확장자 문자열만 보면 {@code 아침루틴_영상.mp4}의 이름을 {@code .jpg}로 바꾼 파일이
     * 그대로 통과한다(§24-4 와이어의 실패 케이스가 정확히 이것이다). {@code ImageIO.read()}가
     * {@code null}을 돌려주면 디코더가 없다는 뜻이므로 거절한다 — 대표 이미지 검증이 이미 쓰는 방식이다.
     */
    private Dimension readImageSize(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
            }
            return new Dimension(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            log.error("이미지 읽기 실패", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private record Dimension(int width, int height) {
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
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            String region = s3Properties.getRegion();
            if (region == null || region.isEmpty()) {
                log.error("S3 리전이 설정되지 않았습니다. AWS_S3_REGION 환경변수를 확인하세요.");
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
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
                    encodedKey);

        } catch (S3Exception e) {
            // 상세 에러 로그 기록 (디버깅용)
            log.error("S3 업로드 실패 - Bucket: {}, Region: {}, Error: {}, StatusCode: {}, ErrorCode: {}",
                    s3Properties.getBucket(),
                    s3Properties.getRegion(),
                    e.getMessage(),
                    e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "N/A");

            // 클라이언트에게는 내부 상세 정보(버킷명, 권한 등)를 숨기고 공통 에러 코드 반환
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    /**
     * 대표 이미지 정밀 검증 (해상도 최소 160×160, 정비율) — 마켓 대표 이미지·쇼룸 프로필 이미지 공통.
     */
    private void validateSquareThumbnail(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // 1. 해상도 검사: 160x160 미만인 경우
            if (width < 160 || height < 160) {
                throw new BusinessException(ErrorCode.IMAGE_RESOLUTION_TOO_LOW);
            }

            // 2. 비율 검사: 정비율(1:1)이 아닌 경우
            if (width != height) {
                throw new BusinessException(ErrorCode.IMAGE_RATIO_NOT_SQUARE);
            }

        } catch (IOException e) {
            log.error("이미지 읽기 실패", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}


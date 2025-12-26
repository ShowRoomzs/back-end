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
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));

            // S3 URL 생성
            return String.format("https://%s.s3.%s.amazonaws.com/%s",
                    s3Properties.getBucket(),
                    s3Properties.getRegion(),
                    s3Key);
        } catch (S3Exception e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
}


package showroomz.image.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import showroomz.auth.DTO.ErrorResponse;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.image.type.ImageType;
import showroomz.image.DTO.ImageUploadResponse;
import showroomz.image.service.ImageService;
import showroomz.swaggerDocs.ImageControllerDocs;
import showroomz.utils.HeaderUtil;

@RestController
@RequestMapping("/v1/images")
@RequiredArgsConstructor
public class ImageController implements ImageControllerDocs {

    private final ImageService imageService;
    private final AuthTokenProvider tokenProvider;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadImage(
            HttpServletRequest request,
            @RequestParam("type") String typeParam,
            @RequestParam("file") MultipartFile file) {

        try {
            // 1. Authorization 헤더 검증
            String accessToken = HeaderUtil.getAccessToken(request);
            if (accessToken == null || accessToken.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "인증 정보가 유효하지 않습니다."));
            }

            AuthToken authToken = tokenProvider.convertAuthToken(accessToken);
            if (!authToken.validate()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "인증 정보가 유효하지 않습니다."));
            }

            // 2. type 파라미터 검증
            ImageType imageType;
            try {
                imageType = ImageType.valueOf(typeParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("INVALID_INPUT", "유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, PRODUCT)"));
            }

            // 3. 파일 검증 및 업로드
            ImageUploadResponse response = imageService.uploadImage(file, imageType);
            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            // 파일 관련 에러 처리
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }

            String code;
            String reason = e.getReason();
            if (status == HttpStatus.BAD_REQUEST) {
                if (reason != null && reason.contains("이미지 파일")) {
                    code = "INVALID_FILE_TYPE";
                } else if (reason != null && reason.contains("존재하지 않습니다")) {
                    code = "EMPTY_FILE";
                } else {
                    code = "INVALID_INPUT";
                }
            } else if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
                code = "FILE_SIZE_EXCEEDED";
            } else {
                code = "INTERNAL_SERVER_ERROR";
            }

            return ResponseEntity.status(status)
                    .body(new ErrorResponse(code, reason != null ? reason : "서버 내부 오류가 발생했습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
        }
    }
}

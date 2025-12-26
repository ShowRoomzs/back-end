package showroomz.oauthlogin.image;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import showroomz.oauthlogin.auth.DTO.ErrorResponse;
import showroomz.oauthlogin.image.DTO.ImageUploadResponse;
import showroomz.oauthlogin.utils.HeaderUtil;
import showroomz.oauthlogin.oauth.token.AuthToken;
import showroomz.oauthlogin.oauth.token.AuthTokenProvider;

@RestController
@RequestMapping("/v1/images")
@RequiredArgsConstructor
@Tag(name = "Image", description = "Image Upload API")
public class ImageController {

    private final ImageService imageService;
    private final AuthTokenProvider tokenProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "이미지 업로드", description = "S3에 이미지를 업로드합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 형식 오류, 빈 파일)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "413", description = "파일 크기 초과")
    })
    public ResponseEntity<?> uploadImage(
            HttpServletRequest request,
            @Parameter(description = "이미지 타입 (PROFILE, REVIEW, PRODUCT)", required = true, example = "PROFILE")
            @RequestParam("type") String typeParam,
            @Parameter(description = "업로드할 이미지 파일 (jpg, png, jpeg, gif, 최대 10MB)", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
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


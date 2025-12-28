package showroomz.swaggerDocs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import showroomz.oauthlogin.auth.DTO.ErrorResponse;
import showroomz.oauthlogin.image.DTO.ImageUploadResponse;

@Tag(name = "Image", description = "Image Upload API")
public interface ImageControllerDocs {

    @Operation(
            summary = "이미지 업로드",
            description = "파일을 받아 S3에 업로드하고, 업로드된 이미지의 URL을 반환합니다.\n\n" +
                    "**호출 도메인**\n" +
                    "- 개발: https://localhost:8080\n" +
                    "- 배포: https://api.showroomz.shop"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "업로드 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"imageUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket-name/uploads/profile/uuid_filename.jpg\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "파일 형식이 이미지가 아님 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "파일 형식 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_FILE_TYPE\",\n" +
                                                    "  \"message\": \"이미지 파일(jpg, png, jpeg, gif)만 업로드 가능합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "399",
                    description = "파일이 비어있음 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "빈 파일",
                                            value = "{\n" +
                                                    "  \"code\": \"EMPTY_FILE\",\n" +
                                                    "  \"message\": \"업로드할 파일이 존재하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "파일 용량 초과 (10MB 제한) - Status: 413 Payload Too Large",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "파일 크기 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"FILE_SIZE_EXCEEDED\",\n" +
                                                    "  \"message\": \"이미지 파일은 최대 10MB까지만 업로드 가능합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<?> uploadImage(
            @Parameter(
                    name = "Authorization",
                    description = "Bearer {access_token} 형식으로 전달",
                    required = true,
                    hidden = false
            )
            HttpServletRequest request,

            @Parameter(
                    description = "업로드할 이미지의 용도 (필수)\n" +
                            "- `PROFILE`: 프로필 이미지\n" +
                            "- `REVIEW`: 리뷰 이미지\n" +
                            "- `PRODUCT`: 상품 이미지",
                    required = true,
                    example = "PROFILE"
            )
            @RequestParam("type") String typeParam,

            @Parameter(
                    description = "업로드할 이미지 파일 (Binary File)\n" +
                            "- 지원 형식: jpg, png, jpeg, gif\n" +
                            "- 최대 크기: 10MB",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            )
            @RequestParam("file") MultipartFile file
    );
}
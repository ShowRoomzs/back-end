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

import showroomz.auth.DTO.ErrorResponse;
import showroomz.image.DTO.ImageUploadResponse;

@Tag(name = "Image", description = "Image Upload API")
public interface ImageControllerDocs {

    @Operation(
            summary = "이미지 업로드",
            description = "파일을 받아 S3에 업로드하고, 업로드된 이미지의 URL을 반환합니다.\n\n" +
                    "**호출 도메인**\n" +
                    "- 개발: https://localhost:8080\n" +
                    "- 배포: https://api.showroomz.shop\n\n" +
                    "**이미지 타입별 제약사항:**\n" +
                    "- `PROFILE`, `REVIEW`, `PRODUCT`: 일반 이미지 (최대 20MB)\n" +
                    "- `MARKET`: 마켓 대표 이미지\n" +
                    "  - 최소 해상도: 160×160px 이상\n" +
                    "  - 비율: 정비율(1:1)만 허용\n" +
                    "  - 최대 크기: 20MB"
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
                    description = "입력값 오류 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효하지 않은 이미지 타입",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, PRODUCT, MARKET)\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "빈 파일",
                                            value = "{\n" +
                                                    "  \"code\": \"EMPTY_FILE\",\n" +
                                                    "  \"message\": \"업로드할 파일이 존재하지 않습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "파일명 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"파일명이 올바르지 않습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "파일 확장자 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_FILE_TYPE\",\n" +
                                                    "  \"message\": \"지원하지 않는 이미지 형식입니다\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "마켓 이미지 해상도 부족",
                                            value = "{\n" +
                                                    "  \"code\": \"IMAGE_RESOLUTION_TOO_LOW\",\n" +
                                                    "  \"message\": \"이미지는 최소 160×160px 이상이어야 합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "마켓 이미지 비율 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"IMAGE_RATIO_NOT_SQUARE\",\n" +
                                                    "  \"message\": \"정비율의 이미지만 업로드 가능합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "파일 용량 초과 (20MB 제한) - Status: 413 Payload Too Large",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "파일 크기 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"FILE_SIZE_EXCEEDED\",\n" +
                                                    "  \"message\": \"이미지 용량은 최대 20MB까지 등록 가능합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "파일 업로드 중 오류 발생 - Status: 500 Internal Server Error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "업로드 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INTERNAL_SERVER_ERROR\",\n" +
                                                    "  \"message\": \"파일 업로드 중 오류가 발생했습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ImageUploadResponse> uploadImage(
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
                            "- `PRODUCT`: 상품 이미지\n" +
                            "- `MARKET`: 마켓 대표 이미지 (160×160px 이상, 정비율 필수)",
                    required = true,
                    example = "PROFILE"
            )
            @RequestParam("type") String typeParam,

            @Parameter(
                    description = "업로드할 이미지 파일 (Binary File)\n" +
                            "- 지원 형식: jpg, png, jpeg, gif\n" +
                            "- 최대 크기: 20MB\n" +
                            "- MARKET 타입의 경우: 최소 160×160px, 정비율(1:1) 필수",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            )
            @RequestParam("file") MultipartFile file
    );
}
package showroomz.swaggerDocs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import showroomz.auth.DTO.ErrorResponse;
import showroomz.image.DTO.ImageUploadResponse;

@Tag(name = "Admin Image", description = "관리자(판매자) 전용 이미지 업로드 API")
public interface AdminImageControllerDocs {

    @Operation(
            summary = "관리자 전용 이미지 업로드",
            description = "관리자(판매자)만 사용 가능한 이미지 업로드 API입니다. MARKET과 PRODUCT 타입만 업로드할 수 있습니다.\n\n" +
            
                    "**이미지 타입별 제약사항:**\n" +
                    "- `PRODUCT`: 상품 이미지 (최대 20MB)\n" +
                    "- `MARKET`: 마켓 대표 이미지\n" +
                    "  - 최소 해상도: 160×160px 이상\n" +
                    "  - 비율: 정비율(1:1)만 허용\n" +
                    "  - 최대 크기: 20MB\n\n" +
                    "**권한:** 관리자(판매자) 인증 필요"
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
                                                    "  \"imageUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket-name/uploads/market/uuid_filename.jpg\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "399",
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
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "마켓 이미지 업로드 오류 - Status: 400 Bad Request (MARKET 타입 전용)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 이미지 해상도 부족",
                                            value = "{\n" +
                                                    "  \"code\": \"IMAGE_RESOLUTION_TOO_LOW\",\n" +
                                                    "  \"message\": \"이미지는 최소 160×160px 이상이어야 합니다.\"\n" +
                                                    "}",
                                            description = "MARKET 타입 이미지 업로드 시, 이미지 해상도가 160×160px 미만인 경우 발생합니다."
                                    ),
                                    @ExampleObject(
                                            name = "마켓 이미지 비율 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"IMAGE_RATIO_NOT_SQUARE\",\n" +
                                                    "  \"message\": \"정비율의 이미지만 업로드 가능합니다.\"\n" +
                                                    "}",
                                            description = "MARKET 타입 이미지 업로드 시, 이미지가 정비율(1:1)이 아닌 경우 발생합니다."
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"FORBIDDEN\",\n" +
                                                    "  \"message\": \"접근 권한이 없습니다.\"\n" +
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
    ResponseEntity<ImageUploadResponse> uploadMarketImage(
            @Parameter(
                    description = "업로드할 이미지의 용도 (필수)\n" +
                            "- `PRODUCT`: 상품 이미지\n" +
                            "- `MARKET`: 마켓 대표 이미지 (160×160px 이상, 정비율 필수)\n\n",
                    required = true,
                    example = "MARKET"
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


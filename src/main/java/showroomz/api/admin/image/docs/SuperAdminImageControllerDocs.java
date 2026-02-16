package showroomz.api.admin.image.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.image.DTO.ImageUploadResponse;

@Tag(name = "Image", description = "Image Upload API")
public interface SuperAdminImageControllerDocs {

    @Operation(
            summary = "어드민 이미지 업로드",
            description = "어드민 전용 이미지 업로드 API입니다. 현재는 **CATEGORY** 타입만 업로드 가능합니다.\n\n" +
                    "**이미지 타입별 제약사항:**\n" +
                    "- `CATEGORY`: 카테고리 아이콘/이미지 (최대 20MB)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
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
                                                    "  \"imageUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket-name/uploads/category/uuid_filename.png\"\n" +
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
                                                    "  \"message\": \"유효하지 않은 이미지 타입입니다. (CATEGORY)\"\n" +
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
                    description = "파일 용량 초과 - Status: 413 Payload Too Large",
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
            )
    })
    ResponseEntity<ImageUploadResponse> uploadCategoryImage(
            @Parameter(
                    description = "업로드할 이미지의 용도 (필수)\n" +
                            "- `CATEGORY`: 카테고리 이미지",
                    required = true,
                    example = "CATEGORY"
            )
            @RequestParam("type") String typeParam,

            @Parameter(
                    description = "업로드할 이미지 파일 (Binary File)\n" +
                            "- 지원 형식: jpg, png, jpeg, gif\n" +
                            "- 최대 크기: 20MB",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            )
            @RequestParam("file") MultipartFile file
    );
}

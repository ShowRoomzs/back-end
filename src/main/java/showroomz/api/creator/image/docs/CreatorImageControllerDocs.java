package showroomz.api.creator.image.docs;

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
public interface CreatorImageControllerDocs {

    @Operation(
            summary = "크리에이터 이미지 업로드",
            description = "크리에이터가 이미지 파일을 업로드하고 업로드된 이미지 URL을 반환합니다.\n\n" +
                    "**type 안내:**\n" +
                    "- `POST`: 게시글 이미지\n" +
                    "- `PRODUCT`: 상품 이미지\n" +
                    "- `MARKET`: 마켓 대표 이미지 (160×160px 이상, 정비율(1:1) 필수)\n\n" +
                    "**권한:** CREATOR\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "업로드 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            {
                                              "imageUrl": "https://cdn.example.com/uploads/post/uuid.jpg"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (type 오류/파일 오류)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효하지 않은 이미지 타입",
                                            value = """
                                                    {
                                                      "code": "INVALID_INPUT",
                                                      "message": "유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, INQUIRY, POST, PRODUCT, MARKET, CATEGORY)"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "빈 파일",
                                            value = """
                                                    {
                                                      "code": "EMPTY_FILE",
                                                      "message": "업로드할 파일이 존재하지 않습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (CREATOR 아님)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "파일 용량 초과(20MB)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "업로드 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<ImageUploadResponse> uploadImage(
            @Parameter(
                    description = "업로드할 이미지의 용도 (필수) - POST | PRODUCT | MARKET",
                    required = true,
                    example = "POST"
            )
            @RequestParam("type") String typeParam,
            @Parameter(
                    description = "업로드할 이미지 파일 (Binary File)",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
            @RequestParam("file") MultipartFile file
    );
}


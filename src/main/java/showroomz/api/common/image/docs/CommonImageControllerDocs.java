package showroomz.api.common.image.docs;

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
public interface CommonImageControllerDocs {

    @Operation(
            summary = "공개 이미지 업로드",
            description = "인증 없이 업로드 가능한 이미지 API입니다.\n\n" +
                    "**허용 `type`:** `SIGNUP_DOCUMENT` (그 외 값은 거부)\n\n" +
                    "**타입별 용도:**\n" +
                    "- `SIGNUP_DOCUMENT`: 판매자 회원가입 증빙(사업자등록증·통신판매업신고증·통장사본 등)\n\n" +
                    "**제약:** jpg, jpeg, png, gif / 최대 20MB / 해상도·비율 제약 없음\n\n" +
                    "**권한:** 인증 불필요"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "업로드 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImageUploadResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공",
                                            value = "{\n" +
                                                    "  \"imageUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket-name/uploads/signup_document/uuid.jpg\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효하지 않은 타입",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, INQUIRY, POST, PRODUCT, MARKET, CATEGORY)\"}"
                                    ),
                                    @ExampleObject(
                                            name = "빈 파일",
                                            value = "{\"code\": \"EMPTY_FILE\", \"message\": \"업로드할 파일이 존재하지 않습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "용량 초과",
                                            value = "{\"code\": \"FILE_SIZE_EXCEEDED\", \"message\": \"이미지 용량은 최대 20MB까지 등록 가능합니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ImageUploadResponse> uploadPublicImage(
            @Parameter(
                    description = "업로드 용도\n- `SIGNUP_DOCUMENT`: 회원가입 증빙 서류",
                    required = true,
                    example = "SIGNUP_DOCUMENT"
            )
            @RequestParam("type") String typeParam,

            @Parameter(
                    description = "업로드할 이미지 파일",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            )
            @RequestParam("file") MultipartFile file
    );
}

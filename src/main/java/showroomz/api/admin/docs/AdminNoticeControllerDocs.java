package showroomz.api.admin.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.notice.dto.AdminNoticeRegisterRequest;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;

@Tag(name = "Admin - Notice", description = "관리자 공지 관리 API\n\n" +
        "공지 등록 및 노출 여부 설정. 등록된 공지는 사용자 API(/v1/user/notices)에서 조회됩니다.")
public interface AdminNoticeControllerDocs {

    @Operation(
            summary = "공지 등록",
            description = "관리자가 새로운 공지를 등록합니다.\n\n" +
                    "**노출 여부 (isVisible):**\n" +
                    "- 생략 또는 `true`: 공개 공지 (사용자 공지 목록/상세에 노출)\n" +
                    "- `false`: 비공개 공지 (관리자만 확인, 사용자에게 미노출)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**응답:** 201 Created, Location 헤더에 생성된 공지 상세 조회 경로 반환 (`/v1/user/notices/{noticeId}`)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공 - Location 헤더에 생성된 리소스 경로 반환"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (제목/내용 필수)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효성 검증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    { \"field\": \"title\", \"reason\": \"제목은 필수 입력값입니다.\" }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (ADMIN 권한 필요)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "공지 등록 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminNoticeRegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "공개 공지 등록",
                                    value = "{\n" +
                                            "  \"title\": \"서비스 점검 안내\",\n" +
                                            "  \"content\": \"2025년 1월 20일 02:00~04:00 점검 예정입니다.\",\n" +
                                            "  \"isVisible\": true\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "비공개 공지 등록",
                                    value = "{\n" +
                                            "  \"title\": \"(내부) 공지 초안\",\n" +
                                            "  \"content\": \"검토 후 공개 예정\",\n" +
                                            "  \"isVisible\": false\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> registerNotice(@Valid @RequestBody AdminNoticeRegisterRequest request);
}

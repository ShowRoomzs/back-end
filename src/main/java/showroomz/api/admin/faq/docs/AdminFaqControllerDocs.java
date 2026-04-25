package showroomz.api.admin.faq.docs;

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
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.dto.FaqReorderRequest;
import showroomz.api.app.auth.DTO.ErrorResponse;

@Tag(name = "Admin - FAQ", description = "관리자 FAQ 관리 API")
public interface AdminFaqControllerDocs {

    @Operation(
            summary = "FAQ 등록",
            description = "관리자가 새로운 FAQ를 등록합니다.\n\n" +
                    "**카테고리:** DELIVERY, CANCEL_EXCHANGE_REFUND, PRODUCT_AS, ORDER_PAYMENT, SERVICE, USAGE_GUIDE, MEMBER_INFO (전체/ALL 불가)\n\n" +
                    "**노출 여부:**\n" +
                    "- `isVisible`을 생략하면 기본값 `true`로 저장됩니다.\n" +
                    "- `isVisible=false`로 등록하면 비공개 FAQ로 저장됩니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공 - Location 헤더에 생성된 리소스 경로 반환"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (유효성 검증 실패 시 첫 번째 필드 메시지 반환)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효성 검증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"질문 내용을 입력해주세요.\"\n" +
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
            description = "FAQ 등록 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminFaqRegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "공개 FAQ 등록",
                                    value = "{\n" +
                                            "  \"category\": \"DELIVERY\",\n" +
                                            "  \"question\": \"배송은 얼마나 걸리나요?\",\n" +
                                            "  \"answer\": \"평균 2~3일 소요됩니다.\",\n" +
                                            "  \"isVisible\": true\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "비공개 FAQ 등록",
                                    value = "{\n" +
                                            "  \"category\": \"ORDER_PAYMENT\",\n" +
                                            "  \"question\": \"(내부용) 특정 CS 대응 문구\",\n" +
                                            "  \"answer\": \"(내부용) 상황에 따라 안내\",\n" +
                                            "  \"isVisible\": false\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> registerFaq(@Valid @RequestBody AdminFaqRegisterRequest request);

    @Operation(
            summary = "FAQ 노출 순서 변경",
            description = "관리자가 FAQ ID 목록 순서대로 노출 순서를 일괄 변경합니다.\n\n" +
                    "**동작 규칙:**\n" +
                    "- 배열 인덱스 순서를 기준으로 displayOrder가 1부터 재부여됩니다.\n" +
                    "- 요청한 FAQ ID는 모두 존재해야 하며, 중복 ID는 허용되지 않습니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "정렬 변경 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (빈 배열, null ID, 중복 ID 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청한 FAQ ID 중 존재하지 않는 데이터가 포함됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "FAQ 정렬 순서 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = FaqReorderRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "FAQ 순서 변경",
                                    value = "{\n" +
                                            "  \"faqIds\": [5, 2, 7, 1]\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> reorderFaqs(@Valid @RequestBody FaqReorderRequest request);
}


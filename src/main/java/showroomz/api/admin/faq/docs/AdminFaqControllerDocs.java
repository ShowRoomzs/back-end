package showroomz.api.admin.faq.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.faq.dto.AdminFaqListRequest;
import showroomz.api.admin.faq.dto.AdminFaqListResponse;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.dto.AdminFaqUpdateRequest;
import showroomz.api.admin.faq.dto.FaqReorderRequest;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - FAQ", description = "관리자 FAQ 관리 API")
public interface AdminFaqControllerDocs {

    @Operation(
            summary = "FAQ 등록",
            description = "관리자가 새로운 FAQ를 등록합니다.\n\n" +
                    "**카테고리 조회:**\n" +
                    "- 카테고리 값(`category`)은 Common API `GET /v1/common/faqs/categories`를 사용해 조회하세요.\n\n" +
                    "**카테고리:** ALL, DELIVERY, CANCEL_EXCHANGE_REFUND, PRODUCT_AS, ORDER_PAYMENT, SERVICE, USAGE_GUIDE, MEMBER_INFO\n\n" +
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
                                    name = "FAQ 등록",
                                    value = "{\n" +
                                            "  \"category\": \"DELIVERY\",\n" +
                                            "  \"question\": \"배송은 얼마나 걸리나요?\",\n" +
                                            "  \"answer\": \"평균 2~3일 소요됩니다.\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> registerFaq(@Valid @RequestBody AdminFaqRegisterRequest request);

    @Operation(
            summary = "FAQ 노출 순서 변경",
            description = "관리자가 `reorderList`에 담긴 FAQ별 `displayOrder`를 반영해 노출 순서를 변경합니다.\n\n" +
                    "**요청 바디:**\n" +
                    "- `reorderList`: `{ \"faqId\": number, \"displayOrder\": number }` 객체 배열 (비어 있을 수 없음)\n" +
                    "- 각 항목의 `displayOrder` 값이 해당 FAQ에 그대로 저장됩니다.\n\n" +
                    "**동작 규칙:**\n" +
                    "- 일부 FAQ만 전달해도 됩니다. 요청에 포함된 FAQ만 순서가 갱신됩니다.\n" +
                    "- `reorderList` 안의 `faqId`는 서로 중복될 수 없습니다.\n" +
                    "- 요청한 모든 `faqId`는 DB에 존재해야 합니다.\n\n" +
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
                    description = "입력값 오류 (빈 reorderList, null faqId·displayOrder, 중복 faqId 등)",
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
            description = "변경할 FAQ와 노출 순서 목록. `reorderList`에 `faqId`와 적용할 `displayOrder`를 쌍으로 넣습니다.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = FaqReorderRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "일부 FAQ만 순서 변경",
                                    value = "{\n" +
                                            "  \"reorderList\": [\n" +
                                            "    { \"faqId\": 5, \"displayOrder\": 10 },\n" +
                                            "    { \"faqId\": 2, \"displayOrder\": 20 }\n" +
                                            "  ]\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "여러 FAQ 순서 일괄 반영",
                                    value = "{\n" +
                                            "  \"reorderList\": [\n" +
                                            "    { \"faqId\": 5, \"displayOrder\": 1 },\n" +
                                            "    { \"faqId\": 2, \"displayOrder\": 2 },\n" +
                                            "    { \"faqId\": 7, \"displayOrder\": 3 },\n" +
                                            "    { \"faqId\": 1, \"displayOrder\": 4 }\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> reorderFaqs(@Valid @RequestBody FaqReorderRequest request);

    @Operation(
            summary = "FAQ 단일 조회",
            description = "관리자가 FAQ를 단건 조회합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
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
                    description = "존재하지 않는 FAQ",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminFaqListResponse> getFaq(
            @Parameter(description = "FAQ ID", required = true) Long faqId
    );

    @Operation(
            summary = "FAQ 목록 조회",
            description = "관리자가 FAQ 목록을 조회합니다.\n\n" +
                    "**카테고리 조회:**\n" +
                    "- 카테고리 값(`category`)은 Common API `GET /v1/common/faqs/categories`를 사용해 조회하세요.\n\n" +
                    "**필터 조건:** 카테고리, 질문/답변 키워드 (복합 적용 가능)\n\n" +
                    "**정렬:** 노출 순서(displayOrder) 오름차순\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
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
    @Parameters({
            @Parameter(
                    name = "category",
                    description = "카테고리 필터 (미입력/ALL 시 전체)",
                    example = "DELIVERY",
                    schema = @Schema(allowableValues = {"ALL", "DELIVERY", "CANCEL_EXCHANGE_REFUND", "PRODUCT_AS", "ORDER_PAYMENT", "SERVICE", "USAGE_GUIDE", "MEMBER_INFO"})
            ),
            @Parameter(
                    name = "keyword",
                    description = "질문 또는 답변 키워드 필터",
                    example = "배송"
            ),
            @Parameter(
                    name = "page",
                    description = "페이지 번호 (1부터 시작)",
                    example = "1"
            ),
            @Parameter(
                    name = "size",
                    description = "페이지당 항목 수",
                    example = "20"
            )
    })
    ResponseEntity<PageResponse<AdminFaqListResponse>> getFaqs(
            @Parameter(hidden = true) AdminFaqListRequest request,
            @Parameter(hidden = true) PagingRequest pagingRequest
    );

    @Operation(
            summary = "FAQ 수정",
            description = "관리자가 FAQ의 카테고리, 질문, 답변을 수정합니다.\n\n" +
                    "**카테고리:** DELIVERY, CANCEL_EXCHANGE_REFUND, PRODUCT_AS, ORDER_PAYMENT, SERVICE, USAGE_GUIDE, MEMBER_INFO (전체/ALL 불가)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "수정 성공"
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 FAQ",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "FAQ 수정 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminFaqUpdateRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "FAQ 수정",
                                    value = "{\n" +
                                            "  \"category\": \"DELIVERY\",\n" +
                                            "  \"question\": \"배송은 얼마나 걸리나요?\",\n" +
                                            "  \"answer\": \"평균 3~5일 소요됩니다.\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateFaq(
            @Parameter(description = "FAQ ID", required = true) Long faqId,
            @Valid @RequestBody AdminFaqUpdateRequest request
    );

    @Operation(
            summary = "FAQ 단일 삭제",
            description = "관리자가 FAQ를 단건 삭제합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "삭제 성공"
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
                    description = "존재하지 않는 FAQ",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteFaq(
            @Parameter(description = "FAQ ID", required = true) Long faqId
    );
}


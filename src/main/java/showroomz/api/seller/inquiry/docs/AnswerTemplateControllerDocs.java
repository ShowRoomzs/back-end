package showroomz.api.seller.inquiry.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.inquiry.dto.AnswerTemplateDeleteRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateDto;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterResponse;
import showroomz.api.seller.inquiry.dto.AnswerTemplateUpdateRequest;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Seller - Answer Template", description = "판매자 답변 템플릿 API")
public interface AnswerTemplateControllerDocs {

    @Operation(
            summary = "답변 템플릿 등록",
            description = "문의 답변에 사용할 템플릿을 등록합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**필수 입력:**\n" +
                    "- `title`: 템플릿 제목 (최대 30자)\n" +
                    "- `category`: 카테고리 (PRODUCT, SIZE, STOCK, DELIVERY, ORDER_PAYMENT, CANCEL_REFUND_EXCHANGE, DEFECT_AS)\n" +
                    "- `content`: 답변 내용 (최대 1000자)\n\n" +
                    "**선택 입력:**\n" +
                    "- `isActive`: 사용 여부. false 설정 시 템플릿 목록에 노출되지 않습니다. 기본값은 true(사용)입니다.\n\n" +
                    "**카테고리 설명:**\n" +
                    "| 코드 | 설명 |\n" +
                    "|------|------|\n" +
                    "| `PRODUCT` | 상품 |\n" +
                    "| `SIZE` | 사이즈 |\n" +
                    "| `STOCK` | 재고/재입고 |\n" +
                    "| `DELIVERY` | 배송 |\n" +
                    "| `ORDER_PAYMENT` | 주문/결제 |\n" +
                    "| `CANCEL_REFUND_EXCHANGE` | 취소/교환/환불 |\n" +
                    "| `DEFECT_AS` | 불량/AS |"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "답변 템플릿 등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AnswerTemplateRegisterResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "등록 성공",
                                            value = "{\n" +
                                                    "  \"templateId\": 1\n" +
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
                                            name = "제목 30자 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"템플릿 제목은 최대 30자까지 입력 가능합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "답변 내용 1000자 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"답변 내용은 최대 1000자까지 입력 가능합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "필수 항목 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\"\n" +
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
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "답변 템플릿 등록 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AnswerTemplateRegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "사용 중인 템플릿 등록",
                                    value = "{\n" +
                                            "  \"title\": \"재입고 안내 템플릿\",\n" +
                                            "  \"category\": \"STOCK\",\n" +
                                            "  \"content\": \"안녕하세요, 해당 상품은 다음 주 중 재입고 예정입니다. 감사합니다.\",\n" +
                                            "  \"isActive\": true\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "미사용 템플릿 등록",
                                    value = "{\n" +
                                            "  \"title\": \"배송 지연 안내\",\n" +
                                            "  \"category\": \"DELIVERY\",\n" +
                                            "  \"content\": \"현재 물류 상황으로 인해 배송이 다소 지연될 수 있습니다. 양해 부탁드립니다.\",\n" +
                                            "  \"isActive\": false\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<AnswerTemplateRegisterResponse> registerTemplate(
            @Valid @RequestBody AnswerTemplateRegisterRequest request
    );

    @Operation(
            summary = "답변 템플릿 목록 조회",
            description = "본인 마켓의 답변 템플릿 목록을 페이징 조회합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**필터 조건 (선택):**\n" +
                    "- `includeInactive`: 미사용(isActive=false) 템플릿 포함 여부. `true` 설정 시 사용/미사용 템플릿 모두 조회, `false`(기본값) 설정 시 사용 중인(isActive=true) 템플릿만 조회\n" +
                    "- `category`: 카테고리로 필터링. 미입력 시 전체 조회\n" +
                    "- `keyword`: 템플릿 제목 검색 (부분 일치). 미입력 시 전체 조회\n\n" +
                    "**페이징:**\n" +
                    "- `page`: 페이지 번호 (1부터 시작, 기본값 1)\n" +
                    "- `size`: 페이지당 항목 수 (기본값 20)\n\n" +
                    "**정렬:** 수정일시 내림차순(최근 수정 순)\n\n" +
                    "**카테고리 코드:**\n" +
                    "| 코드 | 설명 |\n" +
                    "|------|------|\n" +
                    "| `PRODUCT` | 상품 |\n" +
                    "| `SIZE` | 사이즈 |\n" +
                    "| `STOCK` | 재고/재입고 |\n" +
                    "| `DELIVERY` | 배송 |\n" +
                    "| `ORDER_PAYMENT` | 주문/결제 |\n" +
                    "| `CANCEL_REFUND_EXCHANGE` | 취소/교환/환불 |\n" +
                    "| `DEFECT_AS` | 불량/AS |"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "전체 조회",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"templateId\": 1,\n" +
                                                    "      \"title\": \"재입고 안내 템플릿\",\n" +
                                                    "      \"category\": \"STOCK\",\n" +
                                                    "      \"categoryName\": \"재고/재입고 문의\",\n" +
                                                    "      \"content\": \"안녕하세요, 해당 상품은 다음 주 중 재입고 예정입니다.\",\n" +
                                                    "      \"createdAt\": \"2026-04-01T10:30:00\",\n" +
                                                    "      \"modifiedAt\": \"2026-04-01T12:00:00\",\n" +
                                                    "      \"isActive\": true\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"templateId\": 2,\n" +
                                                    "      \"title\": \"배송 문의 안내\",\n" +
                                                    "      \"category\": \"DELIVERY\",\n" +
                                                    "      \"categoryName\": \"배송\",\n" +
                                                    "      \"content\": \"평균 배송 기간은 영업일 기준 2~3일입니다.\",\n" +
                                                    "      \"createdAt\": \"2026-04-01T09:00:00\",\n" +
                                                    "      \"modifiedAt\": \"2026-04-01T09:00:00\",\n" +
                                                    "      \"isActive\": true\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 3,\n" +
                                                    "    \"totalResults\": 42,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  }\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "결과 없음",
                                            value = "{\n" +
                                                    "  \"content\": [],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 0,\n" +
                                                    "    \"totalResults\": 0,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
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
            )
    })
    ResponseEntity<PageResponse<AnswerTemplateDto>> getTemplates(
            @Parameter(description = "미사용 템플릿 포함 여부. true 시 사용/미사용 모두 조회, false(기본값) 시 사용 중인 템플릿만 조회",
                    example = "false",
                    schema = @Schema(defaultValue = "false"))
            @RequestParam(value = "includeInactive", required = false, defaultValue = "false") Boolean includeInactive,

            @Parameter(description = "카테고리 필터. 미입력 시 전체 조회",
                    example = "STOCK",
                    schema = @Schema(allowableValues = {"PRODUCT", "SIZE", "STOCK", "DELIVERY", "ORDER_PAYMENT", "CANCEL_REFUND_EXCHANGE", "DEFECT_AS"}))
            @RequestParam(value = "category", required = false) MarketInquiryFilterType category,

            @Parameter(description = "템플릿 제목 검색어 (부분 일치). 미입력 시 전체 조회", example = "재입고")
            @RequestParam(value = "keyword", required = false) String keyword,

            @Parameter(description = "페이징 요청 정보 (page: 1부터 시작, size: 페이지당 항목 수)")
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "답변 템플릿 단건 조회",
            description = "특정 답변 템플릿의 상세 정보를 조회합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "- 본인이 등록한 템플릿만 조회할 수 있습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AnswerTemplateDto.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"templateId\": 1,\n" +
                                            "  \"title\": \"재입고 안내 템플릿\",\n" +
                                            "  \"category\": \"STOCK\",\n" +
                                            "  \"categoryName\": \"재고/재입고 문의\",\n" +
                                            "  \"content\": \"안녕하세요, 해당 상품은 다음 주 중 재입고 예정입니다.\",\n" +
                                            "  \"createdAt\": \"2026-04-01T10:30:00\",\n" +
                                            "  \"modifiedAt\": \"2026-04-01T12:00:00\",\n" +
                                            "  \"isActive\": true\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "템플릿을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"NOT_FOUND_DATA\",\n  \"message\": \"데이터를 찾을 수 없습니다.\"\n}")
                    )
            )
    })
    ResponseEntity<AnswerTemplateDto> getTemplate(
            @Parameter(description = "템플릿 ID", required = true, example = "1")
            @PathVariable("templateId") Long templateId
    );

    @Operation(
            summary = "답변 템플릿 수정",
            description = "특정 답변 템플릿의 내용을 수정합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "- 본인이 등록한 템플릿만 수정할 수 있습니다.\n" +
                    "- 모든 필드는 필수 입력입니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "제목 30자 초과", value = "{\n  \"code\": \"INVALID_INPUT\",\n  \"message\": \"템플릿 제목은 최대 30자까지 입력 가능합니다.\"\n}"),
                                    @ExampleObject(name = "답변 내용 1000자 초과", value = "{\n  \"code\": \"INVALID_INPUT\",\n  \"message\": \"답변 내용은 최대 1000자까지 입력 가능합니다.\"\n}")
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "템플릿을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"NOT_FOUND_DATA\",\n  \"message\": \"데이터를 찾을 수 없습니다.\"\n}")
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "템플릿 수정 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AnswerTemplateUpdateRequest.class),
                    examples = @ExampleObject(
                            value = "{\n" +
                                    "  \"title\": \"재입고 안내 - 수정본\",\n" +
                                    "  \"category\": \"STOCK\",\n" +
                                    "  \"content\": \"안녕하세요, 해당 상품은 이번 주 내로 재입고 예정입니다.\",\n" +
                                    "  \"isActive\": true\n" +
                                    "}"
                    )
            )
    )
    ResponseEntity<Void> updateTemplate(
            @Parameter(description = "템플릿 ID", required = true, example = "1")
            @PathVariable("templateId") Long templateId,
            @Valid @RequestBody AnswerTemplateUpdateRequest request
    );

    @Operation(
            summary = "답변 템플릿 삭제",
            description = "템플릿 ID 목록을 전달하여 여러 템플릿을 한 번에 삭제합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "- 본인이 등록한 템플릿만 삭제할 수 있습니다.\n" +
                    "- 목록 중 본인 소유가 아닌 ID는 무시됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"INVALID_INPUT\",\n  \"message\": \"삭제할 템플릿 ID를 하나 이상 입력해주세요.\"\n}")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "삭제할 템플릿 ID 목록",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AnswerTemplateDeleteRequest.class),
                    examples = @ExampleObject(value = "{\n  \"templateIds\": [1, 2, 3]\n}")
            )
    )
    ResponseEntity<Void> deleteTemplates(
            @Valid @RequestBody AnswerTemplateDeleteRequest request
    );
}

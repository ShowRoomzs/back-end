package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.inquiry.dto.InquiryDetailResponse;
import showroomz.api.app.inquiry.dto.InquiryListResponse;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.InquiryRegisterResponse;
import showroomz.api.app.inquiry.dto.InquiryUpdateRequest;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Inquiry (1:1 문의)", description = "1:1 문의 관련 API")
public interface InquiryControllerDocs {

    @Operation(
            summary = "1:1 문의 등록",
            description = "문의 타입(Enum)과 상세 유형(String), 내용을 입력하여 1:1 문의를 등록합니다.\n\n" +
                    "**필수 값:**\n" +
                    "- `type`: 문의 타입 (DELIVERY, ORDER_PAYMENT, CANCEL_REFUND_EXCHANGE, USER_INFO, PRODUCT_CHECK, SERVICE)\n" +
                    "- `category`: 문의 유형 (현재는 string 타입으로 임의값 입력, 추후 기획 시 enum으로 변경 예정)\n" +
                    "- `content`: 문의 내용\n\n" +
                    "**선택 값:**\n" +
                    "- `imageUrls`: 첨부 이미지 URL 리스트 (최대 10장)\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공 - Status: 201 Created (생성된 문의 ID 반환)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InquiryRegisterResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"inquiryId\": 1\n" +
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
                                            name = "유효성 검증 실패",
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
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "1:1 문의 등록 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = InquiryRegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "배송 지연 문의",
                                    value = "{\n" +
                                            "  \"type\": \"DELIVERY\",\n" +
                                            "  \"category\": \"배송 지연\",\n" +
                                            "  \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다. 배송 일정을 확인 부탁드립니다.\",\n" +
                                            "  \"imageUrls\": [\n" +
                                            "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<InquiryRegisterResponse> registerInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @RequestBody InquiryRegisterRequest request
    );

    @Operation(
            summary = "내 문의 내역 조회",
            description = "현재 로그인한 사용자가 등록한 1:1 문의 목록을 최신순으로 페이징 조회합니다.\n\n" +
                    "**정렬 기준:**\n" +
                    "- 생성일(`createdAt`) 기준 내림차순\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 1,\n" +
                                                    "      \"type\": \"DELIVERY\",\n" +
                                                    "      \"category\": \"배송 지연\",\n" +
                                                    "      \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다.\",\n" +
                                                    "      \"status\": \"WAITING\",\n" +
                                                    "      \"createdAt\": \"2025-02-07T10:30:00\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 5,\n" +
                                                    "    \"totalResults\": 48,\n" +
                                                    "    \"limit\": 10,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  }\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    PageResponse<InquiryListResponse> getMyInquiries(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "페이징 요청 정보 (page: 1부터 시작, size: 페이지당 항목 수)", required = true)
            PagingRequest pagingRequest
    );

    @Operation(
            summary = "문의 상세 조회",
            description = "특정 1:1 문의의 상세 정보(타입, 상세 유형, 내용, 이미지, 답변 상태/내용 등)를 조회합니다.\n\n" +
                    "- 본인이 등록한 문의만 조회할 수 있습니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InquiryDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "답변 대기 예시",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"type\": \"DELIVERY\",\n" +
                                                    "  \"category\": \"배송 지연\",\n" +
                                                    "  \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다. 배송 일정을 확인 부탁드립니다.\",\n" +
                                                    "  \"imageUrls\": [\n" +
                                                    "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                                    "  ],\n" +
                                                    "  \"status\": \"WAITING\",\n" +
                                                    "  \"answerContent\": null,\n" +
                                                    "  \"answeredAt\": null,\n" +
                                                    "  \"createdAt\": \"2025-02-07T10:30:00\"\n" +
                                                    "}",
                                            description = "답변 대기 중인 문의"
                                    ),
                                    @ExampleObject(
                                            name = "답변 완료 예시",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"type\": \"DELIVERY\",\n" +
                                                    "  \"category\": \"배송 지연\",\n" +
                                                    "  \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다. 배송 일정을 확인 부탁드립니다.\",\n" +
                                                    "  \"imageUrls\": [\n" +
                                                    "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                                    "  ],\n" +
                                                    "  \"status\": \"ANSWERED\",\n" +
                                                    "  \"answerContent\": \"죄송합니다. 해당 주문은 현재 출고 준비 중이며, 내일 발송 예정입니다.\",\n" +
                                                    "  \"answeredAt\": \"2025-02-07T14:00:00\",\n" +
                                                    "  \"createdAt\": \"2025-02-07T10:30:00\"\n" +
                                                    "}",
                                            description = "답변 완료된 문의"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 문의에 대한 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 리소스에 대한 접근 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문의 또는 사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "문의 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"NOT_FOUND_DATA\",\n" +
                                                    "  \"message\": \"데이터를 찾을 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    InquiryDetailResponse getInquiryDetail(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "조회할 문의 ID", required = true, example = "1")
            @PathVariable Long inquiryId
    );

    @Operation(
            summary = "문의 수정",
            description = "답변 대기 중인 1:1 문의의 내용을 수정합니다.\n\n" +
                    "- 본인이 등록한 문의만 수정할 수 있습니다.\n" +
                    "- 답변이 완료된 문의는 수정할 수 없습니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공 - Status: 200 OK (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 답변 완료된 문의 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "답변 완료된 문의",
                                            value = "{\n" +
                                                    "  \"code\": \"INQUIRY_ALREADY_ANSWERED\",\n" +
                                                    "  \"message\": \"답변이 완료된 문의는 수정하거나 삭제할 수 없습니다.\"\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 문의에 대한 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문의를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "문의 수정 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = InquiryUpdateRequest.class)
            )
    )
    ResponseEntity<Void> updateInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "수정할 문의 ID", required = true, example = "1")
            @PathVariable Long inquiryId,
            @RequestBody InquiryUpdateRequest request
    );

    @Operation(
            summary = "문의 삭제",
            description = "답변 대기 중인 1:1 문의를 삭제합니다. (물리 삭제)\n\n" +
                    "- 본인이 등록한 문의만 삭제할 수 있습니다.\n" +
                    "- 답변이 완료된 문의는 삭제할 수 없습니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공 - Status: 200 OK (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "답변 완료된 문의 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "답변 완료된 문의",
                                            value = "{\n" +
                                                    "  \"code\": \"INQUIRY_ALREADY_ANSWERED\",\n" +
                                                    "  \"message\": \"답변이 완료된 문의는 수정하거나 삭제할 수 없습니다.\"\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 문의에 대한 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문의를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "삭제할 문의 ID", required = true, example = "1")
            @PathVariable Long inquiryId
    );
}


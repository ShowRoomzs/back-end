package showroomz.api.app.inquiry.docs;

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
import showroomz.api.app.inquiry.dto.InquirySummaryResponse;
import showroomz.api.app.inquiry.dto.InquiryUpdateRequest;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Inquiry (1:1 문의)", description = "1:1 문의 관련 API")
public interface InquiryControllerDocs {

    @Operation(
            summary = "1:1 문의 등록",
            description = "문의 유형과 내용을 입력하여 1:1 문의를 등록합니다. 등록된 문의는 마켓이 아닌 **어드민(운영자)**에게만 전달됩니다.\n\n" +
                    "**필수 값:**\n" +
                    "- `type`: 문의 유형 5종 (DELIVERY, CANCEL_EXCHANGE_RETURN, ORDER_PAYMENT, SERVICE, ACCOUNT) — 소분류는 없습니다\n" +
                    "- `content`: 문의 내용 (최대 1000자)\n\n" +
                    "**선택 값:**\n" +
                    "- `imageUrls`: 첨부 이미지 URL 리스트 (최대 5장)\n" +
                    "- `orderId`: 참조 주문 ID — **모든 유형에서 선택값**입니다. 주문 없이도 문의할 수 있습니다\n\n" +
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
                                    name = "주문 연결 없이 문의",
                                    value = "{\n" +
                                            "  \"type\": \"DELIVERY\",\n" +
                                            "  \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다. 배송 일정을 확인 부탁드립니다.\",\n" +
                                            "  \"imageUrls\": [\n" +
                                            "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                            "  ]\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "참조 주문을 연결한 문의",
                                    value = "{\n" +
                                            "  \"type\": \"CANCEL_EXCHANGE_RETURN\",\n" +
                                            "  \"content\": \"주문을 취소하고 싶습니다.\",\n" +
                                            "  \"imageUrls\": [\n" +
                                            "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                            "  ],\n" +
                                            "  \"orderId\": 123456\n" +
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
                    "- 생성일(`createdAt`) 기준 내림차순 (정렬 옵션 없음 — 문의 내역은 항상 최신순입니다)\n\n" +
                    "**필터 (`status` 쿼리 파라미터, 선택):**\n" +
                    "- 생략: 전체\n" +
                    "- `WAITING`: 답변 대기만 — 화면의 [답변 대기만] 체크에 대응합니다\n" +
                    "- `ANSWERED`: 답변완료만\n\n" +
                    "필터를 반영한 건수는 `pageInfo.totalResults`로 내려가며, 화면 상단의 `전체 N건` / `답변 대기 N건` 표기에 사용합니다.\n\n" +
                    "**status 값:**\n" +
                    "- `WAITING`: 접수(답변 대기)\n" +
                    "- `ANSWERED`: 답변완료\n\n" +
                    "**주문 카드(`order`):** 주문을 연결한 문의만 값이 있고, 연결하지 않았으면 `null`입니다.\n" +
                    "`orderNumber`(주문번호) · `orderDate`(주문일) · `productName`(대표 상품명) · `productImageUrl`(썸네일)로 목록의 주문 카드를 그립니다.\n\n" +
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
                                                    "      \"typeName\": \"배송\",\n" +
                                                    "      \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다. 배송 일정을 확인 부탁드립니다.\",\n" +
                                                    "      \"imageUrls\": [\n" +
                                                    "        \"https://example.com/inquiries/img1.jpg\"\n" +
                                                    "      ],\n" +
                                                    "      \"orderId\": 1147,\n" +
                                                    "      \"order\": {\n" +
                                                    "        \"orderId\": 1147,\n" +
                                                    "        \"orderNumber\": \"20260803-1147\",\n" +
                                                    "        \"orderDate\": \"2026-08-03T13:20:00\",\n" +
                                                    "        \"productName\": \"시카 리페어 앰플 30ml 리필 2개 세트\",\n" +
                                                    "        \"productImageUrl\": \"https://example.com/orders/1147/thumb.jpg\",\n" +
                                                    "        \"productCount\": 1\n" +
                                                    "      },\n" +
                                                    "      \"status\": \"WAITING\",\n" +
                                                    "      \"answerContent\": null,\n" +
                                                    "      \"answeredAt\": null,\n" +
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
    PageResponse<InquiryListResponse> getMyInquiries(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "답변 상태 필터 (선택) — 생략하면 전체, `WAITING`이면 [답변 대기만]",
                    example = "WAITING", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
            InquiryStatus status,
            @Parameter(description = "페이징 요청 정보 (page: 1부터 시작, size: 페이지당 항목 수)", required = true)
            PagingRequest pagingRequest
    );

    @Operation(
            summary = "문의 내역 탭 건수 조회",
            description = "문의 내역 화면 상단 탭([1:1 문의 N] [상품 문의 N]) 배지용 건수를 한 번에 조회합니다.\n\n" +
                    "탭을 눌러 보기 전에도 어느 쪽에 내역이 있는지 보여야 하므로 1:1 문의와 상품 문의 건수를 함께 내려줍니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InquirySummaryResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"oneToOneTotal\": 4,\n" +
                                                    "  \"oneToOneWaiting\": 1,\n" +
                                                    "  \"productTotal\": 3,\n" +
                                                    "  \"productWaiting\": 1\n" +
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
            )
    })
    InquirySummaryResponse getInquirySummary(
            @Parameter(hidden = true) UserPrincipal userPrincipal
    );

    @Operation(
            summary = "문의 상세 조회",
            description = "특정 1:1 문의의 상세 정보(유형, 내용, 이미지, 답변 상태/내용 등)를 조회합니다.\n\n" +
                    "- 본인이 등록한 문의만 조회할 수 있습니다.\n\n" +
                    "**스레드 구성 순서:** 상태·유형·날짜 머리 → 연결된 주문(`order`) → 내 문의(`writerNickname`, `content`, `imageUrls`) → 운영자 답변(`answererName`, `answerContent`, `answeredAt`)\n\n" +
                    "**주문 카드(`order`):** 주문을 연결하지 않은 문의는 `null`이며, 이 경우 화면에서 블록 자체를 노출하지 않습니다.\n\n" +
                    "**답변자(`answererName`):** 1:1 문의 답변 주체는 항상 운영팀(`쇼룸즈 고객센터`)이며, 답변 대기 상태이면 `null`입니다.\n\n" +
                    "**status 값:**\n" +
                    "- `WAITING`: 접수(답변 대기)\n" +
                    "- `ANSWERED`: 답변완료\n\n" +
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
                                                    "  \"typeName\": \"배송\",\n" +
                                                    "  \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다. 배송 일정을 확인 부탁드립니다.\",\n" +
                                                    "  \"imageUrls\": [\n" +
                                                    "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                                    "  ],\n" +
                                                    "  \"orderId\": null,\n" +
                                                    "  \"order\": null,\n" +
                                                    "  \"writerNickname\": \"수민\",\n" +
                                                    "  \"writerProfileImageUrl\": \"https://example.com/users/108/profile.jpg\",\n" +
                                                    "  \"status\": \"WAITING\",\n" +
                                                    "  \"answererName\": null,\n" +
                                                    "  \"answerContent\": null,\n" +
                                                    "  \"answeredAt\": null,\n" +
                                                    "  \"createdAt\": \"2025-02-07T10:30:00\"\n" +
                                                    "}",
                                            description = "답변 대기 중인 문의 — 주문을 연결하지 않아 order가 null"
                                    ),
                                    @ExampleObject(
                                            name = "답변 완료 예시",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"type\": \"DELIVERY\",\n" +
                                                    "  \"typeName\": \"배송\",\n" +
                                                    "  \"content\": \"주문한 지 3일이 지났는데 아직 배송 준비 중입니다. 배송 일정을 확인 부탁드립니다.\",\n" +
                                                    "  \"imageUrls\": [\n" +
                                                    "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                                    "  ],\n" +
                                                    "  \"orderId\": 1147,\n" +
                                                    "  \"order\": {\n" +
                                                    "    \"orderId\": 1147,\n" +
                                                    "    \"orderNumber\": \"20260803-1147\",\n" +
                                                    "    \"orderDate\": \"2026-08-03T13:20:00\",\n" +
                                                    "    \"productName\": \"시카 리페어 앰플 30ml 리필 2개 세트\",\n" +
                                                    "    \"productImageUrl\": \"https://example.com/orders/1147/thumb.jpg\",\n" +
                                                    "    \"productCount\": 1\n" +
                                                    "  },\n" +
                                                    "  \"writerNickname\": \"수민\",\n" +
                                                    "  \"writerProfileImageUrl\": \"https://example.com/users/108/profile.jpg\",\n" +
                                                    "  \"status\": \"ANSWERED\",\n" +
                                                    "  \"answererName\": \"쇼룸즈 고객센터\",\n" +
                                                    "  \"answerContent\": \"죄송합니다. 해당 주문은 현재 출고 준비 중이며, 내일 발송 예정입니다.\",\n" +
                                                    "  \"answeredAt\": \"2025-02-07T14:00:00\",\n" +
                                                    "  \"createdAt\": \"2025-02-07T10:30:00\"\n" +
                                                    "}",
                                            description = "답변 완료된 문의 — 주문을 연결한 경우"
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
            @Parameter(description = "조회할 문의 ID", required = true, example = "1", in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
            @PathVariable("inquiryId") Long inquiryId
    );

    @Operation(
            summary = "문의 수정",
            description = "접수(답변 대기) 상태인 1:1 문의의 내용을 수정합니다.\n\n" +
                    "- 본인이 등록한 문의만 수정할 수 있습니다.\n" +
                    "- 답변이 등록된 문의는 수정할 수 없습니다.\n" +
                    "- `orderId`(참조 주문)는 모든 유형에서 선택값입니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "수정 성공 - Status: 204 No Content (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 답변 완료된 문의 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "답변이 등록된 문의",
                                            value = "{\n" +
                                                    "  \"code\": \"INQUIRY_ALREADY_ANSWERED\",\n" +
                                                    "  \"message\": \"이미 답변이 등록된 문의입니다. 답변은 1회만 등록할 수 있습니다.\"\n" +
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
                    description = "문의를 찾을 수 없음 - Status: 404 Not Found",
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "문의 수정 요청 바디 (orderId는 선택값)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = InquiryUpdateRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "수정 예시 (orderId 포함)",
                                    value = "{\n" +
                                            "  \"type\": \"CANCEL_EXCHANGE_RETURN\",\n" +
                                            "  \"content\": \"주문 취소 요청 내용을 수정합니다.\",\n" +
                                            "  \"imageUrls\": [\n" +
                                            "    \"https://example.com/inquiries/img1.jpg\"\n" +
                                            "  ],\n" +
                                            "  \"orderId\": 123456\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "수정할 문의 ID", required = true, example = "1", in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
            @PathVariable("inquiryId") Long inquiryId,
            @RequestBody InquiryUpdateRequest request
    );

    @Operation(
            summary = "문의 삭제",
            description = "접수(답변 대기) 상태인 1:1 문의를 삭제합니다.\n\n" +
                    "- 본인이 등록한 문의만 삭제할 수 있습니다.\n" +
                    "- 답변이 등록된 문의는 삭제할 수 없습니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "삭제 성공 - Status: 204 No Content (응답 본문 없음)"
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
                                                    "  \"message\": \"이미 답변이 등록된 문의입니다. 답변은 1회만 등록할 수 있습니다.\"\n" +
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
                    description = "문의를 찾을 수 없음 - Status: 404 Not Found",
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
    ResponseEntity<Void> deleteInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "삭제할 문의 ID", required = true, example = "1", in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
            @PathVariable("inquiryId") Long inquiryId
    );
}


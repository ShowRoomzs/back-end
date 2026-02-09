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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.inquiry.dto.ProductInquiryListResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryUpdateRequest;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Inquiry Product", description = "상품 문의 관련 API")
public interface ProductInquiryControllerDocs {

    @Operation(
            summary = "상품 문의 등록",
            description = "특정 상품에 대한 문의를 등록합니다.\n\n" +
                    "**필수 값:**\n" +
                    "- `type`: 문의 타입 (DELIVERY, ORDER_PAYMENT, CANCEL_REFUND_EXCHANGE, USER_INFO, PRODUCT_CHECK, SERVICE)\n" +
                    "- `category`: 문의 유형 (상세 사유 - 문자열)\n" +
                    "- `content`: 문의 내용\n\n" +
                    "**선택 값:**\n" +
                    "- `secret`: 비밀글 여부 (기본값: false)\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공 - Status: 201 Created (생성된 상품 문의 ID 반환)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductInquiryRegisterResponse.class),
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
                            schema = @Schema(implementation = ErrorResponse.class)
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
                    responseCode = "404",
                    description = "사용자 또는 상품을 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "상품 문의 등록 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ProductInquiryRegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "상품 문의 예시",
                                    value = "{\n" +
                                            "  \"type\": \"PRODUCT_CHECK\",\n" +
                                            "  \"category\": \"사이즈 문의\",\n" +
                                            "  \"content\": \"키 170에 보통 체형인데 M 사이즈가 맞을까요?\",\n" +
                                            "  \"secret\": false\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<ProductInquiryRegisterResponse> registerInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "문의할 상품 ID", required = true, example = "1", in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
            @PathVariable("productId") Long productId,
            @RequestBody ProductInquiryRegisterRequest request
    );

    @Operation(
            summary = "내 상품 문의 목록 조회",
            description = "현재 로그인한 사용자가 등록한 상품 문의 목록을 최신순으로 페이징 조회합니다.\n\n" +
                    "응답에는 쇼룸 이름, 상품명, 대표 이미지 URL, 문의/답변 내용 등이 포함됩니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class)
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
    PageResponse<ProductInquiryListResponse> getMyInquiries(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "페이징 요청 정보 (page: 1부터 시작, size: 페이지당 항목 수)", required = true)
            PagingRequest pagingRequest
    );

    @Operation(
            summary = "상품 문의 수정",
            description = "답변 대기 중인 상품 문의의 내용을 수정합니다.\n\n" +
                    "- 본인이 등록한 문의만 수정할 수 있습니다.\n" +
                    "- 답변이 완료된 문의는 수정할 수 없습니다.\n\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class)
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
                    description = "상품 문의를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "상품 문의 수정 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ProductInquiryUpdateRequest.class)
            )
    )
    ResponseEntity<Void> updateInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "수정할 상품 문의 ID", required = true, example = "1", in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
            @PathVariable("inquiryId") Long inquiryId,
            @RequestBody ProductInquiryUpdateRequest request
    );

    @Operation(
            summary = "상품 문의 삭제",
            description = "답변 대기 중인 상품 문의를 삭제합니다.\n\n" +
                    "- 본인이 등록한 문의만 삭제할 수 있습니다.\n" +
                    "- 답변이 완료된 문의는 삭제할 수 없습니다.\n\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class)
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
                    description = "상품 문의를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteInquiry(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "삭제할 상품 문의 ID", required = true, example = "1", in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
            @PathVariable("inquiryId") Long inquiryId
    );
}


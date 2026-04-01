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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.inquiry.dto.ProductInquiryDetailResponse;
import showroomz.api.seller.inquiry.dto.SellerInquiryAnswerRequest;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Seller - Inquiry", description = "판매자 상품 문의 답변 API")
public interface SellerInquiryControllerDocs {

    @Operation(
            summary = "판매자 문의 목록 조회",
            description = "본인 마켓 기준으로 상품 문의와 1:1 문의를 통합 조회합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**검색 조건:**\n" +
                    "- 기간(startDate, endDate)\n" +
                    "- 문의 유형(inquiryTypes)\n" +
                    "- 답변 상태(status)\n" +
                    "- 키워드(keyword: 내용, 고객명, 상품명 통합 검색)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "문의 목록 조회 성공"
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
                    responseCode = "404",
                    description = "판매자 마켓을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SellerInquiryListResponse> getInquiries(
            @ModelAttribute SellerInquirySearchCondition condition,
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "상품 문의 상세 조회",
            description = "본인 마켓 상품에 등록된 특정 상품 문의의 상세 정보를 조회합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**포함 정보:**\n" +
                    "- 고객명(실명 우선, 없으면 닉네임)\n" +
                    "- 고객 이메일\n" +
                    "- 문의 정보 및 답변 내용\n" +
                    "- 상품 가격/노출/강제 품절 상태\n" +
                    "- 판매 상태 코드(`ON_SALE`, `UNAVAILABLE`)\n\n" +
                    "**saleStatus 규칙:**\n" +
                    "- `ON_SALE`: 진열 중이며 강제 품절이 아닌 상태\n" +
                    "- `UNAVAILABLE`: 미진열이거나 강제 품절인 상태"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 문의 상세 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductInquiryDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "판매중 상품 문의",
                                            value = "{\n" +
                                                    "  \"inquiryId\": 1,\n" +
                                                    "  \"type\": \"PRODUCT_INQUIRY\",\n" +
                                                    "  \"customerName\": \"홍길동\",\n" +
                                                    "  \"email\": \"user@example.com\",\n" +
                                                    "  \"createdAt\": \"2026-04-01T10:30:00\",\n" +
                                                    "  \"content\": \"이 상품 재입고 예정이 있나요?\",\n" +
                                                    "  \"answerContent\": null,\n" +
                                                    "  \"productName\": \"오버핏 셔츠\",\n" +
                                                    "  \"productCode\": \"SRZ-20260401-001\",\n" +
                                                    "  \"regularPrice\": 59000,\n" +
                                                    "  \"salePrice\": 49000,\n" +
                                                    "  \"isDisplay\": true,\n" +
                                                    "  \"isOutOfStockForced\": false,\n" +
                                                    "  \"saleStatus\": \"ON_SALE\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "판매불가 상품 문의",
                                            value = "{\n" +
                                                    "  \"inquiryId\": 2,\n" +
                                                    "  \"type\": \"STOCK_INQUIRY\",\n" +
                                                    "  \"customerName\": \"쇼룸러버\",\n" +
                                                    "  \"email\": \"lover@example.com\",\n" +
                                                    "  \"createdAt\": \"2026-04-01T11:00:00\",\n" +
                                                    "  \"content\": \"품절 해제 예정이 있나요?\",\n" +
                                                    "  \"answerContent\": \"현재 재입고 일정은 미정입니다.\",\n" +
                                                    "  \"productName\": \"와이드 데님 팬츠\",\n" +
                                                    "  \"productCode\": \"SRZ-20260401-002\",\n" +
                                                    "  \"regularPrice\": 69000,\n" +
                                                    "  \"salePrice\": 59000,\n" +
                                                    "  \"isDisplay\": false,\n" +
                                                    "  \"isOutOfStockForced\": true,\n" +
                                                    "  \"saleStatus\": \"UNAVAILABLE\"\n" +
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
                    description = "권한 없음 (다른 마켓의 상품 문의)",
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
                    responseCode = "404",
                    description = "문의를 찾을 수 없음",
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
    ResponseEntity<ProductInquiryDetailResponse> getInquiryDetail(
            @Parameter(description = "문의 ID", required = true, example = "1")
            @PathVariable("inquiryId") Long inquiryId
    );

    @Operation(
            summary = "상품 문의 답변 등록",
            description = "본인 마켓 상품에 대한 상품 문의에 답변을 등록합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**제약사항:**\n" +
                    "- 이미 답변이 등록된 문의는 다시 답변할 수 없습니다.\n" +
                    "- 본인 마켓의 상품 문의에만 답변 가능합니다.\n" +
                    "- 답변 내용은 최대 500자까지 입력 가능합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "답변 등록 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 이미 답변 완료된 문의",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이미 답변 완료",
                                            value = "{\n" +
                                                    "  \"code\": \"INQUIRY_ALREADY_ANSWERED\",\n" +
                                                    "  \"message\": \"답변이 완료된 문의는 수정하거나 삭제할 수 없습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "답변 내용 500자 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"답변 내용은 최대 500자까지 입력 가능합니다.\"\n" +
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
                    description = "권한 없음 (다른 마켓의 상품 문의)",
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
                    responseCode = "404",
                    description = "문의를 찾을 수 없음",
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
    ResponseEntity<Void> registerAnswer(
            @Parameter(description = "문의 ID", required = true, example = "1")
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryAnswerRequest request
    );
}

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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.inquiry.dto.SellerInquiryAnswerRequest;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;

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
            Pageable pageable
    );

    @Operation(
            summary = "상품 문의 답변 등록",
            description = "본인 마켓 상품에 대한 상품 문의에 답변을 등록합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**제약사항:**\n" +
                    "- 이미 답변이 등록된 문의는 다시 답변할 수 없습니다.\n" +
                    "- 본인 마켓의 상품 문의에만 답변 가능합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "답변 등록 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 답변 완료된 문의",
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

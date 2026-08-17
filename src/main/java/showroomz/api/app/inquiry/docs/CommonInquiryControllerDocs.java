package showroomz.api.app.inquiry.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;

import java.util.List;

@Tag(name = "User - Inquiry (1:1 문의)", description = "1:1 문의 관련 API")
public interface CommonInquiryControllerDocs {

    @Operation(
            summary = "1:1 문의 유형 목록 조회",
            description = "문의 등록 시 선택할 수 있는 유형 5종을 조회합니다. 소분류는 없습니다.\n\n" +
                    "FAQ 카테고리와 동일한 분류 체계를 사용합니다 — 배송 · 취소/교환/반품 · 주문·결제 · 서비스 · 계정\n\n" +
                    "**권한:** 인증 불필요 (비로그인 허용)\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InquiryCategoryResponse.class)),
                            examples = @ExampleObject(value = "[\n" +
                                    "  { \"key\": \"DELIVERY\", \"description\": \"배송\" },\n" +
                                    "  { \"key\": \"CANCEL_EXCHANGE_RETURN\", \"description\": \"취소/교환/반품\" },\n" +
                                    "  { \"key\": \"ORDER_PAYMENT\", \"description\": \"주문·결제\" },\n" +
                                    "  { \"key\": \"SERVICE\", \"description\": \"서비스\" },\n" +
                                    "  { \"key\": \"ACCOUNT\", \"description\": \"계정\" }\n" +
                                    "]")))
    })
    ResponseEntity<List<InquiryCategoryResponse>> getInquiryCategories();
}

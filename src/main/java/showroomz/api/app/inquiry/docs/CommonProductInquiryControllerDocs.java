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

@Tag(name = "User - Inquiry Product", description = "상품 문의 관련 API")
public interface CommonProductInquiryControllerDocs {

    @Operation(
            summary = "상품 문의 타입 목록 조회",
            description = "상품 문의 등록 시 선택할 수 있는 문의 타입 목록을 조회합니다. \n\n" +
                    "**권한:** 인증 불필요 (비로그인 허용)\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InquiryCategoryResponse.class)),
                            examples = @ExampleObject(value = "[\n" +
                                    "  { \"key\": \"OPTION\", \"description\": \"옵션\" },\n" +
                                    "  { \"key\": \"INGREDIENT_USAGE\", \"description\": \"성분·사용법\" },\n" +
                                    "  { \"key\": \"RESTOCK\", \"description\": \"재입고\" },\n" +
                                    "  { \"key\": \"DELIVERY\", \"description\": \"배송\" },\n" +
                                    "  { \"key\": \"ETC\", \"description\": \"기타\" }\n" +
                                    "]")))
    })
    ResponseEntity<List<InquiryCategoryResponse>> getProductInquiryCategories();
}

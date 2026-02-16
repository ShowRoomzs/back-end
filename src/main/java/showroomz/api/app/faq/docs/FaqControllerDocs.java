package showroomz.api.app.faq.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.faq.dto.FaqCategoryItem;
import showroomz.api.app.faq.dto.FaqResponse;

import java.util.List;

@Tag(name = "Common - FAQ", description = "자주 묻는 질문(FAQ) 조회 API")
public interface FaqControllerDocs {

    @Operation(
            summary = "FAQ 목록 조회",
            description = "노출 여부가 true인 FAQ 목록을 조회합니다.\n\n" +
                    "**검색:**\n" +
                    "- `keyword`: 질문 내용 부분 일치 (대소문자 무시)\n" +
                    "- `category`: 고정 카테고리 (전체, 배송, 취소/교환/반품, 상품/AS문의, 주문/결제, 서비스, 이용 안내, 회원 정보). 전체 또는 생략 시 전체 조회.\n\n" +
                    "**권한:** 없음\n" +
                    "**요청 헤더:** 없음",
            parameters = {
                    @Parameter(name = "keyword", description = "질문 검색 키워드 (부분 일치, 선택)", required = false, example = "배송", in = ParameterIn.QUERY),
                    @Parameter(name = "category", description = "카테고리 enum (ALL, DELIVERY 등)", required = false, example = "DELIVERY", in = ParameterIn.QUERY)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FaqResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시 (실제 응답)",
                                            value = "[\n" +
                                                    "  {\n" +
                                                    "    \"id\": 1,\n" +
                                                    "    \"category\": \"DELIVERY\",\n" +
                                                    "    \"categoryName\": \"배송\",\n" +
                                                    "    \"question\": \"배송은 얼마나 걸리나요?\",\n" +
                                                    "    \"answer\": \"평균 2~3일 소요됩니다.\"\n" +
                                                    "  },\n" +
                                                    "  {\n" +
                                                    "    \"id\": 2,\n" +
                                                    "    \"category\": \"ORDER_PAYMENT\",\n" +
                                                    "    \"categoryName\": \"주문/결제\",\n" +
                                                    "    \"question\": \"결제 방법은 무엇이 있나요?\",\n" +
                                                    "    \"answer\": \"카드, 계좌이체, 간편결제를 이용하실 수 있습니다.\"\n" +
                                                    "  }\n" +
                                                    "]"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<List<FaqResponse>> getFaqList(String keyword, String category);

    @Operation(
            summary = "FAQ 카테고리 목록 조회",
            description = "고정 FAQ 카테고리 목록을 key(enum 이름), description(한글 표시명) 형식으로 반환합니다.\n\n" +
                    "**권한:** 없음\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FaqCategoryItem.class),
                            examples = @ExampleObject(
                                    name = "성공 예시 (실제 응답)",
                                    value = "[\n" +
                                            "  { \"key\": \"ALL\", \"description\": \"전체\" },\n" +
                                            "  { \"key\": \"DELIVERY\", \"description\": \"배송\" },\n" +
                                            "  { \"key\": \"CANCEL_EXCHANGE_REFUND\", \"description\": \"취소/교환/반품\" },\n" +
                                            "  { \"key\": \"PRODUCT_AS\", \"description\": \"상품/AS문의\" },\n" +
                                            "  { \"key\": \"ORDER_PAYMENT\", \"description\": \"주문/결제\" },\n" +
                                            "  { \"key\": \"SERVICE\", \"description\": \"서비스\" },\n" +
                                            "  { \"key\": \"USAGE_GUIDE\", \"description\": \"이용 안내\" },\n" +
                                            "  { \"key\": \"MEMBER_INFO\", \"description\": \"회원 정보\" }\n" +
                                            "]"
                            )
                    )
            )
    })
    ResponseEntity<List<FaqCategoryItem>> getFaqCategories();
}


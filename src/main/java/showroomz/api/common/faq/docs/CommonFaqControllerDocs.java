package showroomz.api.common.faq.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.faq.dto.FaqCategoryItem;

import java.util.List;

@Tag(name = "Common - FAQ", description = "자주 묻는 질문(FAQ) 공용 조회 API")
public interface CommonFaqControllerDocs {

    @Operation(
            summary = "FAQ 카테고리 목록 조회",
            description = "고정 FAQ 카테고리 목록을 key(enum 이름), description(한글 표시명) 형식으로 반환합니다.\n\n" +
                    "전체(ALL) + 5종이며 배열 순서가 어드민 탭 · 소비자 칩 노출 순서입니다. 저장용 카테고리에는 ALL을 쓸 수 없습니다.\n\n" +
                    "1:1 문의 유형(`GET /v1/common/inquiries/categories`)과 **같은 enum을 공유**하므로 두 목록의 코드·표시명은 항상 일치합니다.\n\n" +
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
                                            "  { \"key\": \"CANCEL_EXCHANGE_RETURN\", \"description\": \"취소/교환/반품\" },\n" +
                                            "  { \"key\": \"ORDER_PAYMENT\", \"description\": \"주문·결제\" },\n" +
                                            "  { \"key\": \"SERVICE\", \"description\": \"서비스\" },\n" +
                                            "  { \"key\": \"ACCOUNT\", \"description\": \"계정\" }\n" +
                                            "]"
                            )
                    )
            )
    })
    ResponseEntity<List<FaqCategoryItem>> getFaqCategories();
}

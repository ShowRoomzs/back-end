package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.faq.dto.FaqResponse;

import java.util.List;

@Tag(name = "Common - FAQ", description = "자주 묻는 질문(FAQ) 조회 API")
public interface FaqControllerDocs {

    @Operation(
            summary = "FAQ 목록 조회",
            description = "노출 여부가 true인 FAQ 전체 목록을 조회합니다.\n\n" +
                    "**권한:** 없음\n" +
                    "**요청 헤더:** 없음"
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
                                            name = "성공 예시",
                                            value = "[\n" +
                                                    "  {\n" +
                                                    "    \"id\": 1,\n" +
                                                    "    \"category\": \"배송 지연\",\n" +
                                                    "    \"question\": \"배송은 얼마나 걸리나요?\",\n" +
                                                    "    \"answer\": \"평균 2~3일 소요됩니다.\"\n" +
                                                    "  }\n" +
                                                    "]"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<List<FaqResponse>> getFaqList();

    @Operation(
            summary = "FAQ 카테고리 목록 조회",
            description = "노출 여부가 true인 FAQ에 사용된 카테고리 목록을 중복 없이 가나다순으로 조회합니다.\n\n" +
                    "**권한:** 없음\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공 예시",
                                    value = "[\n  \"배송 지연\",\n  \"교환/반품\",\n  \"주문/결제\"\n]"
                            )
                    )
            )
    })
    ResponseEntity<List<String>> getFaqCategories();
}


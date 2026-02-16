package showroomz.api.app.search.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.search.dto.AutoCompleteResponse;

@Tag(
        name = "User - Search",
        description = "검색 관련 API."
)
public interface SearchControllerDocs {

    @Operation(
            summary = "검색어 자동완성",
            description =
                    "입력한 키워드로 **상품**, **마켓**, **쇼룸**을 각각 검색하여 자동완성 후보를 반환합니다.\n\n" +

                            "**동작 방식**\n" +
                            "- **상품**: 이름에 키워드가 포함되고, 전시 중인 상품만 조회. 이름 길이 짧은 순 최대 5건\n" +
                            "- **마켓**: 마켓명에 키워드가 포함된 마켓. 이름 길이 짧은 순 최대 3건\n" +
                            "- **쇼룸**: 쇼룸명에 키워드가 포함된 쇼룸. 이름 길이 짧은 순 최대 3건\n\n" +

                            "**쿼리 파라미터**\n" +
                            "- `keyword`: 검색 키워드 (선택). 없거나 공백만 있으면 빈 배열로 응답\n\n" +

                            "**권한:** 인증 불필요 (비로그인 가능)\n\n" +

                            "**응답 구조**\n" +
                            "- `products`: 상품 목록 (id: 상품 ID, name: 상품명)\n" +
                            "- `markets`: 마켓 목록 (id: 마켓 ID, name: 마켓명)\n" +
                            "- `showrooms`: 쇼룸 목록 (id: 쇼룸 ID, name: 쇼룸명)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 키워드가 없거나 공백이면 products/markets/showrooms 모두 빈 배열",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AutoCompleteResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "키워드 있음 - 결과 있음",
                                            value = "{\n" +
                                                    "  \"products\": [\n" +
                                                    "    { \"id\": 1, \"name\": \"화이트 린넨 셔츠\" },\n" +
                                                    "    { \"id\": 2, \"name\": \"화이트 데님 팬츠\" }\n" +
                                                    "  ],\n" +
                                                    "  \"markets\": [\n" +
                                                    "    { \"id\": 10, \"name\": \"화이트 브랜드\" }\n" +
                                                    "  ],\n" +
                                                    "  \"showrooms\": []\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "키워드 없음/공백 - 빈 결과",
                                            value = "{\n" +
                                                    "  \"products\": [],\n" +
                                                    "  \"markets\": [],\n" +
                                                    "  \"showrooms\": []\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AutoCompleteResponse> getAutocomplete(
            @Parameter(
                    name = "keyword",
                    description = "검색 키워드. 없거나 공백만 있으면 빈 배열로 응답",
                    example = "화이트"
            )
            String keyword
    );
}

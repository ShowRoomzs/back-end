package showroomz.api.app.search.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.search.dto.AutoCompleteResponse;
import showroomz.api.app.search.dto.ShowroomSearchItem;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(
        name = "User - Search",
        description = "검색 관련 API."
)
public interface SearchControllerDocs {

    @Operation(
            summary = "쇼룸 검색 (C14)",
            description =
                    "쇼룸을 **이름**과 **아이디(@handle)** 로 검색합니다. 상품 검색·카테고리 탐색은 이 API의 범위가 아닙니다.\n\n" +

                            "**동작 방식**\n" +
                            "- 쇼룸명 **부분 일치** 또는 쇼룸 아이디 **부분 일치** (대소문자 무시)\n" +
                            "- 아이디는 `@`를 붙여 입력해도(`@brai`) 동일하게 검색됩니다\n" +
                            "- 노출 대상: 등록을 마쳐 쇼룸명·아이디가 확정되고 계정이 정상인 쇼룸\n" +
                            "- 정렬: 이름 앞부분 일치 → 이름 부분 일치 → 아이디 앞부분 일치 → 아이디 부분 일치, " +
                            "같은 등급에서는 쇼룸명이 짧은 순\n\n" +

                            "**쿼리 파라미터**\n" +
                            "- `keyword`: 검색어 (선택). 없거나 공백만 있으면 빈 결과\n" +
                            "- `page`: 페이지 번호 (1부터 시작, 기본값 1)\n" +
                            "- `size`: 페이지당 항목 수 (기본값 20)\n\n" +

                            "**권한:** 인증 불필요 (비로그인 가능)\n\n" +

                            "**화면 연결**\n" +
                            "- `pageInfo.totalResults` = 상단의 \"검색 결과 N\"\n" +
                            "- 일치 구간 하이라이트는 클라이언트가 `keyword`로 계산합니다 (서버는 원문만 반환)\n" +
                            "- 행 전체가 C4 쇼룸 진입이므로 팔로우 상태·팔로워 수·한 줄 소개는 내려주지 않습니다\n" +
                            "- `totalResults`가 0이면 결과 없음 화면이며, 하단 추천 목록은 " +
                            "`GET /v1/user/search/showrooms/active`로 채웁니다"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 검색어가 없거나 공백이면 content는 빈 배열",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "결과 있음 - '브라이'",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"showroomId\": 5,\n" +
                                                    "      \"showroomName\": \"브라이튼 룸\",\n" +
                                                    "      \"showroomAddress\": \"brighten_room\",\n" +
                                                    "      \"showroomImageUrl\": \"https://cdn.showroomz.co.kr/showroom/profile/a.jpg\",\n" +
                                                    "      \"hasOngoingGroupBuy\": true\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"showroomId\": 9,\n" +
                                                    "      \"showroomName\": \"맑은살림\",\n" +
                                                    "      \"showroomAddress\": \"brai_beauty\",\n" +
                                                    "      \"showroomImageUrl\": null,\n" +
                                                    "      \"hasOngoingGroupBuy\": false\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 2,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "결과 없음 - '브라이언트홈'",
                                            value = "{\n" +
                                                    "  \"content\": [],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 0,\n" +
                                                    "    \"totalResults\": 0,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<ShowroomSearchItem>> searchShowrooms(
            @Parameter(
                    name = "keyword",
                    description = "검색어 — 쇼룸 이름 또는 아이디(@handle). 없거나 공백만 있으면 빈 결과",
                    example = "브라이"
            )
            String keyword,
            PagingRequest pagingRequest
    );

    @Operation(
            summary = "활동 중인 쇼룸 (C14 결과 없음 화면)",
            description =
                    "검색 결과가 없을 때 아래에 이어 붙이는 \"이런 쇼룸은 어떠세요\" 목록입니다.\n\n" +

                            "**동작 방식**\n" +
                            "- 최근에 게시물을 올린 쇼룸 순 (C2 팔로잉 기본 정렬과 같은 기준)\n" +
                            "- 게시물이 아직 없는 쇼룸은 신규 등록순으로 뒤를 채웁니다\n" +
                            "- 랭킹 개념이 아니므로 순위 값은 내려주지 않습니다\n\n" +

                            "**쿼리 파라미터**\n" +
                            "- `size`: 개수 (선택, 기본값 10)\n\n" +

                            "**권한:** 인증 불필요 (비로그인 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ShowroomSearchItem.class)),
                            examples = @ExampleObject(
                                    value = "[\n" +
                                            "  {\n" +
                                            "    \"showroomId\": 12,\n" +
                                            "    \"showroomName\": \"제니의 뷰티룸\",\n" +
                                            "    \"showroomAddress\": \"jenny_beautyroom\",\n" +
                                            "    \"showroomImageUrl\": \"https://cdn.showroomz.co.kr/showroom/profile/b.jpg\",\n" +
                                            "    \"hasOngoingGroupBuy\": true\n" +
                                            "  },\n" +
                                            "  {\n" +
                                            "    \"showroomId\": 18,\n" +
                                            "    \"showroomName\": \"하루 코스메틱\",\n" +
                                            "    \"showroomAddress\": \"haru_cosmetic\",\n" +
                                            "    \"showroomImageUrl\": null,\n" +
                                            "    \"hasOngoingGroupBuy\": true\n" +
                                            "  }\n" +
                                            "]"
                            )
                    )
            )
    })
    ResponseEntity<List<ShowroomSearchItem>> getActiveShowrooms(
            @Parameter(name = "size", description = "가져올 쇼룸 수 (기본값 10)", example = "10")
            Integer size
    );

    @Operation(
            summary = "검색어 자동완성",
            description =
                    "입력한 키워드로 **상품**, **마켓**, **쇼룸**을 각각 검색하여 자동완성 후보를 반환합니다.\n\n" +

                            "**동작 방식**\n" +
                            "- **상품**: 이름에 키워드가 포함되고, 전시 중인 상품만 조회. 이름 길이 짧은 순 최대 5건\n" +
                            "- **마켓**: 마켓명에 키워드가 포함되고, 승인(APPROVED)된 판매자의 마켓만 조회. 이름 길이 짧은 순 최대 3건\n" +
                            "- **쇼룸**: 쇼룸명 또는 아이디(@handle)에 키워드가 포함되고, 등록을 마쳐 쇼룸명·아이디가 확정되고 " +
                            "계정이 정상인 쇼룸만 조회. 이름 길이 짧은 순 최대 3건\n\n" +

                            "**쿼리 파라미터**\n" +
                            "- `keyword`: 검색 키워드 (선택). 없거나 공백만 있으면 빈 배열로 응답\n\n" +

                            "**권한:** 인증 불필요 (비로그인 가능)\n\n" +

                            "**응답 구조**\n" +
                            "- `products`: 상품 목록 (id: 상품 ID, name: 상품명)\n" +
                            "- `markets`: 마켓 목록 (id: 마켓 ID, name: 마켓명)\n" +
                            "- `showrooms`: 쇼룸 목록 (id: 쇼룸(크리에이터) ID, name: 쇼룸명)\n\n" +

                            "**참고:** C14 쇼룸 검색 화면은 이 API가 아니라 `GET /v1/user/search/showrooms`를 사용합니다."
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

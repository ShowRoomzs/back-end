package showroomz.api.app.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Market", description = "사용자 마켓 API")
public interface UserMarketControllerDocs {

    @Operation(
            summary = "마켓 목록 조회",
            description = "승인된 판매자의 마켓 목록을 조회합니다. (비로그인 상태에서도 조회 가능)\n\n" +
                    "**조회 조건:**\n" +
                    "- 승인된(APPROVED) 판매자의 마켓만 조회\n" +
                    "- 카테고리 필터링 (선택)\n" +
                    "- 마켓명 키워드 검색 (선택)\n\n" +

                    "**권한:** 인증 불필요 (비로그인 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 1,\n" +
                                                    "      \"marketName\": \"쇼룸즈\",\n" +
                                                    "      \"marketImageUrl\": \"https://s3.amazonaws.com/bucket/market-image.jpg\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 2,\n" +
                                                    "      \"marketName\": \"트렌디샵\",\n" +
                                                    "      \"marketImageUrl\": \"https://s3.amazonaws.com/bucket/market-image2.jpg\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"page\": 1,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"totalElements\": 150,\n" +
                                                    "    \"totalPages\": 8,\n" +
                                                    "    \"first\": true,\n" +
                                                    "    \"last\": false\n" +
                                                    "  }\n" +
                                                    "}",
                                            description = "마켓 목록과 페이징 정보"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<MarketListResponse>> getMarkets(
            @Parameter(name = "mainCategory", description = "카테고리 필터 (선택)", required = false, example = "패션/의류", in = ParameterIn.QUERY)
            String mainCategory,
            @Parameter(name = "keyword", description = "마켓명 검색 키워드 (선택)", required = false, example = "쇼룸즈", in = ParameterIn.QUERY)
            String keyword,
            PagingRequest pagingRequest
    );

    @Operation(
            summary = "마켓 상세 조회",
            description = "마켓 정보와 팔로워 수를 조회합니다. (비로그인 상태에서도 조회 가능)\n\n" +
                    "**응답 필드:**\n" +
                    "- `marketId`: 마켓 ID\n" +
                    "- `marketName`: 마켓명\n" +
                    "- `marketImageUrl`: 마켓 이미지 URL\n" +
                    "- `marketDescription`: 마켓 한줄 소개\n" +
                    "- `marketUrl`: 마켓 URL\n" +
                    "- `mainCategory`: 대표 카테고리\n" +
                    "- `csNumber`: 고객센터 번호\n" +
                    "- `snsLink1`: SNS 링크 1\n" +
                    "- `snsLink2`: SNS 링크 2\n" +
                    "- `snsLink3`: SNS 링크 3\n" +
                    "- `followerCount`: 이 마켓을 찜한 유저 수\n" +
                    "- `isFollowed`: 현재 유저가 찜했는지 여부 (비로그인 시 false)\n\n" +
                    "**권한:** 인증 불필요 (비로그인 가능)",
            parameters = {
                    @Parameter(name = "marketId", description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MarketDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시 (로그인 상태, 팔로우 중)",
                                            value = "{\n" +
                                                    "  \"marketId\": 1,\n" +
                                                    "  \"marketName\": \"쇼룸즈\",\n" +
                                                    "  \"marketImageUrl\": \"https://s3.amazonaws.com/bucket/market-image.jpg\",\n" +
                                                    "  \"marketDescription\": \"트렌디한 라이프스타일을 제안하는 마켓입니다.\",\n" +
                                                    "  \"marketUrl\": \"https://www.showroomz.co.kr/shop/showroomz\",\n" +
                                                    "  \"mainCategory\": \"패션/의류\",\n" +
                                                    "  \"csNumber\": \"1588-0000\",\n" +
                                                    "  \"snsLink1\": \"https://instagram.com/showroomz\",\n" +
                                                    "  \"snsLink2\": \"https://facebook.com/showroomz\",\n" +
                                                    "  \"snsLink3\": \"https://twitter.com/showroomz\",\n" +
                                                    "  \"followerCount\": 150,\n" +
                                                    "  \"isFollowed\": true\n" +
                                                    "}",
                                            description = "로그인한 사용자가 해당 마켓을 팔로우한 경우"
                                    ),
                                    @ExampleObject(
                                            name = "성공 시 (비로그인 상태)",
                                            value = "{\n" +
                                                    "  \"marketId\": 1,\n" +
                                                    "  \"marketName\": \"쇼룸즈\",\n" +
                                                    "  \"marketImageUrl\": \"https://s3.amazonaws.com/bucket/market-image.jpg\",\n" +
                                                    "  \"marketDescription\": \"트렌디한 라이프스타일을 제안하는 마켓입니다.\",\n" +
                                                    "  \"marketUrl\": \"https://www.showroomz.co.kr/shop/showroomz\",\n" +
                                                    "  \"mainCategory\": \"패션/의류\",\n" +
                                                    "  \"csNumber\": \"1588-0000\",\n" +
                                                    "  \"snsLink1\": \"https://instagram.com/showroomz\",\n" +
                                                    "  \"snsLink2\": \"https://facebook.com/showroomz\",\n" +
                                                    "  \"snsLink3\": null,\n" +
                                                    "  \"followerCount\": 150,\n" +
                                                    "  \"isFollowed\": false\n" +
                                                    "}",
                                            description = "비로그인 상태에서는 isFollowed가 항상 false"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"MARKET_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 마켓입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<MarketDetailResponse> getMarketDetail(
            @Parameter(description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            Long marketId
    );
}

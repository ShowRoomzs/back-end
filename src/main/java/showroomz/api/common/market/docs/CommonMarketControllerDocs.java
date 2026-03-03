package showroomz.api.common.market.docs;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.common.market.dto.MarketRecommendationResponse;
import showroomz.api.common.market.dto.PopularProductResponse;

@Tag(name = "Common - Market", description = "공용 마켓 API")
public interface CommonMarketControllerDocs {

    @Operation(
            summary = "비회원/회원 추천 쇼룸 조회",
            description = "추천 마켓(쇼룸) 목록을 페이징 조회합니다.\n\n" +
                    "**추천 로직:**\n" +
                    "- isRecommended=true인 상품이 있는 마켓 우선 정렬\n" +
                    "- 승인된(APPROVED) 판매자의 마켓만 조회\n\n" +
                    "**응답 구조:**\n" +
                    "- content: 추천 마켓 목록 (representativeProducts: productId + imageUrl, 이미지 클릭 시 상세 페이지 이동용)\n" +
                    "- pageInfo: 페이징 메타데이터 (currentPage, totalPages, totalResults, limit, hasNext)\n\n" +
                    "**파라미터:**\n" +
                    "- categoryId: 대표 카테고리 ID 필터 (선택)\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- limit: 페이지당 항목 수 (기본값: 20)\n\n" +
                    "**권한:** 비회원/USER (Authorization 헤더 선택)\n" +
                    "**isFollowing:** 로그인 시 본인 팔로우 여부 반영, 비회원은 false\n\n" +
                    "**representativeProducts:** 기획안 4-2 - 상품 이미지 클릭 시 상세 페이지 이동을 위해 productId 포함"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MarketRecommendationResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 1,\n" +
                                                    "      \"marketName\": \"M 브라이튼\",\n" +
                                                    "      \"sellerId\": 1,\n" +
                                                    "      \"marketImageUrl\": \"https://example.com/market.jpg\",\n" +
                                                    "      \"representativeProducts\": [\n" +
                                                    "        { \"productId\": 1024, \"imageUrl\": \"https://example.com/product1.jpg\" },\n" +
                                                    "        { \"productId\": 1025, \"imageUrl\": \"https://example.com/product2.jpg\" },\n" +
                                                    "        { \"productId\": 1026, \"imageUrl\": \"https://example.com/product3.jpg\" }\n" +
                                                    "      ],\n" +
                                                    "      \"marketDescription\": \"브랜드 소개\",\n" +
                                                    "      \"marketUrl\": \"https://example.com\",\n" +
                                                    "      \"shopType\": \"SHOWROOM\",\n" +
                                                    "      \"followCount\": 1200,\n" +
                                                    "      \"isFollowing\": false,\n" +
                                                    "      \"mainCategoryId\": 1\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 5,\n" +
                                                    "    \"totalResults\": 80,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<MarketRecommendationResponse> getRecommendedMarkets(
            @Parameter(description = "대표 카테고리 ID 필터 (선택)", example = "1")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "페이지 번호 (1부터 시작, 기본값: 1)", example = "1")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "페이지당 항목 수 (기본값: 20)", example = "20")
            @RequestParam(value = "limit", required = false) Integer limit
    );

    @Operation(
            summary = "비회원/회원 특정 쇼룸의 인기 상품 Top10 조회",
            description = "특정 마켓(쇼룸)의 인기 상품 상위 10개를 조회합니다.\n\n" +
                    "**정렬 기준:**\n" +
                    "1. Wishlist 수 많은 순 (DESC)\n" +
                    "2. 최신 등록일(createdAt) 순 (DESC)\n\n" +
                    "**필터링:** 해당 마켓에 속한 상품 중 전시 중(isDisplay=true)인 상품만 대상\n\n" +
                    "**응답 구조:**\n" +
                    "- content: 상품 리스트 (id, name, marketName, representativeImageUrl, price, wishCount, isWished, reviewCount, tags 등)\n" +
                    "- pageInfo: 고정값 (currentPage=1, totalPages=1, totalResults, limit=10, hasNext=false)\n\n" +
                    "**권한:** 비회원/회원 공통 (로그인 시 isWished 반영, 비회원은 false)\n\n" +
                    "**파라미터:** marketId (경로) - 조회할 마켓(쇼룸) ID (필수)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PopularProductResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PopularProductResponse> getPopularProducts(
            @Parameter(name = "marketId", description = "조회할 마켓(쇼룸) ID - 해당 마켓의 인기 상품 Top 10 반환", example = "1", required = true, in = ParameterIn.PATH)
            @PathVariable("marketId") Long marketId
    );
}

package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.product.DTO.ProductDto;

@Tag(name = "Common - Product", description = "공용 상품 API")
public interface UserProductControllerDocs {

    @Operation(
            summary = "비회원/회원 상품 목록 조회",
            description = "검색 조건에 따라 모든 상품 목록을 조회합니다.\n\n" +
                    "**검색 조건:**\n" +
                    "- q: 검색어 (상품명, 마켓명 등)\n" +
                    "- categoryId: 카테고리 ID (하위 카테고리 포함)\n" +
                    "- marketId: 쇼룸 ID\n" +
                    "- gender: 성별 (MALE, FEMALE, UNISEX)\n" +
                    "- color: 색상\n" +
                    "- minPrice/maxPrice: 가격 범위\n" +
                    "- sort: 정렬 기준 (RECOMMEND, POPULAR, NEWEST, PRICE_ASC, PRICE_DESC)\n\n" +
                    "**정렬 옵션:**\n" +
                    "- RECOMMEND: 추천순 (isRecommended DESC, createdAt DESC)\n" +
                    "- POPULAR: 인기순 (현재: 최신순, 추후 좋아요 수 기준)\n" +
                    "- NEWEST: 최신순 (createdAt DESC)\n" +
                    "- PRICE_ASC: 가격 낮은순 (salePrice ASC)\n" +
                    "- PRICE_DESC: 가격 높은순 (salePrice DESC)\n\n" +
                    "**페이징:**\n" +
                    "- page: 페이지 번호 (기본값: 1)\n" +
                    "- limit: 페이지당 항목 수 (기본값: 20)\n\n" +
                    "**참고사항:**\n" +
                    "- 진열된 상품(isDisplay = true)만 조회됩니다.\n" +
                    "- Authorization 헤더가 없어도 조회 가능합니다 (게스트 검색).\n" +
                    "- 로그인한 사용자의 경우 isWished 정보가 포함됩니다.\n\n" +
                    "**권한:** 선택사항 (게스트 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.ProductSearchResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"products\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 1024,\n" +
                                                    "      \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"representativeImageUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "      \"marketId\": 5,\n" +
                                                    "      \"marketName\": \"M 브라이튼\",\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"regularPrice\": 113000,\n" +
                                                    "        \"discountRate\": 70,\n" +
                                                    "        \"salePrice\": 33900,\n" +
                                                    "        \"maxBenefitPrice\": 31000\n" +
                                                    "      },\n" +
                                                    "      \"status\": {\n" +
                                                    "        \"isOutOfStock\": false,\n" +
                                                    "        \"isOutOfStockForced\": false\n" +
                                                    "      },\n" +
                                                    "      \"likeCount\": 1200,\n" +
                                                    "      \"reviewCount\": 850,\n" +
                                                    "      \"isWished\": true\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"pageSize\": 20,\n" +
                                                    "    \"totalElements\": 1540,\n" +
                                                    "    \"totalPages\": 77,\n" +
                                                    "    \"isLast\": false,\n" +
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
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ProductDto.ProductSearchResponse> searchProducts(
            @Parameter(description = "Authorization 헤더 (Optional)", required = false)
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "검색 조건", required = false)
            @RequestParam(required = false) String q,
            @Parameter(description = "카테고리 ID", required = false)
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "쇼룸 ID", required = false)
            @RequestParam(required = false) Long marketId,
            @Parameter(description = "성별 (MALE, FEMALE, UNISEX)", required = false)
            @RequestParam(required = false) String gender,
            @Parameter(description = "색상", required = false)
            @RequestParam(required = false) String color,
            @Parameter(description = "최소 가격", required = false)
            @RequestParam(required = false) Integer minPrice,
            @Parameter(description = "최대 가격", required = false)
            @RequestParam(required = false) Integer maxPrice,
            @Parameter(description = "정렬 기준 (RECOMMEND, POPULAR, NEWEST, PRICE_ASC, PRICE_DESC)", required = false)
            @RequestParam(required = false) String sort,
            @Parameter(description = "페이지 번호 (기본값: 1)", required = false, example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지당 항목 수 (기본값: 20)", required = false, example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer limit
    );
}

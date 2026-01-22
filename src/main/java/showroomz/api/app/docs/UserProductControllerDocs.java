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
                    "- filters: 동적 필터 목록 (JSON 배열 문자열, 정렬 조건은 key: 'sort'로 포함하여 전달)\n\n" +
                    "**정렬 옵션:**\n" +
                    "- RECOMMEND: 추천순 (isRecommended DESC, createdAt DESC)\n" +
                    "- POPULAR: 인기순 (현재: createdAt DESC, 추후 좋아요 수 기준)\n" +
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
                                                    "      \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "      \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"sellerProductCode\": \"PROD-001\",\n" +
                                                    "      \"representativeImageUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "      \"thumbnailUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "      \"categoryId\": 1,\n" +
                                                    "      \"categoryName\": \"의류\",\n" +
                                                    "      \"marketId\": 5,\n" +
                                                    "      \"marketName\": \"M 브라이튼\",\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"regularPrice\": 113000,\n" +
                                                    "        \"discountRate\": 70,\n" +
                                                    "        \"salePrice\": 33900,\n" +
                                                    "        \"maxBenefitPrice\": 31000\n" +
                                                    "      },\n" +
                                                    "      \"purchasePrice\": 30000,\n" +
                                                    "      \"gender\": \"UNISEX\",\n" +
                                                    "      \"isDisplay\": true,\n" +
                                                    "      \"isRecommended\": false,\n" +
                                                    "      \"productNotice\": \"{\\\"origin\\\":\\\"한국\\\"}\",\n" +
                                                    "      \"description\": \"<p>상품 상세 설명</p>\",\n" +
                                                    "      \"tags\": \"[\\\"신상\\\", \\\"할인\\\"]\",\n" +
                                                    "      \"deliveryType\": \"STANDARD\",\n" +
                                                    "      \"deliveryFee\": 3000,\n" +
                                                    "      \"deliveryFreeThreshold\": 50000,\n" +
                                                    "      \"deliveryEstimatedDays\": 3,\n" +
                                                    "      \"createdAt\": \"2025-12-28T14:30:00Z\",\n" +
                                                    "      \"status\": {\n" +
                                                    "        \"isOutOfStock\": false,\n" +
                                                    "        \"isOutOfStockForced\": false\n" +
                                                    "      },\n" +
                                                    "      \"likeCount\": 1200,\n" +
                                                    "      \"reviewCount\": 850,\n" +
                                                    "      \"isWished\": false\n" +
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
            @Parameter(
                    description = "필터 목록 (JSON 배열 문자열). 정렬 조건은 key: 'sort'로 포함하여 전달합니다.\n" +
                            "- sort=RECOMMEND: 추천순 (isRecommended DESC, createdAt DESC)\n" +
                            "- sort=POPULAR: 인기순 (현재: createdAt DESC, 추후 좋아요 수 기준)\n" +
                            "- sort=NEWEST: 최신순 (createdAt DESC)\n" +
                            "- sort=PRICE_ASC: 가격 낮은순 (salePrice ASC)\n" +
                            "- sort=PRICE_DESC: 가격 높은순 (salePrice DESC)\n" +
                            "예: [{\"key\":\"gender\",\"values\":[\"MALE\"]},{\"key\":\"sort\",\"values\":[\"RECOMMEND\"]}]",
                    required = false
            )
            @RequestParam(required = false) String filters,
            @Parameter(description = "페이지 번호 (기본값: 1)", required = false, example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지당 항목 수 (기본값: 20)", required = false, example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer limit
    );
}

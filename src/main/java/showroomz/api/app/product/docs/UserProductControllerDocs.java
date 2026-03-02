package showroomz.api.app.product.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.global.dto.PageResponse;

import java.util.List;

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
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
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
                                                    "      \"discountRate\": 70,\n" +
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
                                                    "      \"wishCount\": 300,\n" +
                                                    "      \"reviewCount\": 850,\n" +
                                                    "      \"isWished\": false\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 77,\n" +
                                                    "    \"totalResults\": 1540,\n" +
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
    ResponseEntity<PageResponse<ProductDto.ProductItem>> searchProducts(
            @Parameter(description = "Authorization 헤더 (Optional)", required = false, hidden = true)
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

    @Operation(
            summary = "비회원/회원 상품 상세 조회",
            description = "상품 ID로 상세 정보를 조회합니다.\n\n" +
                    "**참고사항:**\n" +
                    "- 대표 이미지: 상품 이미지 중 order == 0\n" +
                    "- 커버 이미지: 상품 이미지 중 order >= 1\n" +
                    "- 무료배송 여부는 deliveryFreeThreshold 기준으로 계산됩니다.\n" +
                    "- 로그인한 사용자는 isWished, isFollowing 정보가 포함됩니다.\n\n" +
                    "**권한:** 선택사항 (게스트 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.ProductDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"id\": 1024,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"marketId\": 5,\n" +
                                                    "  \"marketName\": \"M 브라이튼\",\n" +
                                                    "  \"categoryId\": 1,\n" +
                                                    "  \"categoryName\": \"의류\",\n" +
                                                    "  \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "  \"sellerProductCode\": \"PROD-001\",\n" +
                                                    "  \"representativeImageUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "  \"coverImageUrls\": [\n" +
                                                    "    \"https://example.com/image1.jpg\",\n" +
                                                    "    \"https://example.com/image2.jpg\"\n" +
                                                    "  ],\n" +
                                                    "  \"description\": \"<p>상품 상세 설명</p>\",\n" +
                                                    "  \"productNotice\": {\"origin\":\"한국\"},\n" +
                                                    "  \"tags\": [\"신상\", \"할인\"],\n" +
                                                    "  \"gender\": \"UNISEX\",\n" +
                                                    "  \"isRecommended\": false,\n" +
                                                    "  \"regularPrice\": 113000,\n" +
                                                    "  \"salePrice\": 33900,\n" +
                                                    "  \"deliveryType\": \"STANDARD\",\n" +
                                                    "  \"deliveryFee\": 3000,\n" +
                                                    "  \"deliveryFreeThreshold\": 50000,\n" +
                                                    "  \"deliveryEstimatedDays\": 3,\n" +
                                                    "  \"isFreeDelivery\": false,\n" +
                                                    "  \"optionGroups\": [\n" +
                                                    "    {\n" +
                                                    "      \"optionGroupId\": 1,\n" +
                                                    "      \"name\": \"사이즈\",\n" +
                                                    "      \"options\": [\n" +
                                                    "        {\"optionId\": 1, \"name\": \"S\", \"price\": 0},\n" +
                                                    "        {\"optionId\": 2, \"name\": \"M\", \"price\": 0}\n" +
                                                    "      ]\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"variants\": [\n" +
                                                    "    {\n" +
                                                    "      \"variantId\": 1,\n" +
                                                    "      \"name\": \"S\",\n" +
                                                    "      \"regularPrice\": 113000,\n" +
                                                    "      \"salePrice\": 33900,\n" +
                                                    "      \"stock\": 10,\n" +
                                                    "      \"isRepresentative\": true,\n" +
                                                    "      \"isDisplay\": true,\n" +
                                                    "      \"optionIds\": [1]\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"isWished\": false,\n" +
                                                    "  \"isFollowing\": false,\n" +
                                                    "  \"createdAt\": \"2025-12-28T14:30:00Z\",\n" +
                                                    "  \"reviewInfo\": {\n" +
                                                    "    \"totalCount\": 42,\n" +
                                                    "    \"averageRating\": 4.5,\n" +
                                                    "    \"reviews\": [\n" +
                                                    "      {\n" +
                                                    "        \"reviewId\": 1,\n" +
                                                    "        \"userName\": \"쇼핑러버\",\n" +
                                                    "        \"rating\": 5,\n" +
                                                    "        \"content\": \"너무 만족합니다!\",\n" +
                                                    "        \"imageUrls\": [\"https://example.com/review1.jpg\"],\n" +
                                                    "        \"createdAt\": \"1일 전\"\n" +
                                                    "      }\n" +
                                                    "    ]\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ProductDto.ProductDetailResponse> getProductDetail(
            @Parameter(name = "productId", description = "상품 ID", required = true, example = "1")
            @PathVariable("productId") Long productId
    );

    @Operation(
            summary = "비회원/회원 옵션별 실시간 상품 재고 다중 조회",
            description = "상품 ID와 옵션(Variant) ID 목록으로 재고 및 가격 정보를 한 번에 조회합니다.\n\n" +
                    "**쿼리 파라미터:**\n" +
                    "- variantIds: 조회할 옵션 ID 목록 (예: variantIds=1&variantIds=2&variantIds=3)\n\n" +
                    "**참고사항:**\n" +
                    "- 비회원도 조회 가능합니다.\n" +
                    "- IN 절로 1회 쿼리하여 N+1을 방지합니다.\n" +
                    "- 재고 수량, 품절 여부(isOutOfStock), 강제 품절 여부(isOutOfStockForced)를 포함합니다.\n\n" +
                    "**권한:** 선택사항 (게스트 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.VariantStockListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "다중 조회 성공 예시",
                                            value = "{\n" +
                                                    "  \"variants\": [\n" +
                                                    "    {\n" +
                                                    "      \"productId\": 1024,\n" +
                                                    "      \"variantId\": 1,\n" +
                                                    "      \"stock\": 10,\n" +
                                                    "      \"isOutOfStock\": false,\n" +
                                                    "      \"isOutOfStockForced\": false,\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"regularPrice\": 113000,\n" +
                                                    "        \"discountRate\": 70,\n" +
                                                    "        \"salePrice\": 33900,\n" +
                                                    "        \"maxBenefitPrice\": 33900\n" +
                                                    "      }\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"productId\": 1024,\n" +
                                                    "      \"variantId\": 2,\n" +
                                                    "      \"stock\": 0,\n" +
                                                    "      \"isOutOfStock\": true,\n" +
                                                    "      \"isOutOfStockForced\": false,\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"regularPrice\": 113000,\n" +
                                                    "        \"discountRate\": 70,\n" +
                                                    "        \"salePrice\": 33900,\n" +
                                                    "        \"maxBenefitPrice\": 33900\n" +
                                                    "      }\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "variantIds 누락 또는 잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ProductDto.VariantStockListResponse> getVariantStocks(
            @Parameter(name = "productId", description = "상품 ID", required = true, example = "1024")
            @PathVariable("productId") Long productId,
            @Parameter(description = "조회할 옵션(Variant) ID 목록 (여러 개 테스트: 1, 2, 3)", required = true, example = "1")
            @RequestParam List<Long> variantIds
    );

    @Operation(
            summary = "비회원/회원 연관 상품 조회",
            description = "특정 상품과 연관된 상품 목록을 조회합니다.\n\n" +
                    "**추천 기준:**\n" +
                    "- 1순위: 동일 카테고리 상품\n" +
                    "- 2순위: 동일 성별 상품\n\n" +
                    "**정렬:**\n" +
                    "- isRecommended DESC, createdAt DESC\n\n" +
                    "**참고사항:**\n" +
                    "- 조회 대상 상품은 결과에서 제외됩니다.\n" +
                    "- Authorization 헤더가 없어도 조회 가능합니다 (게스트 조회).\n" +
                    "- 로그인한 사용자의 경우 isWished 정보가 포함됩니다.\n\n" +
                    "**권한:** 선택사항 (게스트 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageResponse<ProductDto.ProductItem>> getRelatedProducts(
            @Parameter(name = "productId", description = "상품 ID", required = true, example = "1")
            @PathVariable("productId") Long productId,
            @Parameter(description = "Authorization 헤더 (Optional)", required = false, hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "페이지 번호 (기본값: 1)", required = false, example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지당 항목 수 (기본값: 20)", required = false, example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer limit
    );
}

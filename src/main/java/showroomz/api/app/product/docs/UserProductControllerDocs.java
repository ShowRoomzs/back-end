package showroomz.api.app.product.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

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
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- size: 페이지당 항목 수 (기본값: 20)\n\n" +
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
"      \"gender\": \"UNISEX\",\n" +
"      \"isDisplay\": true,\n" +
"      \"isRecommended\": false,\n" +
"      \"productNotice\": \"{\\\"origin\\\":\\\"한국\\\"}\",\n" +
"      \"description\": \"<p>상품 상세 설명</p>\",\n" +
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
                                                    "    \"size\": 20,\n" +
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
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "카테고리 ID", required = false)
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @Parameter(description = "쇼룸 ID", required = false)
            @RequestParam(name = "marketId", required = false) Long marketId,
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
            @RequestParam(name = "filters", required = false) String filters,
            @ParameterObject PagingRequest pagingRequest
    );

    @Operation(
            summary = "비회원/회원 상품 상세 조회",
            description = "상품 ID로 C7 상품 상세 화면에 필요한 정보를 조회합니다.\n\n" +
                    "**게시 조건 (모두 충족해야 조회됨):**\n" +
                    "- 진열중(displayStatus: DISPLAY)인 상품만 조회됩니다.\n" +
                    "- 공구에 연결된 상품(groupBuyStatus: PREPARING, READY, IN_PROGRESS)만 조회됩니다.\n" +
                    "- 미진열 또는 공구 연결이 없는 상품(NOT_CONNECTED)은 404 PRODUCT_NOT_FOUND로 응답합니다.\n\n" +
                    "**화면 구성과 응답 필드:**\n" +
                    "- 갤러리: representativeImageUrl(첫 장, 이미지 order == 0) + coverImageUrls(order >= 1)\n" +
                    "- 브랜드 줄: marketName + brandSiteUrl (brandSiteUrl이 null이면 [브랜드 사이트] 버튼을 숨깁니다)\n" +
                    "- 가격: regularPrice(취소선) + discountRate(%) + salePrice(공구가)\n" +
                    "- 배송 블록: delivery (발송 예정일 · 배송비 · 무료배송 기준 · 도서산간 추가비 · 반품비 · 교환비)\n" +
                    "- 상세정보 탭: description + productNotice\n" +
                    "- 판매자 정보 탭: productNotice(고시) + delivery(배송/교환/반품) + sellerInfo(사업자 정보)\n" +
                    "- 옵션 시트: optionGroups + variants (variants[].isOutOfStock으로 옵션 단위 품절 표시)\n" +
                    "- 하단 CTA: status (상품 전체 판매 상태)\n\n" +
                    "**판매 상태 (status):**\n" +
                    "- 재고는 옵션마다 소진되므로, 남은 옵션이 하나도 없을 때 비로소 status.isOutOfStock = true가 됩니다.\n" +
                    "- status.isOutOfStockForced는 재고가 남아 있어도 브랜드가 강제로 내려둔 경우이며, 이때도 isOutOfStock = true입니다.\n" +
                    "- 품절이면 하단 CTA를 [구매하기] 대신 판매 종료 상태로 그립니다. 개별 옵션의 품절은 variants[].isOutOfStock을 보세요.\n" +
                    "- \"공구 마감\" 상태는 아직 표현되지 않습니다 — 공구 연결이 풀린 상품은 상세 자체가 404이며, " +
                    "마감된 공구를 화면에 남기는 처리는 공구 도메인이 붙은 뒤 정해집니다.\n\n" +
                    "**할인율:**\n" +
                    "- discountRate는 서버가 정가·판매가로 계산해 반올림한 값입니다. 클라이언트가 다시 계산하지 마세요 " +
                    "(반올림 방식이 갈리면 같은 상품에 34%와 35%가 동시에 보입니다).\n" +
                    "- 할인이 없거나 판매가가 정가보다 높은 데이터면 0입니다.\n\n" +
                    "**이 응답에 포함되지 않는 것:**\n" +
                    "- 상품 문의(문의 탭): `GET /v1/common/products/{productId}/inquiries`가 담당합니다.\n" +
                    "- 옵션별 실시간 재고: 시트를 연 뒤에는 `GET /v1/common/products/{productId}/variants`로 갱신합니다.\n" +
                    "- 찜(♥): 게시물 단위 기능이라 상품 상세에는 두지 않습니다.\n" +
                    "- **\"이 공구에서 함께 판매 중\"(공구 추천 상품): 공구 연결 기능이 아직 구현되지 않아 추후 구현 예정입니다.** " +
                    "같은 공구에 묶인 상품을 내려주는 값이므로, 공구 도메인이 붙은 뒤 이 응답 또는 별도 API로 추가됩니다.\n" +
                    "- 연관 상품 / 추천 상품 API는 폐기되었습니다.\n\n" +
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
                                                    "  \"name\": \"시카 리페어 앰플 30ml 리필 2개 세트\",\n" +
                                                    "  \"representativeImageUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "  \"coverImageUrls\": [\n" +
                                                    "    \"https://example.com/image1.jpg\",\n" +
                                                    "    \"https://example.com/image2.jpg\"\n" +
                                                    "  ],\n" +
                                                    "  \"marketId\": 5,\n" +
                                                    "  \"marketName\": \"라보에이치\",\n" +
                                                    "  \"brandSiteUrl\": \"https://labo-h.example.com\",\n" +
                                                    "  \"regularPrice\": 38000,\n" +
                                                    "  \"discountRate\": 34,\n" +
                                                    "  \"salePrice\": 24900,\n" +
                                                    "  \"groupBuyStatus\": \"IN_PROGRESS\",\n" +
                                                    "  \"status\": {\n" +
                                                    "    \"isOutOfStock\": false,\n" +
                                                    "    \"isOutOfStockForced\": false\n" +
                                                    "  },\n" +
                                                    "  \"delivery\": {\n" +
                                                    "    \"shippingLeadDays\": 2,\n" +
                                                    "    \"deliveryFee\": 3000,\n" +
                                                    "    \"freeShippingThreshold\": 30000,\n" +
                                                    "    \"remoteAreaSurcharge\": 5000,\n" +
                                                    "    \"returnFee\": 3000,\n" +
                                                    "    \"exchangeFee\": 6000\n" +
                                                    "  },\n" +
                                                    "  \"description\": \"<p>상품 상세 설명</p>\",\n" +
                                                    "  \"productNotice\": {\"용량 또는 중량\":\"30ml (리필 30ml × 2)\",\"제조국\":\"대한민국\"},\n" +
                                                    "  \"optionGroups\": [\n" +
                                                    "    {\n" +
                                                    "      \"optionGroupId\": 1,\n" +
                                                    "      \"name\": \"구성\",\n" +
                                                    "      \"options\": [\n" +
                                                    "        {\"optionId\": 1, \"name\": \"단품 30ml\", \"price\": 0},\n" +
                                                    "        {\"optionId\": 2, \"name\": \"30ml + 리필 2개\", \"price\": 25000}\n" +
                                                    "      ]\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"variants\": [\n" +
                                                    "    {\n" +
                                                    "      \"variantId\": 1,\n" +
                                                    "      \"name\": \"단품 30ml\",\n" +
                                                    "      \"regularPrice\": 38000,\n" +
                                                    "      \"salePrice\": 24900,\n" +
                                                    "      \"stock\": 10,\n" +
                                                    "      \"isOutOfStock\": false,\n" +
                                                    "      \"isRepresentative\": true,\n" +
                                                    "      \"optionIds\": [1]\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"sellerInfo\": {\n" +
                                                    "    \"companyName\": \"주식회사 라보에이치\",\n" +
                                                    "    \"representativeName\": \"홍길동\",\n" +
                                                    "    \"businessRegistrationNumber\": \"000-00-00000\",\n" +
                                                    "    \"mailOrderRegNumber\": \"제0000-서울강남-00000호\",\n" +
                                                    "    \"businessAddress\": \"서울특별시 강남구 ○○로 00 4층\",\n" +
                                                    "    \"csNumber\": \"000-0000-0000\",\n" +
                                                    "    \"email\": \"brand@example.com\"\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품을 찾을 수 없거나 공구에 연결되지 않은 상품",
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
                    "- 진열중이면서 공구에 연결된 상품만 조회됩니다 (그 외 404 PRODUCT_NOT_FOUND).\n" +
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
                    description = "상품을 찾을 수 없거나 공구에 연결되지 않은 상품",
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
            @RequestParam(name = "variantIds") List<Long> variantIds
    );

}

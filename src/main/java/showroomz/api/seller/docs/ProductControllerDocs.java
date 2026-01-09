package showroomz.api.seller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.seller.product.DTO.ProductDto;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Seller - Product", description = "Seller Product API")
public interface ProductControllerDocs {

    @Operation(
            summary = "상품 등록",
            description = "백스테이지 관리자가 새로운 상품을 등록합니다. 카테고리, 가격, 옵션 조합(재고/판매가), 이미지, 상세 설명 및 공시 정보를 모두 포함합니다.\n\n" +
                    "**필수 항목:**\n" +
                    "- categoryId: 카테고리 ID (예: 1, 2, 3)\n" +
                    "- name: 상품명\n" +
                    "- regularPrice: 판매가 (할인 전)\n" +
                    "- salePrice: 할인 판매가 (최종가)\n" +
                    "- variants: 옵션 목록 (조합된 결과)\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "상품 등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.CreateProductResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"message\": \"상품이 성공적으로 등록되었습니다.\"\n" +
                                                    "}",
                                            description = "상품이 성공적으로 등록되었습니다."
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "입력값 오류 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"name\",\n" +
                                                    "      \"reason\": \"상품명은 필수 입력값입니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "카테고리 또는 브랜드를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "카테고리 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"CATEGORY_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 카테고리입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "상품 등록 정보",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ProductDto.CreateProductRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"categoryId\": 1,\n" +
                                            "  \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                            "  \"sellerProductCode\": \"PROD-001\",\n" +
                                            "  \"purchasePrice\": 30000,\n" +
                                            "  \"regularPrice\": 59000,\n" +
                                            "  \"salePrice\": 49000,\n" +
                                            "  \"isDiscount\": true,\n" +
                                            "  \"representativeImageUrl\": \"https://example.com/image.jpg\",\n" +
                                            "  \"coverImageUrls\": [\n" +
                                            "    \"https://example.com/image1.jpg\",\n" +
                                            "    \"https://example.com/image2.jpg\"\n" +
                                            "  ],\n" +
                                            "  \"description\": \"<p>상품 상세 설명</p>\",\n" +
                                            "  \"tags\": [\"신상\", \"할인\", \"인기\"],\n" +
                                            "  \"deliveryType\": \"STANDARD\",\n" +
                                            "  \"deliveryFee\": 3000,\n" +
                                            "  \"deliveryFreeThreshold\": 50000,\n" +
                                            "  \"deliveryEstimatedDays\": 3,\n" +
                                            "  \"productNotice\": {\n" +
                                            "    \"origin\": \"제품 상세 참고\",\n" +
                                            "    \"material\": \"제품 상세 참고\",\n" +
                                            "    \"color\": \"제품 상세 참고\",\n" +
                                            "    \"size\": \"제품 상세 참고\",\n" +
                                            "    \"manufacturer\": \"제품 상세 참고\",\n" +
                                            "    \"washingMethod\": \"제품 상세 참고\",\n" +
                                            "    \"manufactureDate\": \"제품 상세 참고\",\n" +
                                            "    \"asInfo\": \"제품 상세 참고\",\n" +
                                            "    \"qualityAssurance\": \"제품 상세 참고\"\n" +
                                            "  },\n" +
                                            "  \"optionGroups\": [\n" +
                                            "    {\n" +
                                            "      \"name\": \"사이즈\",\n" +
                                            "      \"options\": [\"Free\"]\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"variants\": [\n" +
                                            "    {\n" +
                                            "      \"optionNames\": [\"Free\"],\n" +
                                            "      \"salePrice\": 49000,\n" +
                                            "      \"stock\": 999,\n" +
                                            "      \"isDisplay\": true,\n" +
                                            "      \"isRepresentative\": true\n" +
                                            "    }\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<ProductDto.CreateProductResponse> createProduct(
            @RequestBody ProductDto.CreateProductRequest request
    );

    @Operation(
            summary = "상품 개별 조회",
            description = "백스테이지에서 판매자가 특정 상품의 상세 정보를 조회합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 개별 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.ProductListItem.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"sellerProductCode\": \"PROD-ABC-001\",\n" +
                                                    "  \"thumbnailUrl\": \"https://example.com/thumbnail.jpg\",\n" +
                                                    "  \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "  \"price\": {\n" +
                                                    "    \"purchasePrice\": 25000,\n" +
                                                    "    \"regularPrice\": 59000,\n" +
                                                    "    \"salePrice\": 49000\n" +
                                                    "  },\n" +
                                                    "  \"createdAt\": \"2025-12-28T14:30:00Z\",\n" +
                                                    "  \"displayStatus\": \"DISPLAY\",\n" +
                                                    "  \"stockStatus\": \"IN_STOCK\",\n" +
                                                    "  \"isOutOfStockForced\": false\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 또는 본인의 상품이 아님",
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
    ResponseEntity<ProductDto.ProductListItem> getProductById(
            @Parameter(
                    description = "조회할 상품 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long productId
    );

    @Operation(
            summary = "상품 목록 조회 (페이징, 필터링, 검색)",
            description = "백스테이지에서 판매자가 자신의 상품 목록을 조회합니다. 페이징, 카테고리, 진열상태, 품절상태 필터 및 검색 기능을 지원합니다.\n\n" +
                    "**필터 파라미터:**\n" +
                    "- categoryId: 최종 선택된 카테고리 ID (선택사항)\n" +
                    "- displayStatus: 진열 상태 (ALL, DISPLAY, HIDDEN) - 기본값: ALL\n" +
                    "- stockStatus: 품절 상태 (ALL, OUT_OF_STOCK, IN_STOCK) - 기본값: ALL\n\n" +
                    "**검색 파라미터:**\n" +
                    "- keyword: 검색어 (선택사항)\n" +
                    "- keywordType: 검색 타입 (productNumber: 상품 번호, sellerProductCode: 판매자 상품 코드, name: 상품명) - keywordType이 없으면 전체 검색\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작) - 기본값: 1\n" +
                    "- size: 페이지당 항목 수 - 기본값: 20\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"productId\": 1,\n" +
                                                    "      \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "      \"sellerProductCode\": \"PROD-ABC-001\",\n" +
                                                    "      \"thumbnailUrl\": \"https://example.com/thumbnail.jpg\",\n" +
                                                    "      \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"purchasePrice\": 25000,\n" +
                                                    "        \"regularPrice\": 59000,\n" +
                                                    "        \"salePrice\": 49000\n" +
                                                    "      },\n" +
                                                    "      \"createdAt\": \"2025-12-28T14:30:00Z\",\n" +
                                                    "      \"displayStatus\": \"DISPLAY\",\n" +
                                                    "      \"stockStatus\": \"IN_STOCK\",\n" +
                                                    "      \"isOutOfStockForced\": false\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 10,\n" +
                                                    "    \"totalResults\": 195,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageResponse<ProductDto.ProductListItem>> getProductList(
            @Parameter(description = "필터 조건 (카테고리, 진열상태, 품절상태)")
            ProductDto.ProductListRequest request,
            @Parameter(description = "페이징 정보 (페이지 번호, 페이지 크기)")
            PagingRequest pagingRequest
    );

    @Operation(
            summary = "상품 수정",
            description = "백스테이지 관리자가 기존 상품의 정보를 수정합니다. 수정하고 싶은 필드만 제공하면 해당 필드만 업데이트됩니다.\n\n" +
                    "**수정 가능한 항목:**\n" +
                    "- categoryId: 카테고리 ID\n" +
                    "- name: 상품명\n" +
                    "- sellerProductCode: 판매자 상품 코드\n" +
                    "- isDisplay: 진열 상태\n" +
                    "- isOutOfStockForced: 강제 품절 처리 여부\n" +
                    "- purchasePrice: 매입가\n" +
                    "- regularPrice: 판매가 (할인 전)\n" +
                    "- salePrice: 할인 판매가 (최종가)\n" +
                    "- representativeImageUrl: 대표 이미지 URL\n" +
                    "- coverImageUrls: 커버 이미지 URL 목록\n" +
                    "- description: 상세 설명\n" +
                    "- tags: 태그 목록\n" +
                    "- deliveryType: 배송 유형\n" +
                    "- deliveryFee: 배송비\n" +
                    "- deliveryFreeThreshold: 무료 배송 최소 금액\n" +
                    "- deliveryEstimatedDays: 배송 예상 일수\n" +
                    "- productNotice: 상품정보제공고시\n" +
                    "- optionGroups: 옵션 그룹 목록 (variants와 함께 제공해야 함)\n" +
                    "- variants: 옵션 목록 (optionGroups와 함께 제공해야 함)\n\n" +
                    "**주의사항:**\n" +
                    "- optionGroups와 variants는 함께 제공해야 합니다.\n" +
                    "- 이미지를 수정하는 경우, representativeImageUrl 또는 coverImageUrls를 제공하면 기존 이미지가 모두 삭제되고 새로운 이미지로 교체됩니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.UpdateProductResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"message\": \"상품이 성공적으로 수정되었습니다.\"\n" +
                                                    "}",
                                            description = "상품이 성공적으로 수정되었습니다."
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "입력값 오류 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"regularPrice\",\n" +
                                                    "      \"reason\": \"판매가는 0 이상이어야 합니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 또는 본인의 상품이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품 또는 카테고리를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "상품 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"PRODUCT_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 상품입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "상품 수정 정보 (수정하고 싶은 필드만 제공)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ProductDto.UpdateProductRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시 (일부 필드만 수정)",
                                    value = "{\n" +
                                            "  \"name\": \"수정된 상품명\",\n" +
                                            "  \"regularPrice\": 69000,\n" +
                                            "  \"salePrice\": 59000,\n" +
                                            "  \"isDisplay\": true\n" +
                                            "}",
                                    description = "일부 필드만 수정하는 예시"
                            ),
                            @ExampleObject(
                                    name = "요청 예시 (전체 필드 수정)",
                                    value = "{\n" +
                                            "  \"categoryId\": 1,\n" +
                                            "  \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                            "  \"sellerProductCode\": \"PROD-001\",\n" +
                                            "  \"purchasePrice\": 30000,\n" +
                                            "  \"regularPrice\": 59000,\n" +
                                            "  \"salePrice\": 49000,\n" +
                                            "  \"representativeImageUrl\": \"https://example.com/image.jpg\",\n" +
                                            "  \"coverImageUrls\": [\n" +
                                            "    \"https://example.com/image1.jpg\",\n" +
                                            "    \"https://example.com/image2.jpg\"\n" +
                                            "  ],\n" +
                                            "  \"description\": \"<p>상품 상세 설명</p>\",\n" +
                                            "  \"tags\": [\"신상\", \"할인\", \"인기\"],\n" +
                                            "  \"deliveryType\": \"STANDARD\",\n" +
                                            "  \"deliveryFee\": 3000,\n" +
                                            "  \"deliveryFreeThreshold\": 50000,\n" +
                                            "  \"deliveryEstimatedDays\": 3,\n" +
                                            "  \"productNotice\": {\n" +
                                            "    \"origin\": \"제품 상세 참고\",\n" +
                                            "    \"material\": \"제품 상세 참고\",\n" +
                                            "    \"color\": \"제품 상세 참고\",\n" +
                                            "    \"size\": \"제품 상세 참고\",\n" +
                                            "    \"manufacturer\": \"제품 상세 참고\",\n" +
                                            "    \"washingMethod\": \"제품 상세 참고\",\n" +
                                            "    \"manufactureDate\": \"제품 상세 참고\",\n" +
                                            "    \"asInfo\": \"제품 상세 참고\",\n" +
                                            "    \"qualityAssurance\": \"제품 상세 참고\"\n" +
                                            "  },\n" +
                                            "  \"optionGroups\": [\n" +
                                            "    {\n" +
                                            "      \"name\": \"사이즈\",\n" +
                                            "      \"options\": [\"Free\"]\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"variants\": [\n" +
                                            "    {\n" +
                                            "      \"optionNames\": [\"Free\"],\n" +
                                            "      \"salePrice\": 49000,\n" +
                                            "      \"stock\": 999,\n" +
                                            "      \"isDisplay\": true,\n" +
                                            "      \"isRepresentative\": true\n" +
                                            "    }\n" +
                                            "  ]\n" +
                                            "}",
                                    description = "전체 필드를 수정하는 예시"
                            )
                    }
            )
    )
    ResponseEntity<ProductDto.UpdateProductResponse> updateProduct(
            @Parameter(
                    description = "수정할 상품 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long productId,
            @RequestBody ProductDto.UpdateProductRequest request
    );

    @Operation(
            summary = "상품 삭제",
            description = "백스테이지 관리자가 자신의 상품을 삭제합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.DeleteProductResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"message\": \"상품이 성공적으로 삭제되었습니다.\"\n" +
                                                    "}",
                                            description = "상품이 성공적으로 삭제되었습니다."
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (본인의 상품이 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"FORBIDDEN\",\n" +
                                                    "  \"message\": \"권한이 없습니다.\"\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "상품 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"PRODUCT_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 상품입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ProductDto.DeleteProductResponse> deleteProduct(
            @Parameter(
                    description = "삭제할 상품 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long productId
    );
}


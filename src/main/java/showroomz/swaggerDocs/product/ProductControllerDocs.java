package showroomz.swaggerDocs.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.auth.DTO.ErrorResponse;
import showroomz.auth.DTO.ValidationErrorResponse;
import showroomz.product.DTO.ProductDto;

@Tag(name = "Seller", description = "Seller API")
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
                    "**권한:** ADMIN\n" +
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
}


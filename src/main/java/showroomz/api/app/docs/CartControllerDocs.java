package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.entity.UserPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.cart.dto.CartDto;

@Tag(name = "User - Cart", description = "장바구니 관리 API")
public interface CartControllerDocs {

    @Operation(
            summary = "장바구니 추가",
            description = "사용자의 장바구니에 옵션(Variant)과 수량을 추가합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "추가 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CartDto.AddCartResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "재고 부족 또는 잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "옵션을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CartDto.AddCartResponse> addCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CartDto.AddCartRequest request
    );

    @Operation(
            summary = "장바구니 조회",
            description = "사용자의 장바구니 목록을 페이징하여 조회합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CartDto.CartListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "조회 성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"cartId\": 10,\n" +
                                                    "      \"productId\": 1024,\n" +
                                                    "      \"variantId\": 1,\n" +
                                                    "      \"productName\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"thumbnailUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "      \"marketId\": 5,\n" +
                                                    "      \"marketName\": \"M 브라이튼\",\n" +
                                                    "      \"optionName\": \"색상: 블랙 / 사이즈: L\",\n" +
                                                    "      \"quantity\": 2,\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"regularPrice\": 59000,\n" +
                                                    "        \"discountRate\": 17,\n" +
                                                    "        \"salePrice\": 49000,\n" +
                                                    "        \"maxBenefitPrice\": 49000\n" +
                                                    "      },\n" +
                                                    "      \"deliveryFee\": 3000,\n" +
                                                    "      \"stock\": {\n" +
                                                    "        \"stock\": 10,\n" +
                                                    "        \"isOutOfStock\": false,\n" +
                                                    "        \"isOutOfStockForced\": false\n" +
                                                    "      }\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"regularTotal\": 200000,\n" +
                                                    "    \"saleTotal\": 150000,\n" +
                                                    "    \"discountTotal\": 50000,\n" +
                                                    "    \"deliveryFeeTotal\": 3000,\n" +
                                                    "    \"finalTotal\": 153000\n" +
                                                    "  },\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 5,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
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
            )
    })
    ResponseEntity<CartDto.CartListResponse> getCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    );

    @Operation(
            summary = "장바구니 수정",
            description = "장바구니 항목의 옵션 또는 수량을 수정합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CartDto.UpdateCartResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "재고 부족 또는 잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장바구니 항목 또는 옵션을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CartDto.UpdateCartResponse> updateCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long cartItemId,
            @RequestBody CartDto.UpdateCartRequest request
    );

    @Operation(
            summary = "장바구니 개별 삭제",
            description = "장바구니 항목을 삭제하고 요약 금액을 반환합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CartDto.DeleteCartResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"deletedCartItemId\": 2,\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"regularTotal\": 118000,\n" +
                                                    "    \"saleTotal\": 98000,\n" +
                                                    "    \"discountTotal\": 20000,\n" +
                                                    "    \"deliveryFeeTotal\": 0,\n" +
                                                    "    \"totalProductPrice\": 98000,\n" +
                                                    "    \"expectedTotalPrice\": 98000\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "삭제 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장바구니 항목을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CartDto.DeleteCartResponse> deleteCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long cartItemId
    );

    @Operation(
            summary = "장바구니 전체 삭제",
            description = "장바구니의 모든 항목을 삭제하고 요약 금액을 반환합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CartDto.ClearCartResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"장바구니가 비워졌습니다.\",\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"regularTotal\": 0,\n" +
                                                    "    \"saleTotal\": 0,\n" +
                                                    "    \"discountTotal\": 0,\n" +
                                                    "    \"deliveryFeeTotal\": 0,\n" +
                                                    "    \"totalProductPrice\": 0,\n" +
                                                    "    \"expectedTotalPrice\": 0\n" +
                                                    "  }\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "이미 비어 있음",
                                            value = "{\n" +
                                                    "  \"message\": \"이미 장바구니가 비어 있습니다\",\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"regularTotal\": 0,\n" +
                                                    "    \"saleTotal\": 0,\n" +
                                                    "    \"discountTotal\": 0,\n" +
                                                    "    \"deliveryFeeTotal\": 0,\n" +
                                                    "    \"totalProductPrice\": 0,\n" +
                                                    "    \"expectedTotalPrice\": 0\n" +
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
            )
    })
    ResponseEntity<CartDto.ClearCartResponse> clearCart(
            @AuthenticationPrincipal UserPrincipal principal
    );
}

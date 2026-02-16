package showroomz.api.app.cart.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.entity.UserPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.cart.dto.CartDto;

import java.util.List;

@Tag(name = "User - Cart", description = "장바구니 관리 API")
public interface CartControllerDocs {

    @Operation(
            summary = "장바구니 상품 추가",
            description = "사용자의 장바구니에 옵션(Variant)과 수량을 추가합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "다중 추가 요청 예시",
                                    value = "[\n" +
                                            "  {\"productId\": 1, \"variantId\": 10, \"quantity\": 2},\n" +
                                            "  {\"productId\": 2, \"variantId\": 11, \"quantity\": 1}\n" +
                                            "]"
                            )
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "추가 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CartDto.BulkAddCartResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "다중 추가 성공 예시",
                                            value = "{\n" +
                                                    "  \"addedCount\": 2,\n" +
                                                    "  \"message\": \"상품 2개가 장바구니에 추가되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
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
    ResponseEntity<CartDto.BulkAddCartResponse> addCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody List<CartDto.AddCartRequest> request
    );

    @Operation(
            summary = "장바구니 조회",
            description = "사용자의 장바구니 목록을 조회합니다. (페이징 미적용)\n\n" +
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
                                                    "  \"items\": [\n" +
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
            @AuthenticationPrincipal UserPrincipal principal
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
            summary = "장바구니 삭제 (개별/선택/전체 통합)",
            description = "하나의 API로 장바구니 삭제를 처리합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- **개별 삭제:** cartItemIds=10 → 해당 ID 1개만 삭제\n" +
                    "- **선택 삭제:** cartItemIds=10&cartItemIds=11&cartItemIds=12 → 지정한 ID들만 삭제\n" +
                    "- **전체 삭제:** cartItemIds 생략 또는 비어있음 → 현재 사용자의 장바구니 전체 삭제\n\n" +
                    "**보안:** 삭제 요청 시 해당 cartItemId가 현재 로그인한 유저의 소유인지 검증합니다. 타인의 장바구니 항목은 삭제할 수 없습니다.\n\n" +
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
                                            name = "선택 삭제 성공",
                                            value = "{\n" +
                                                    "  \"deletedCartItemIds\": [2, 3, 5],\n" +
                                                    "  \"deletedCount\": 3,\n" +
                                                    "  \"message\": \"3개 항목이 삭제되었습니다.\",\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"regularTotal\": 118000,\n" +
                                                    "    \"saleTotal\": 98000,\n" +
                                                    "    \"discountTotal\": 20000,\n" +
                                                    "    \"deliveryFeeTotal\": 0,\n" +
                                                    "    \"totalProductPrice\": 98000,\n" +
                                                    "    \"expectedTotalPrice\": 98000\n" +
                                                    "  }\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "전체 삭제 성공",
                                            value = "{\n" +
                                                    "  \"deletedCartItemIds\": [1, 2, 3],\n" +
                                                    "  \"deletedCount\": 3,\n" +
                                                    "  \"message\": \"3개 항목이 삭제되었습니다.\",\n" +
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
                                                    "  \"deletedCartItemIds\": [],\n" +
                                                    "  \"deletedCount\": 0,\n" +
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
                    responseCode = "403",
                    description = "삭제 권한 없음 (타인의 장바구니 항목 포함)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
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
    ResponseEntity<CartDto.DeleteCartResponse> deleteCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "cartItemIds", required = false) List<Long> cartItemIds
    );
}

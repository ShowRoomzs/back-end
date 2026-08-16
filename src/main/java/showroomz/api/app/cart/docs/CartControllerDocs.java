package showroomz.api.app.cart.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
                    "**담을 수 없는 상품:** 공구가 마감(연결 해제·미진열)되었거나 품절된 옵션은 담을 수 없고 " +
                    "400 `CART_ITEM_NOT_PURCHASABLE`을 반환합니다.\n\n" +
                    "**수량 상한:** 항목당 최대 99개까지입니다(기존 수량과 합산 기준). 초과하면 400입니다.\n\n" +
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
                    description = "재고 부족 · 마감/품절 상품 · 잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "옵션을 찾을 수 없거나 토큰의 사용자를 찾을 수 없음",
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
            description = "사용자의 장바구니를 **공구(쇼룸) 단위 그룹**으로 조회합니다. (페이징 미적용)\n\n" +
                    "**그룹:** 배송비·마감일·발송 시점이 공구마다 달라 상품이 아니라 공구별로 묶습니다. " +
                    "각 그룹은 그 공구의 배송비와 무료배송까지 남은 금액(`shipping.amountToFreeShipping`)을 함께 내려줍니다.\n\n" +
                    "**선택 합산:** `selectedCartItemIds`로 화면의 체크 상태를 넘기면 그 항목만으로 요약을 계산합니다. " +
                    "생략하면 **구매 가능한 항목 전체**가 선택된 것으로 봅니다(화면 진입 시 기본 상태).\n\n" +
                    "**담은 뒤 마감·품절:** 살 수 없게 된 항목도 목록에서 지우지 않고 `availability`로 사유를 알려줍니다. " +
                    "이 항목은 `selectedCartItemIds`에 담겨 있어도 선택에서 빠지며 합계·배송비 계산에 들어가지 않습니다.\n\n" +
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
                                                    "  \"groups\": [\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 5,\n" +
                                                    "      \"marketName\": \"제니의 뷰티룸\",\n" +
                                                    "      \"marketImageUrl\": \"https://example.com/market.jpg\",\n" +
                                                    "      \"isClosed\": false,\n" +
                                                    "      \"items\": [\n" +
                                                    "        {\n" +
                                                    "          \"cartId\": 11,\n" +
                                                    "          \"productId\": 1025,\n" +
                                                    "          \"variantId\": 4,\n" +
                                                    "          \"productName\": \"진정 토너 패드 60매 리필 기획 세트\",\n" +
                                                    "          \"thumbnailUrl\": \"https://example.com/image2.jpg\",\n" +
                                                    "          \"marketId\": 5,\n" +
                                                    "          \"marketName\": \"제니의 뷰티룸\",\n" +
                                                    "          \"optionName\": \"구성: 단품\",\n" +
                                                    "          \"quantity\": 1,\n" +
                                                    "          \"price\": {\n" +
                                                    "            \"regularPrice\": 26000,\n" +
                                                    "            \"discountRate\": 33,\n" +
                                                    "            \"salePrice\": 17500,\n" +
                                                    "            \"maxBenefitPrice\": 17500\n" +
                                                    "          },\n" +
                                                    "          \"deliveryFee\": 3000,\n" +
                                                    "          \"stock\": { \"stock\": 0, \"isOutOfStock\": true, \"isOutOfStockForced\": false },\n" +
                                                    "          \"availability\": {\n" +
                                                    "            \"isPurchasable\": false,\n" +
                                                    "            \"reason\": \"SOLD_OUT\",\n" +
                                                    "            \"label\": \"품절\",\n" +
                                                    "            \"message\": \"품절되어 주문할 수 없어요\"\n" +
                                                    "          },\n" +
                                                    "          \"isSelected\": false\n" +
                                                    "        },\n" +
                                                    "        {\n" +
                                                    "          \"cartId\": 10,\n" +
                                                    "          \"productId\": 1024,\n" +
                                                    "          \"variantId\": 1,\n" +
                                                    "          \"productName\": \"시카 리페어 앰플 30ml 리필 2개 세트\",\n" +
                                                    "          \"thumbnailUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "          \"marketId\": 5,\n" +
                                                    "          \"marketName\": \"제니의 뷰티룸\",\n" +
                                                    "          \"optionName\": \"구성: 30ml + 리필 2개\",\n" +
                                                    "          \"quantity\": 1,\n" +
                                                    "          \"price\": {\n" +
                                                    "            \"regularPrice\": 38000,\n" +
                                                    "            \"discountRate\": 34,\n" +
                                                    "            \"salePrice\": 24900,\n" +
                                                    "            \"maxBenefitPrice\": 24900\n" +
                                                    "          },\n" +
                                                    "          \"deliveryFee\": 3000,\n" +
                                                    "          \"stock\": { \"stock\": 10, \"isOutOfStock\": false, \"isOutOfStockForced\": false },\n" +
                                                    "          \"availability\": {\n" +
                                                    "            \"isPurchasable\": true,\n" +
                                                    "            \"reason\": null,\n" +
                                                    "            \"label\": null,\n" +
                                                    "            \"message\": null\n" +
                                                    "          },\n" +
                                                    "          \"isSelected\": true\n" +
                                                    "        }\n" +
                                                    "      ],\n" +
                                                    "      \"shipping\": {\n" +
                                                    "        \"deliveryFee\": 3000,\n" +
                                                    "        \"freeShippingThreshold\": 30000,\n" +
                                                    "        \"hasSelectedItems\": true,\n" +
                                                    "        \"selectedProductTotal\": 24900,\n" +
                                                    "        \"chargedDeliveryFee\": 3000,\n" +
                                                    "        \"isFreeShipping\": false,\n" +
                                                    "        \"amountToFreeShipping\": 5100\n" +
                                                    "      }\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"regularTotal\": 38000,\n" +
                                                    "    \"saleTotal\": 24900,\n" +
                                                    "    \"discountTotal\": 13100,\n" +
                                                    "    \"deliveryFeeTotal\": 3000,\n" +
                                                    "    \"finalTotal\": 27900,\n" +
                                                    "    \"selectedCount\": 1,\n" +
                                                    "    \"selectableCount\": 1,\n" +
                                                    "    \"totalCount\": 2\n" +
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
                    responseCode = "404",
                    description = "토큰의 사용자를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CartDto.CartListResponse> getCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(
                    description = "요약 계산에 포함할 장바구니 항목 ID 목록(화면의 체크 상태). "
                            + "생략하면 구매 가능한 항목 전체를 선택한 것으로 봅니다.",
                    example = "10,11"
            )
            @RequestParam(value = "selectedCartItemIds", required = false) List<Long> selectedCartItemIds
    );

    @Operation(
            summary = "장바구니 수정",
            description = "장바구니 항목의 옵션 또는 수량을 수정합니다.\n\n" +
                    "**담은 뒤 마감·품절된 항목은 수정할 수 없습니다**(400 `CART_ITEM_NOT_PURCHASABLE`) — " +
                    "화면도 그 행의 수량 스테퍼와 옵션 변경 버튼을 함께 비활성으로 그립니다. " +
                    "바꾸려는 옵션(`variantId`) 쪽이 마감·품절인 경우도 같은 코드로 거절합니다.\n\n" +
                    "**수량 상한:** 최종 수량은 최대 99개입니다(옵션을 합치는 경우 합산 후 기준). 초과하면 400입니다.\n\n" +
                    "**옵션 변경 시 병합:** 이미 담긴 다른 항목과 같은 옵션으로 바꾸면 두 항목이 하나로 합쳐지고 " +
                    "수량이 더해집니다(재고 초과 시 400 `INSUFFICIENT_STOCK`).\n\n" +
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
                    description = "재고 부족 · 마감/품절 상품 · 잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장바구니 항목 또는 옵션을 찾을 수 없거나 토큰의 사용자를 찾을 수 없음",
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "토큰의 사용자를 찾을 수 없음",
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

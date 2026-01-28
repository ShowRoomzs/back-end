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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.product.DTO.ProductDto;

@Tag(name = "User - Wishlist", description = "위시리스트 관리 API")
public interface WishlistControllerDocs {

    @Operation(
            summary = "위시리스트 상품 목록 조회",
            description = "로그인한 사용자가 찜한 상품 목록을 페이징하여 조회합니다.\n\n" +
                    "**정렬:** 최신 찜한 순(위시리스트 생성일 내림차순)\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작) - 기본값: 1\n" +
                    "- limit: 페이지당 항목 수 - 기본값: 20\n" +
                    "- categoryId: 카테고리 ID (선택, 해당 카테고리 상품만 조회)\n\n" +
                    "**응답:**\n" +
                    "- 모든 상품의 isWished 값은 true입니다.\n" +
                    "- ProductSearchResponse 구조를 재사용합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.ProductSearchResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"products\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 1,\n" +
                                                    "      \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "      \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"thumbnailUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"regularPrice\": 59000,\n" +
                                                    "        \"salePrice\": 49000,\n" +
                                                    "        \"discountRate\": 17\n" +
                                                    "      },\n" +
                                                    "      \"isWished\": true\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"pageSize\": 20,\n" +
                                                    "    \"totalElements\": 15,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"isLast\": true,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ProductDto.ProductSearchResponse> getWishlist(
            @AuthenticationPrincipal User principal,
            @Parameter(
                    description = "페이지 번호 (1부터 시작)",
                    example = "1"
            )
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(
                    description = "페이지당 항목 수",
                    example = "20"
            )
            @RequestParam(value = "limit", required = false) Integer limit,
            @Parameter(
                    description = "카테고리 ID (선택, 해당 카테고리 상품만 조회)",
                    example = "10"
            )
            @RequestParam(value = "categoryId", required = false) Long categoryId
    );

    @Operation(
            summary = "위시리스트 추가",
            description = "특정 상품을 위시리스트에 추가합니다.\n\n" +

                    "- 이미 위시리스트에 존재하는 상품을 다시 추가해도 성공(204 No Content)으로 처리됩니다.\n" +
                    "- 중복 추가 시도 시 예외를 발생시키지 않고 무시합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "추가 성공 - Status: 204 No Content"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (사용자 또는 상품을 찾을 수 없음) - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자를 찾을 수 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"사용자를 찾을 수 없습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "상품을 찾을 수 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"PRODUCT_NOT_FOUND\",\n" +
                                                    "  \"message\": \"상품을 찾을 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> addWishlist(
            @AuthenticationPrincipal User principal,
            @Parameter(
                    name = "productId",
                    description = "위시리스트에 추가할 상품 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long productId
    );

    @Operation(
            summary = "위시리스트 삭제",
            description = "특정 상품을 위시리스트에서 삭제합니다.\n\n" +

                    "- 위시리스트에 존재하지 않는 상품을 삭제해도 성공(204 No Content)으로 처리됩니다.\n" +
                    "- 존재하지 않는 항목 삭제 시도 시 예외를 발생시키지 않고 무시합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "삭제 성공 - Status: 204 No Content"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (사용자 또는 상품을 찾을 수 없음) - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자를 찾을 수 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"사용자를 찾을 수 없습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "상품을 찾을 수 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"PRODUCT_NOT_FOUND\",\n" +
                                                    "  \"message\": \"상품을 찾을 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> deleteWishlist(
            @AuthenticationPrincipal User principal,
            @Parameter(
                    name = "productId",
                    description = "위시리스트에서 삭제할 상품 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long productId
    );
}

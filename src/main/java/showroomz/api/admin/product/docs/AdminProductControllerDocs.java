package showroomz.api.admin.product.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.product.DTO.AdminProductDto;
import showroomz.api.admin.product.DTO.AdminProductSearchCondition;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.product.DTO.ProductDto;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Product", description = "관리자 상품 관리 API")
public interface AdminProductControllerDocs {

    @Operation(
            summary = "관리자 상품 목록 조회 (페이징, 필터링, 검색)",
            description = "관리자가 모든 브랜드의 상품 목록을 조회합니다. 셀러 상품 목록과 동일한 구성이며, 응답에 마켓명을 포함합니다.\n\n" +
                    "**응답:** 글로벌 `PageResponse`(`content` + `pageInfo`) + 진열 상태별 건수(`displayStatusCounts`)\n" +
                    "- `displayStatusCounts`: 검색어·공구상태 반영, 진열상태 필터 미반영\n\n" +
                    "**응답 필드:**\n" +
                    "- marketName: 마켓(브랜드)명\n" +
                    "- regularPrice: 판매가\n" +
                    "- createdAt: 등록일\n" +
                    "- modifiedAt: 수정일\n" +
                    "- stock: 재고 수량 (옵션 재고 합계)\n" +
                    "- groupBuyStatus: 공구 상태 (더미)\n" +
                    "  - PREPARING: 준비중\n" +
                    "  - READY: 준비완료\n" +
                    "  - IN_PROGRESS: 진행중\n" +
                    "  - NOT_CONNECTED: 연결없음\n\n" +
                    "**검색 조건 (Query):**\n" +
                    "- displayStatus: 진열 상태 (DISPLAY, HIDDEN, PENDING_REVIEW, HIDE_REQUEST, 미입력 시 전체)\n" +
                    "- groupBuyStatus: 공구 상태 (PREPARING, READY, IN_PROGRESS, NOT_CONNECTED, 미입력 시 전체)\n" +
                    "- keyword: 검색어 (상품명, 상품번호, 브랜드명)\n" +
                    "- sortType: 정렬 (CREATED_AT, MODIFIED_AT, STOCK_ASC, 미입력 시 CREATED_AT)\n" +
                    "- page / size: 페이징\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductDto.ProductListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"productId\": 1,\n" +
                                                    "      \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "      \"sellerProductCode\": \"PROD-ABC-001\",\n" +
                                                    "      \"marketName\": \"프리미엄 쇼핑몰\",\n" +
                                                    "      \"thumbnailUrl\": \"https://example.com/thumbnail.jpg\",\n" +
                                                    "      \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"regularPrice\": 59000,\n" +
                                                    "      \"createdAt\": \"2025-12-28T14:30:00Z\",\n" +
                                                    "      \"modifiedAt\": \"2026-01-05T10:00:00Z\",\n" +
                                                    "      \"displayStatus\": \"DISPLAY\",\n" +
                                                    "      \"groupBuyStatus\": \"PREPARING\",\n" +
                                                    "      \"stock\": 100\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 10,\n" +
                                                    "    \"totalResults\": 195,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  },\n" +
                                                    "  \"displayStatusCounts\": {\n" +
                                                    "    \"all\": 195,\n" +
                                                    "    \"display\": 120,\n" +
                                                    "    \"hidden\": 40,\n" +
                                                    "    \"pendingReview\": 20,\n" +
                                                    "    \"hideRequest\": 15\n" +
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
                    description = "권한 없음 (ADMIN 권한 필요)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminProductDto.ProductListResponse> getProductList(
            @ParameterObject @ModelAttribute AdminProductSearchCondition condition,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "관리자 상품 개별 조회",
            description = "관리자가 특정 상품의 상세 정보를 조회합니다. 브랜드 개별 상품 조회와 동일한 응답이며, 마켓 소유권 제한 없이 조회합니다.\n\n" +
                    "- `displayStatus=HIDDEN`인 경우 `latestHideInfo`에 가장 최근 미진열 사유·상세사유·일시·운영자명이 포함됩니다.\n" +
                    "- `groupBuyStatus`(더미): PREPARING(준비중), READY(준비완료), IN_PROGRESS(진행중), NOT_CONNECTED(연결없음)\n\n" +
                    "**processingHistory.historyType (상품 처리 이력):**\n" +
                    "- `PRODUCT_CREATED` → 상품 등록\n" +
                    "- `PRODUCT_INFO_UPDATED` → 브랜드가 상품 정보 수정\n" +
                    "- `STOCK_UPDATED` → 재고 수량 수정\n" +
                    "- `HIDDEN` → 미진열 처리\n" +
                    "- `REDISPLAYED` → 다시 진열\n" +
                    "- `HIDE_REQUESTED` → 미진열 요청\n" +
                    "- `PENDING_REVIEW` → 재검토 대기\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 개별 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.ProductDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"marketId\": 1,\n" +
                                                    "  \"marketName\": \"프리미엄 쇼핑몰\",\n" +
                                                    "  \"categoryId\": 1,\n" +
                                                    "  \"categoryName\": \"의류\",\n" +
                                                    "  \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "  \"sellerProductCode\": \"PROD-ABC-001\",\n" +
                                                    "  \"representativeImageUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "  \"coverImageUrls\": [\"https://example.com/image1.jpg\", \"https://example.com/image2.jpg\"],\n" +
                                                    "  \"regularPrice\": 59000,\n" +
                                                    "  \"displayStatus\": \"HIDDEN\",\n" +
                                                    "  \"groupBuyStatus\": \"PREPARING\",\n" +
                                                    "  \"latestHideInfo\": {\n" +
                                                    "    \"hideReasonType\": \"PRODUCT_NOTICE_ERROR\",\n" +
                                                    "    \"hideReasonDescription\": \"상품 정보 제공 고시 오류\",\n" +
                                                    "    \"hideDetail\": \"성분 표기 누락\",\n" +
                                                    "    \"hiddenAt\": \"2026-06-20T11:20:00Z\",\n" +
                                                    "    \"processorName\": \"admin@showroomz.com\"\n" +
                                                    "  },\n" +
                                                    "  \"isRecommended\": false,\n" +
                                                    "  \"productNotice\": \"{\\\"origin\\\":\\\"대한민국\\\",\\\"ingredients\\\":\\\"제품 상세 참고\\\"}\",\n" +
                                                    "  \"description\": \"<p>상품 상세 설명</p>\",\n" +
                                                    "  \"createdAt\": \"2025-12-28T14:30:00Z\",\n" +
                                                    "  \"optionGroups\": [\n" +
                                                    "    {\n" +
                                                    "      \"optionGroupId\": 1,\n" +
                                                    "      \"name\": \"사이즈\",\n" +
                                                    "      \"options\": [\n" +
                                                    "        {\n" +
                                                    "          \"optionId\": 1,\n" +
                                                    "          \"name\": \"S\",\n" +
                                                    "          \"price\": 0\n" +
                                                    "        },\n" +
                                                    "        {\n" +
                                                    "          \"optionId\": 2,\n" +
                                                    "          \"name\": \"M\",\n" +
                                                    "          \"price\": 0\n" +
                                                    "        }\n" +
                                                    "      ]\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"variants\": [\n" +
                                                    "    {\n" +
                                                    "      \"variantId\": 1,\n" +
                                                    "      \"name\": \"S / Black\",\n" +
                                                    "      \"regularPrice\": 50000,\n" +
                                                    "      \"stock\": 100,\n" +
                                                    "      \"isRepresentative\": true,\n" +
                                                    "      \"optionIds\": [1, 2]\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"processingHistory\": [\n" +
                                                    "    {\n" +
                                                    "      \"historyId\": 2,\n" +
                                                    "      \"historyType\": \"HIDDEN\",\n" +
                                                    "      \"title\": \"미진열 처리\",\n" +
                                                    "      \"previousDisplayStatus\": \"DISPLAY\",\n" +
                                                    "      \"newDisplayStatus\": \"HIDDEN\",\n" +
                                                    "      \"hideReason\": {\n" +
                                                    "        \"reasonType\": \"PRODUCT_NOTICE_ERROR\",\n" +
                                                    "        \"reasonDescription\": \"상품 정보 제공 고시 오류\",\n" +
                                                    "        \"detail\": \"성분 표기 누락\"\n" +
                                                    "      },\n" +
                                                    "      \"stockQuantity\": null,\n" +
                                                    "      \"processorName\": \"admin@showroomz.com\",\n" +
                                                    "      \"createdAt\": \"2026-06-20T11:20:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"historyId\": 1,\n" +
                                                    "      \"historyType\": \"PRODUCT_CREATED\",\n" +
                                                    "      \"title\": \"상품 등록\",\n" +
                                                    "      \"previousDisplayStatus\": null,\n" +
                                                    "      \"newDisplayStatus\": \"DISPLAY\",\n" +
                                                    "      \"hideReason\": null,\n" +
                                                    "      \"stockQuantity\": null,\n" +
                                                    "      \"processorName\": null,\n" +
                                                    "      \"createdAt\": \"2026-06-12T10:05:00Z\"\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (ADMIN 권한 필요)",
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
    ResponseEntity<ProductDto.ProductDetailResponse> getProductById(
            @Parameter(description = "조회할 상품 ID", required = true, example = "1")
            @PathVariable Long productId
    );

    @Operation(
            summary = "관리자 상품 추천 상태 변경",
            description = "관리자가 특정 상품의 추천 상태를 변경합니다.\n\n" +
                    "**기능:**\n" +
                    "- 상품의 isRecommended 필드를 요청된 값으로 변경\n" +
                    "- 추천 상태가 변경된 상품 정보와 성공 메시지를 반환\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "추천 상태 변경 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductDto.UpdateRecommendationResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"isRecommended\": true,\n" +
                                                    "  \"message\": \"상품 추천 상태가 성공적으로 변경되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class)
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
                    description = "권한 없음 (ADMIN 권한 필요)",
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
    ResponseEntity<AdminProductDto.UpdateRecommendationResponse> updateRecommendation(
            @Parameter(description = "추천 상태를 변경할 상품 ID", required = true, example = "1")
            @PathVariable Long productId,
            @RequestBody AdminProductDto.UpdateRecommendationRequest request
    );

    @Operation(
            summary = "관리자 상품 미진열 / 다시 진열 처리",
            description = "관리자가 상품을 미진열 처리하거나 다시 진열합니다.\n\n" +
                    "**미진열 처리 (HIDDEN):**\n" +
                    "- hideReasonType 필수\n" +
                    "- 사유: PRODUCT_NOTICE_ERROR(상품 정보 제공 고시 오류), AD_DISPLAY_VIOLATION(표시/광고 위반 의심), " +
                    "BRAND_REQUEST(브랜드 요청), OTHER(기타)\n" +
                    "- hideDetail(상세 사유)은 선택\n" +
                    "- 처리 이력에 미진열 사유+상세사유가 묶여 기록됨\n" +
                    "- 이미 미진열(HIDDEN) 상태면 이력 저장 없이 성공 응답만 반환\n\n" +
                    "**다시 진열 (DISPLAY):**\n" +
                    "- 미진열 사유 필드 초기\n" +
                    "- 처리 이력에 '다시 진열' 기록\n" +
                    "- 이미 진열(DISPLAY) 상태면 이력 저장 없이 성공 응답만 반환\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "진열 상태 변경 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductDto.UpdateDisplayStatusResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "미진열 처리",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"displayStatus\": \"HIDDEN\",\n" +
                                                    "  \"hideReasonType\": \"PRODUCT_NOTICE_ERROR\",\n" +
                                                    "  \"hideDetail\": \"성분 표기 누락\",\n" +
                                                    "  \"message\": \"상품이 미진열 처리되었습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "다시 진열",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"displayStatus\": \"DISPLAY\",\n" +
                                                    "  \"hideReasonType\": null,\n" +
                                                    "  \"hideDetail\": null,\n" +
                                                    "  \"message\": \"상품이 다시 진열되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminProductDto.UpdateDisplayStatusRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "미진열 처리",
                                    value = "{\n" +
                                            "  \"displayStatus\": \"HIDDEN\",\n" +
                                            "  \"hideReasonType\": \"PRODUCT_NOTICE_ERROR\",\n" +
                                            "  \"hideDetail\": \"성분 표기 누락\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "다시 진열",
                                    value = "{\n" +
                                            "  \"displayStatus\": \"DISPLAY\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<AdminProductDto.UpdateDisplayStatusResponse> updateDisplayStatus(
            @Parameter(description = "상품 ID", required = true, example = "1")
            @PathVariable Long productId,
            @RequestBody AdminProductDto.UpdateDisplayStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(
            summary = "관리자 상품 미진열 / 다시 진열 일괄 처리",
            description = "여러 상품을 일괄 미진열 처리하거나 다시 진열합니다.\n\n" +
                    "이미 동일 진열 상태인 상품은 이력 저장 없이 성공으로 포함됩니다.\n\n**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "일괄 처리 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductDto.BulkUpdateDisplayStatusResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "일괄 미진열",
                                            value = "{\n" +
                                                    "  \"productIds\": [1, 2, 3],\n" +
                                                    "  \"count\": 3,\n" +
                                                    "  \"displayStatus\": \"HIDDEN\",\n" +
                                                    "  \"message\": \"3개 상품이 미진열 처리되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminProductDto.BulkUpdateDisplayStatusResponse> bulkUpdateDisplayStatus(
            @RequestBody AdminProductDto.BulkUpdateDisplayStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );
}

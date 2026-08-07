package showroomz.api.admin.product.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.product.DTO.AdminProductDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.product.dto.ProductProcessingHistoryDto;

@Tag(name = "Admin - Product", description = "관리자 상품 관리 API")
public interface AdminProductControllerDocs {

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
                    "- 처리 이력에 미진열 사유+상세사유가 묶여 기록됨\n\n" +
                    "**다시 진열 (DISPLAY):**\n" +
                    "- 미진열 사유 필드 초기\n" +
                    "- 처리 이력에 '다시 진열' 기록\n\n" +
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
            description = "여러 상품을 일괄 미진열 처리하거나 다시 진열합니다.\n\n**권한:** ADMIN"
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

    @Operation(
            summary = "상품 처리 이력 조회",
            description = "상품별 처리 이력을 최신순으로 조회합니다. 브랜드와 어드민이 동일 이력을 공유합니다.\n\n" +
                    "- 미진열 처리 시 사유와 상세사유는 `hideReason` 객체로 묶여 응답됩니다.\n" +
                    "- 어드민 처리 이력에는 `processorName`(예: 김운영 운영자)이 포함됩니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductProcessingHistoryDto.HistoryListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "처리 이력 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"processingHistory\": [\n" +
                                                    "    {\n" +
                                                    "      \"historyId\": 4,\n" +
                                                    "      \"historyType\": \"STOCK_UPDATED\",\n" +
                                                    "      \"title\": \"재고 수량 수정\",\n" +
                                                    "      \"previousDisplayStatus\": \"PENDING_REVIEW\",\n" +
                                                    "      \"newDisplayStatus\": \"PENDING_REVIEW\",\n" +
                                                    "      \"hideReason\": null,\n" +
                                                    "      \"stockQuantity\": 120,\n" +
                                                    "      \"processorName\": null,\n" +
                                                    "      \"createdAt\": \"2026-07-28T16:42:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"historyId\": 3,\n" +
                                                    "      \"historyType\": \"REDISPLAYED\",\n" +
                                                    "      \"title\": \"다시 진열\",\n" +
                                                    "      \"previousDisplayStatus\": \"HIDDEN\",\n" +
                                                    "      \"newDisplayStatus\": \"DISPLAY\",\n" +
                                                    "      \"hideReason\": null,\n" +
                                                    "      \"stockQuantity\": null,\n" +
                                                    "      \"processorName\": \"김운영 운영자\",\n" +
                                                    "      \"createdAt\": \"2026-06-25T09:00:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"historyId\": 2,\n" +
                                                    "      \"historyType\": \"HIDDEN\",\n" +
                                                    "      \"title\": \"미진열 처리\",\n" +
                                                    "      \"previousDisplayStatus\": \"DISPLAY\",\n" +
                                                    "      \"newDisplayStatus\": \"HIDDEN\",\n" +
                                                    "      \"hideReason\": {\n" +
                                                    "        \"reasonType\": \"PRODUCT_NOTICE_ERROR\",\n" +
                                                    "        \"reasonDescription\": \"상품 정보 제공 고시 오류\",\n" +
                                                    "        \"detail\": null\n" +
                                                    "      },\n" +
                                                    "      \"stockQuantity\": null,\n" +
                                                    "      \"processorName\": \"김운영 운영자\",\n" +
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
            @ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ProductProcessingHistoryDto.HistoryListResponse> getProcessingHistory(
            @Parameter(description = "상품 ID", required = true, example = "1")
            @PathVariable Long productId
    );
}

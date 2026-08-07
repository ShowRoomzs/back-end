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

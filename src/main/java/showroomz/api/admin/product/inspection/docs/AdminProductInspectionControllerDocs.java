package showroomz.api.admin.product.inspection.docs;

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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.product.inspection.dto.AdminProductInspectionDto;
import showroomz.api.admin.product.inspection.dto.ProductInspectionSearchCondition;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Product - Inspection", description = "관리자 상품 검수 API")
public interface AdminProductInspectionControllerDocs {

    @Operation(
            summary = "[검수] 목록 조회",
            description = "관리자 전용 상품 검수 목록입니다. 미승인(WAITING 등) 상품도 포함됩니다.\n\n" +
                    "**QueryDSL 동적 필터:** 검수 상태, 등록일 구간(KST), 키워드(상품명·상품번호·판매자코드·마켓명), 마켓 ID\n\n" +
                    "**페이징:** `PagingRequest` (page, size)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 필요)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<AdminProductInspectionDto.ListItem>> list(
            @ParameterObject @ModelAttribute ProductInspectionSearchCondition condition,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "[검수] 상세 조회",
            description = "단건 상품 데이터, 마켓·판매자 요약, 검수 이력 타임라인을 반환합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductInspectionDto.InspectionDetailResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminProductInspectionDto.InspectionDetailResponse> detail(
            @Parameter(description = "상품 ID", required = true, example = "1") @PathVariable Long productId
    );

    @Operation(
            summary = "[검수] 상태 변경 (단건)",
            description = "승인·반려·보류·재신청 등 검수 상태를 변경하고 `product_inspection_history`에 이력을 남깁니다.\n\n" +
                    "**반려(REJECTED):** `rejectReasonType` 필수, 타입이 OTHER면 `rejectDetail` 필수\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "변경 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductInspectionDto.UpdateStatusResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "검수 상태 및 반려 사유·메모",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminProductInspectionDto.UpdateStatusRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "승인",
                                    value = "{\n  \"inspectionStatus\": \"APPROVED\",\n  \"adminMemo\": \"검수 완료\"\n}"
                            ),
                            @ExampleObject(
                                    name = "반려",
                                    value = "{\n  \"inspectionStatus\": \"REJECTED\",\n  \"rejectReasonType\": \"POLICY_VIOLATION\",\n  \"adminMemo\": \"정책 위반\"\n}"
                            )
                    }
            )
    )
    ResponseEntity<AdminProductInspectionDto.UpdateStatusResponse> updateStatus(
            @Parameter(description = "상품 ID", required = true, example = "1") @PathVariable Long productId,
            @RequestBody AdminProductInspectionDto.UpdateStatusRequest request
    );

    @Operation(
            summary = "[검수] 상태 변경 (일괄)",
            description = "동일한 검수 정책으로 여러 상품을 한 번에 처리합니다. 상품마다 이력 행이 생성됩니다.\n\n" +
                    "**반려(REJECTED):** `rejectReasonType` 필수, OTHER 시 `rejectDetail` 필수\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "처리 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductInspectionDto.BulkUpdateStatusResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "일부 상품 ID 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "상품 ID 목록 및 검수 상태",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminProductInspectionDto.BulkUpdateStatusRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "일괄 승인",
                                    value = "{\n  \"productIds\": [1, 2, 3],\n  \"inspectionStatus\": \"APPROVED\",\n  \"adminMemo\": \"일괄 승인\"\n}"
                            )
                    }
            )
    )
    ResponseEntity<AdminProductInspectionDto.BulkUpdateStatusResponse> bulkUpdateStatus(
            @RequestBody AdminProductInspectionDto.BulkUpdateStatusRequest request
    );
}

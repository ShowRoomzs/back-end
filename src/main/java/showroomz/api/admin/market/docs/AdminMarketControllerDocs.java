package showroomz.api.admin.market.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.DTO.AdminSellerDetailResponse;
import showroomz.api.admin.market.DTO.UpdateReviewMemoRequest;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.auth.DTO.SellerDto;

@Tag(name = "Admin - Seller", description = "관리자 마켓 가입 관리 API")
public interface AdminMarketControllerDocs {

    @Operation(
            summary = "마켓 가입 신청 관리 목록 조회",
            description = "마켓 가입 신청 내역을 조회합니다.\n\n" +
                    "**필터 기능:**\n" +
                    "- **status**: 신청서 상태 (PENDING: 심사대기, APPROVED: 승인, REJECTED: 반려, 미입력: 전체)\n" +
                    "- **keyword**: 브랜드명(마켓명) 부분 일치 검색\n\n" +
                    "**반환 정보:**\n" +
                    "- **applicationId**: 입점 신청서 ID\n" +
                    "- 판매자 및 마켓 기본 정보 (신청서 스냅샷 기준)\n" +
                    "- **businessType**, **businessNumber**: 해당 신청서에 저장된 사업자 구분·사업자 등록번호\n" +
                    "- **processedAt**: 관리자가 승인/반려 처리한 일시 (미처리 시 null)\n" +
                    "- **elapsedTime**: 신청일(`createdAt`)부터 현재까지 경과 시간 (`11h`, `3일 11h`)\n" +
                    "- 신청서 승인 상태 및 반려 사유 (반려된 경우)\n" +
                    "- **statusCounts**: 상태별 신청서 건수 (all / pending / approved / rejected). 브랜드명 검색어는 반영되며, status 필터는 반영되지 않음\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- size: 페이지당 항목 수 (기본값: 20)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMarketDto.ApplicationListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "목록 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"applicationId\": 100,\n" +
                                                    "      \"sellerId\": 1,\n" +
                                                    "      \"marketId\": 10,\n" +
                                                    "      \"email\": \"seller@example.com\",\n" +
                                                    "      \"name\": \"홍길동\",\n" +
                                                    "      \"marketName\": \"멋쟁이 옷장\",\n" +
                                                    "      \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "      \"status\": \"PENDING\",\n" +
                                                    "      \"rejectionReason\": null,\n" +
                                                    "      \"createdAt\": \"2024-01-15T10:30:00\",\n" +
                                                    "      \"elapsedTime\": \"11h\",\n" +
                                                    "      \"businessType\": \"개인사업자\",\n" +
                                                    "      \"businessNumber\": \"123-45-67890\",\n" +
                                                    "      \"processedAt\": null\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"applicationId\": 99,\n" +
                                                    "      \"sellerId\": 1,\n" +
                                                    "      \"marketId\": 10,\n" +
                                                    "      \"email\": \"seller@example.com\",\n" +
                                                    "      \"name\": \"홍길동\",\n" +
                                                    "      \"marketName\": \"멋쟁이 옷장\",\n" +
                                                    "      \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "      \"status\": \"REJECTED\",\n" +
                                                    "      \"rejectionReason\": \"INSUFFICIENT_DOCUMENTS\",\n" +
                                                    "      \"createdAt\": \"2024-01-10T09:00:00\",\n" +
                                                    "      \"elapsedTime\": \"3일 11h\",\n" +
                                                    "      \"businessType\": \"개인사업자\",\n" +
                                                    "      \"businessNumber\": \"hashed-business-number\",\n" +
                                                    "      \"processedAt\": \"2024-01-11T14:20:00\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 5,\n" +
                                                    "    \"totalResults\": 42,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  },\n" +
                                                    "  \"statusCounts\": {\n" +
                                                    "    \"all\": 42,\n" +
                                                    "    \"pending\": 10,\n" +
                                                    "    \"approved\": 25,\n" +
                                                    "    \"rejected\": 7\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminMarketDto.ApplicationListResponse> getMarketApplications(
            @ParameterObject showroomz.global.dto.PagingRequest pagingRequest,
            @ParameterObject AdminMarketDto.SearchCondition searchCondition
    );

    @Operation(
            summary = "입점 신청서 상세 조회",
            description = "입점 신청서(`seller_application`) ID로 상세 정보를 조회합니다.\n\n" +
                    "**조회 단위:** 신청서 1건 (`applicationId`)\n" +
                    "- 사업자·정산 정보는 해당 신청서 스냅샷 기준\n" +
                    "- 반려된 신청서는 브랜드명·사업자등록번호 해시만 보존된 상태로 반환\n\n" +
                    "**추가 응답 필드:**\n" +
                    "- `applicationId`, `sellerId`\n" +
                    "- `elapsedTime`: 신청일(`applicationDate`)부터 현재까지 경과 시간 (`11h`, `2일 19h`)\n" +
                    "- `processorId`: 해당 신청서를 승인/반려한 운영자(ADMIN) ID (심사대기 시 null)\n\n" +
                    "**처리 이력 (`processingHistory`):** 동일 판매자의 신청 접수/승인/반려 이력 (재신청 포함 누적)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminSellerDetailResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신청서를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신청서 없음",
                                            value = "{\"code\": \"APPLICATION_NOT_FOUND\", \"message\": \"존재하지 않는 신청입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminSellerDetailResponse> getMarketDetail(
            @Parameter(
                    description = "조회할 입점 신청서 ID",
                    required = true,
                    example = "100",
                    in = ParameterIn.PATH
            )
            @PathVariable Long applicationId
    );

    @Operation(
            summary = "마켓 판매자 계정 상태 변경 (승인/반려)",
            description = "회원가입을 신청한 마켓 판매자(SELLER) 계정의 상태를 변경합니다. \n\n" +
                    "**상태값:**\n" +
                    "- `APPROVED`: 승인 (로그인 가능)\n" +
                    "- `REJECTED`: 반려 (로그인 불가). 신청서·계정·마켓의 개인·사업자·정산 정보를 즉시 파기하고, **브랜드명**과 **사업자등록번호 일방향 해시**만 보존합니다.\n\n" +
                    "**요청 필드:**\n" +
                    "- `status`: `APPROVED` 또는 `REJECTED` (필수)\n" +
                    "- `rejectionReasonType`: **`status`가 `REJECTED`일 때 필수.** DB `rejectionReason`에는 enum 이름(예: `INSUFFICIENT_DOCUMENTS`)이 저장됩니다.\n" +
                    "- `rejectionReasonDetail`: **선택.** 전달 시 DB `rejectionReasonDetail`에 저장하고, `null`이면 해당 컬럼을 비웁니다. \n\n" +
                    "**`rejectionReasonType` 목록:**\n" +
                    "- `INSUFFICIENT_DOCUMENTS`: 서류 미비\n" +
                    "- `BUSINESS_REG_NUMBER_MISMATCH`: 사업자등록번호 불일치\n" +
                    "- `MAIL_ORDER_REPORT_INCOMPLETE`: 통신판매업신고 미완료\n" +
                    "- `BANK_ACCOUNT_ERROR`: 계좌 정보 오류\n" +
                    "- `DUPLICATE_APPLICATION`: 중복 신청\n" +
                    "- `OTHER`: 기타 (상세는 `rejectionReasonDetail`에 선택 입력)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "상태 변경 성공 - Status: 204 No Content",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 상태값, 반려 시 `rejectionReasonType` 누락, 또는 PENDING이 아닌 계정 처리 시도 등",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 상태값",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "반려인데 rejectionReasonType 없음",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "PENDING 상태가 아님",
                                            value = "{\"code\": \"ACCOUNT_NOT_PENDING\", \"message\": \"승인 대기 상태인 계정만 처리할 수 있습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "판매자 계정을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "계정 없음",
                                            value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "변경할 계정 상태 (APPROVED 또는 REJECTED)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SellerDto.UpdateStatusRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "승인 요청 예시",
                                    value = "{\n  \"status\": \"APPROVED\"\n}"
                            ),
                            @ExampleObject(
                                    name = "반려 요청 예시 (사전 정의된 사유)",
                                    value = "{\n  \"status\": \"REJECTED\",\n  \"rejectionReasonType\": \"INSUFFICIENT_DOCUMENTS\"\n}"
                            ),
                            @ExampleObject(
                                    name = "반려 (타입 + 상세)",
                                    value = "{\n  \"status\": \"REJECTED\",\n  \"rejectionReasonType\": \"OTHER\",\n  \"rejectionReasonDetail\": \"사업자 등록증이 흐릿합니다.\"\n}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateSellerStatus(
            @Parameter(
                    description = "상태를 변경할 판매자(Seller) ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long sellerId,
            @RequestBody SellerDto.UpdateStatusRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "셀러 검토 메모 수정",
            description = "관리자가 특정 셀러의 심사 검토 메모를 수정합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "수정 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (메모 길이 초과 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "판매자를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UpdateReviewMemoRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "검토 메모 수정",
                                    value = "{\n  \"reviewMemo\": \"서류 확인 완료, 마켓 URL 보완 필요\"\n}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateReviewMemo(
            @Parameter(
                    description = "검토 메모를 수정할 판매자(Seller) ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long sellerId,
            @Valid @RequestBody UpdateReviewMemoRequest request
    );
}

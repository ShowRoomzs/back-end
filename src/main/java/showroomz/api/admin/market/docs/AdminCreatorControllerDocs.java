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
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.auth.DTO.SellerDto;

@Tag(name = "Admin - Creator", description = "관리자 크리에이터 가입 관리 API")
public interface AdminCreatorControllerDocs {

    @Operation(
            summary = "크리에이터 가입 신청 관리 목록 조회",
            description = "크리에이터(CREATOR) 가입 신청 내역을 조회합니다. 상태별 필터링, 기간 조회, 키워드 검색이 가능합니다.\n\n" +
                    "**필터 기능:**\n" +
                    "- **status**: 크리에이터 상태 (PENDING: 승인 대기, APPROVED: 승인, REJECTED: 반려, null: 전체)\n" +
                    "- **startDate / endDate**: 신청일 기준 조회 기간 (YYYY-MM-DD)\n" +
                    "- **keyword**: 검색어 (부분 일치 검색)\n" +
                    "- **keywordType**: 검색 타입 (SELLER_ID: 신청 ID, MARKET_NAME: 쇼룸명, NAME: 이름(본명), PHONE_NUMBER: 연락처)\n\n" +
                    "**반환 정보:**\n" +
                    "- 가입 신청 PK, 쇼룸명, 신청일, 이름(본명), 전화번호\n" +
                    "- 현재 승인 상태 및 반려 사유 (반려된 경우)\n\n" +
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
                            schema = @Schema(implementation = showroomz.global.dto.PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "크리에이터 목록 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"sellerId\": 5,\n" +
                                                    "      \"showroomName\": \"감성 룩북\",\n" +
                                                    "      \"createdAt\": \"2024-03-01T14:00:00\",\n" +
                                                    "      \"name\": \"김지수\",\n" +
                                                    "      \"phoneNumber\": \"010-1111-2222\",\n" +
                                                    "      \"status\": \"PENDING\",\n" +
                                                    "      \"rejectionReason\": null\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"sellerId\": 6,\n" +
                                                    "      \"showroomName\": \"트렌디 피드\",\n" +
                                                    "      \"createdAt\": \"2024-02-20T09:30:00\",\n" +
                                                    "      \"name\": \"박서준\",\n" +
                                                    "      \"phoneNumber\": \"010-3333-4444\",\n" +
                                                    "      \"status\": \"REJECTED\",\n" +
                                                    "      \"rejectionReason\": \"플랫폼 확인 불가\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 2,\n" +
                                                    "    \"totalResults\": 15,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<showroomz.global.dto.PageResponse<AdminMarketDto.CreatorApplicationResponse>> getCreatorApplications(
            @ParameterObject showroomz.global.dto.PagingRequest pagingRequest,
            @ParameterObject AdminMarketDto.SearchCondition searchCondition
    );

    @Operation(
            summary = "크리에이터 상세 정보 조회",
            description = "특정 크리에이터의 상세 정보를 조회합니다.\n\n" +
                    "**반환 정보:** 가입 신청 PK, 이메일, 쇼룸명, 활동명, 플랫폼 URL, 이름(본명), 전화번호\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMarketDto.CreatorDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "크리에이터 상세 조회 예시",
                                            value = "{\n" +
                                                    "  \"sellerId\": 5,\n" +
                                                    "  \"email\": \"creator@example.com\",\n" +
                                                    "  \"showroomName\": \"감성 룩북\",\n" +
                                                    "  \"activityName\": \"감성크리에이터지수\",\n" +
                                                    "  \"platformUrl\": \"https://instagram.com/creator_jisu\",\n" +
                                                    "  \"name\": \"김지수\",\n" +
                                                    "  \"phoneNumber\": \"010-1111-2222\",\n" +
                                                    "  \"status\": \"PENDING\",\n" +
                                                    "  \"rejectionReason\": null\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "크리에이터 또는 쇼룸을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "크리에이터 없음",
                                            value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "쇼룸 없음",
                                            value = "{\"code\": \"MARKET_NOT_FOUND\", \"message\": \"존재하지 않는 마켓입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminMarketDto.CreatorDetailResponse> getCreatorDetail(
            @Parameter(
                    description = "조회할 크리에이터 ID",
                    required = true,
                    example = "5",
                    in = ParameterIn.PATH
            )
            @PathVariable Long creatorId
    );

    @Operation(
            summary = "크리에이터 계정 상태 변경 (승인/반려)",
            description = "가입 신청한 크리에이터 계정의 상태를 변경합니다. (APPROVED, REJECTED)\n\n" +
                    "**상태값:**\n" +
                    "- `APPROVED`: 승인 (로그인 가능, 크리에이터 입점 승인 메일 발송)\n" +
                    "- `REJECTED`: 반려 (로그인 불가, 반려 사유 메일 발송)\n\n" +
                    "**반려 사유:**\n" +
                    "- REJECTED 상태일 때 `rejectionReasonType` 필드는 필수입니다.\n" +
                    "- `rejectionReasonType`이 `OTHER`일 경우 `rejectionReasonDetail` 필드도 필수입니다.\n\n" +
                    "**반려 사유 타입:**\n" +
                    "- `BUSINESS_INFO_UNVERIFIED`: 사업자정보 확인 불가\n" +
                    "- `CRITERIA_NOT_MET`: 입점 기준 미달성\n" +
                    "- `INAPPROPRIATE_MARKET_NAME`: 마켓명 부적절\n" +
                    "- `OTHER`: 기타(직접 작성) - 이 경우 `rejectionReasonDetail` 필수\n\n" +
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
                    description = "잘못된 상태값 요청 또는 PENDING 상태가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 상태값",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "크리에이터 계정을 찾을 수 없음",
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
                                    value = "{\n  \"status\": \"REJECTED\",\n  \"rejectionReasonType\": \"CRITERIA_NOT_MET\"\n}"
                            ),
                            @ExampleObject(
                                    name = "반려 요청 예시 (기타 사유)",
                                    value = "{\n  \"status\": \"REJECTED\",\n  \"rejectionReasonType\": \"OTHER\",\n  \"rejectionReasonDetail\": \"플랫폼 팔로워 수 기준 미달입니다.\"\n}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateCreatorStatus(
            @Parameter(
                    description = "상태를 변경할 크리에이터 ID",
                    required = true,
                    example = "5",
                    in = ParameterIn.PATH
            )
            @PathVariable Long creatorId,
            @RequestBody SellerDto.UpdateStatusRequest request
    );
}

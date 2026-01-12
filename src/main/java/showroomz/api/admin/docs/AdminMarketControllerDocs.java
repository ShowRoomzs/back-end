package showroomz.api.admin.docs;

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

@Tag(name = "Admin - Seller", description = "관리자 판매자 가입 관리 API")
public interface AdminMarketControllerDocs {

    @Operation(
            summary = "판매자 가입 신청 관리 목록 조회",
            description = "판매자 가입 신청 내역을 조회합니다. 상태별 필터링, 기간 조회, 키워드 검색이 가능합니다.\n\n" +
                    "**필터 기능:**\n" +
                    "- **status**: 판매자 상태 (PENDING: 승인 대기, APPROVED: 승인, REJECTED: 반려, null: 전체)\n" +
                    "- **startDate / endDate**: 신청일 기준 조회 기간 (YYYY-MM-DD)\n" +
                    "- **keyword**: 검색어 (부분 일치 검색)\n" +
                    "- **keywordType**: 검색 타입 (SELLER_ID: 신청 ID, MARKET_NAME: 마켓명, NAME: 담당자 이름, PHONE_NUMBER: 연락처)\n\n" +

                    "**반환 정보:**\n" +
                    "- 판매자 및 마켓 기본 정보\n" +
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
                                            name = "목록 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"sellerId\": 1,\n" +
                                                    "      \"marketId\": 10,\n" +
                                                    "      \"email\": \"seller@example.com\",\n" +
                                                    "      \"name\": \"홍길동\",\n" +
                                                    "      \"marketName\": \"멋쟁이 옷장\",\n" +
                                                    "      \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "      \"status\": \"PENDING\",\n" +
                                                    "      \"rejectionReason\": null,\n" +
                                                    "      \"createdAt\": \"2024-01-15T10:30:00\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"sellerId\": 2,\n" +
                                                    "      \"marketId\": 11,\n" +
                                                    "      \"email\": \"rejected@example.com\",\n" +
                                                    "      \"name\": \"김철수\",\n" +
                                                    "      \"marketName\": \"빈티지 샵\",\n" +
                                                    "      \"phoneNumber\": \"010-9876-5432\",\n" +
                                                    "      \"status\": \"REJECTED\",\n" +
                                                    "      \"rejectionReason\": \"사업자 등록증 식별 불가\",\n" +
                                                    "      \"createdAt\": \"2024-01-10T09:00:00\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 5,\n" +
                                                    "    \"totalResults\": 42,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<showroomz.global.dto.PageResponse<AdminMarketDto.ApplicationResponse>> getMarketApplications(
            @ParameterObject showroomz.global.dto.PagingRequest pagingRequest,
            @ParameterObject AdminMarketDto.SearchCondition searchCondition
    );

    @Operation(
            summary = "마켓 목록 조회",
            description = "어드민용 마켓 목록을 조회합니다. 승인된(APPROVED) 판매자의 마켓만 조회하며, 카테고리 필터와 마켓명 검색을 지원합니다.\n\n" +
                    "**필터/검색:**\n" +
                    "- mainCategory: 대표 카테고리(대분류)\n" +
                    "- marketName: 마켓명 검색어 (부분 일치)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = showroomz.global.dto.PageResponse.class)
                    )
            )
    })
    ResponseEntity<showroomz.global.dto.PageResponse<AdminMarketDto.MarketResponse>> getMarkets(
            @ParameterObject showroomz.global.dto.PagingRequest pagingRequest,
            @ParameterObject AdminMarketDto.MarketListSearchCondition searchCondition
    );

    @Operation(
            summary = "판매자 상세 정보 조회",
            description = "특정 판매자의 상세 정보(판매자 정보 및 마켓 정보)를 조회합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMarketDto.MarketDetailResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "판매자 또는 마켓을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "판매자 없음",
                                            value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "마켓 없음",
                                            value = "{\"code\": \"MARKET_NOT_FOUND\", \"message\": \"존재하지 않는 마켓입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminMarketDto.MarketDetailResponse> getMarketDetail(
            @Parameter(
                    description = "조회할 판매자 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long sellerId
    );

    @Operation(
            summary = "판매자 계정 상태 변경 (승인/반려)",
            description = "회원가입을 신청한 판매자 계정의 상태를 변경합니다. (APPROVED, REJECTED)\n\n" +
                    "**상태값:**\n" +
                    "- `APPROVED`: 승인 (로그인 가능)\n" +
                    "- `REJECTED`: 반려 (로그인 불가)\n\n" +
                    "**반려 사유:**\n" +
                    "- REJECTED 상태일 때 `rejectionReasonType` 필드는 필수입니다.\n" +
                    "- `rejectionReasonType`이 `OTHER`일 경우 `rejectionReasonDetail` 필드도 필수입니다.\n" +
                    "- APPROVED 상태로 변경 시 반려 사유 관련 필드는 무시됩니다.\n\n" +
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
                                    ),
                                    @ExampleObject(
                                            name = "PENDING 상태가 아님",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
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
                                    value = "{\n  \"status\": \"REJECTED\",\n  \"rejectionReasonType\": \"BUSINESS_INFO_UNVERIFIED\"\n}"
                            ),
                            @ExampleObject(
                                    name = "반려 요청 예시 (기타 사유)",
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
            @RequestBody SellerDto.UpdateStatusRequest request
    );
}

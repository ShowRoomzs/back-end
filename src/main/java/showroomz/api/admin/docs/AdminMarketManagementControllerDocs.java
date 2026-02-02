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
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.app.auth.DTO.ErrorResponse;

@Tag(name = "Admin - Market", description = "관리자 마켓 관리 API")
public interface AdminMarketManagementControllerDocs {

    @Operation(
            summary = "마켓 목록 조회",
            description = "어드민용 마켓 목록을 조회합니다. 승인된(APPROVED) 판매자의 마켓만 조회하며, 카테고리 필터와 마켓명 검색을 지원합니다.\n\n" +
                    "**필터/검색:**\n" +
                    "- mainCategoryId: 대표 카테고리 ID (정수)\n" +
                    "- marketName: 마켓명 검색어 (부분 일치)\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- size: 페이지당 항목 수 (기본값: 20)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (1부터 시작)", example = "1", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "페이지당 항목 수", example = "20", in = ParameterIn.QUERY),
                    @Parameter(name = "mainCategoryId", description = "대표 카테고리 ID 필터", example = "1", in = ParameterIn.QUERY),
                    @Parameter(name = "marketName", description = "마켓명 검색어", example = "멋쟁이", in = ParameterIn.QUERY)
            }
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
                                                    "  \"data\": [\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 10,\n" +
                                                    "      \"marketName\": \"멋쟁이 옷장\",\n" +
                                                    "      \"mainCategoryId\": 1,\n" +
                                                    "      \"mainCategoryName\": \"의류\",\n" +
                                                    "      \"sellerName\": \"홍길동\",\n" +
                                                    "      \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "      \"productCount\": 120,\n" +
                                                    "      \"createdAt\": \"2024-01-01T10:00:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 11,\n" +
                                                    "      \"marketName\": \"빈티지 샵\",\n" +
                                                    "      \"mainCategoryId\": 2,\n" +
                                                    "      \"mainCategoryName\": \"액세서리\",\n" +
                                                    "      \"sellerName\": \"김철수\",\n" +
                                                    "      \"phoneNumber\": \"010-9876-5432\",\n" +
                                                    "      \"productCount\": 85,\n" +
                                                    "      \"createdAt\": \"2024-01-05T14:30:00Z\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"pageSize\": 20,\n" +
                                                    "    \"totalElements\": 42,\n" +
                                                    "    \"totalPages\": 5,\n" +
                                                    "    \"isLast\": false\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\"code\": \"FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<showroomz.global.dto.PageResponse<AdminMarketDto.MarketResponse>> getMarkets(
            @ParameterObject showroomz.global.dto.PagingRequest pagingRequest,
            @ParameterObject AdminMarketDto.MarketListSearchCondition searchCondition
    );

    @Operation(
            summary = "마켓 관리 상세 정보 조회",
            description = "마켓 정보 관리용 상세 정보를 조회합니다. 마켓 관리 페이지에서 사용됩니다.\n\n" +
                    "**반환 정보:**\n" +
                    "- 마켓 기본 정보 (마켓명, 고객센터 번호, 이미지, 소개글 등)\n" +
                    "- 마켓 URL 및 대표 카테고리 (ID, 이름)\n" +
                    "- SNS 링크 목록\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMarketDto.MarketAdminDetailResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 없음",
                                            value = "{\"code\": \"MARKET_NOT_FOUND\", \"message\": \"존재하지 않는 마켓입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminMarketDto.MarketAdminDetailResponse> getMarketInfo(
            @Parameter(
                    description = "조회할 마켓 ID",
                    required = true,
                    example = "10",
                    in = ParameterIn.PATH
            )
            @PathVariable Long marketId
    );
}

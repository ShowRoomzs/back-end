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
import org.springframework.web.bind.annotation.ModelAttribute;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Market", description = "관리자 마켓 관리 API")
public interface AdminMarketListControllerDocs {

    @Operation(
            summary = "마켓 목록 조회",
            description = "어드민용 마켓 목록을 조회합니다. 대표 카테고리·검색·마켓 운영 상태로 필터링할 수 있습니다.\n\n" +
                    "**필터/검색:**\n" +
                    "- mainCategoryId: 대표(메인) 카테고리 ID (통합 검색과 별도, 미입력 시 전체)\n" +
                    "- keywordType + keyword: MARKET_ID, MARKET_NAME, MANAGER_NAME, CONTACT (부분 일치)\n" +
                    "- 카테고리 조건과 키워드 조건은 AND로 결합됩니다.\n" +
                    "- status: 마켓 운영 상태 ACTIVE, SUSPENDED, DORMANT(휴면), WITHDRAWN(탈퇴) (미입력 시 전체)\n\n" +
                    "**응답:**\n" +
                    "- totalSalesAmount: 누적 판매액 (미구현, 현재 0 고정)\n" +
                    "- status: 판매자(마켓) 계정 상태\n" +
                    "- processedDate: 입점일\n\n" +
                    "**페이징:**\n" +
                    "- page: 1부터 시작 (기본 1)\n" +
                    "- size: 페이지 크기 (기본 20)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (1부터 시작)", example = "1", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "페이지당 항목 수", example = "20", in = ParameterIn.QUERY),
                    @Parameter(name = "mainCategoryId", description = "대표 카테고리 ID 필터", example = "1", in = ParameterIn.QUERY),
                    @Parameter(name = "keywordType", description = "검색 타입", example = "MARKET_NAME", in = ParameterIn.QUERY),
                    @Parameter(name = "keyword", description = "검색어", example = "멋쟁이", in = ParameterIn.QUERY),
                    @Parameter(name = "status", description = "마켓 운영 상태 필터 (ACTIVE, SUSPENDED, DORMANT, WITHDRAWN)", example = "ACTIVE", in = ParameterIn.QUERY)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "목록 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 10,\n" +
                                                    "      \"marketName\": \"멋쟁이 옷장\",\n" +
                                                    "      \"mainCategoryId\": 1,\n" +
                                                    "      \"mainCategoryName\": \"의류\",\n" +
                                                    "      \"sellerName\": \"홍길동\",\n" +
                                                    "      \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "      \"productCount\": 120,\n" +
                                                    "      \"totalSalesAmount\": 0,\n" +
                                                    "      \"status\": \"APPROVED\",\n" +
                                                    "      \"processedDate\": \"2024-01-01T10:00:00\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 5,\n" +
                                                    "    \"totalResults\": 42,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
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
    ResponseEntity<PageResponse<AdminMarketDto.MarketResponse>> getMarkets(
            @ParameterObject @ModelAttribute PagingRequest pagingRequest,
            @ParameterObject @ModelAttribute AdminMarketDto.MarketSearchRequest searchRequest
    );
}

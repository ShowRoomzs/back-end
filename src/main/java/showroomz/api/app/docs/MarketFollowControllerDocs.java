package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.market.DTO.FollowingMarketResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Shop", description = "사용자 Shop API")
public interface MarketFollowControllerDocs {

    @Operation(
            summary = "마켓 팔로우",
            description = "마켓을 팔로우합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 이미 팔로우 중이면 아무 동작도 하지 않음\n" +
                    "- 팔로우하지 않았으면 팔로우 추가\n\n" +
                    "**권한:** USER (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "marketId", description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "팔로우 성공 (No Content)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 정보 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_AUTH_INFO\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
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
                                            value = "{\n" +
                                                    "  \"code\": \"MARKET_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 마켓입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> followMarket(
            @Parameter(description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            Long marketId
    );

    @Operation(
            summary = "마켓 팔로우 취소",
            description = "마켓 팔로우를 취소합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 팔로우 중이면 팔로우 삭제\n" +
                    "- 팔로우하지 않았으면 아무 동작도 하지 않음\n\n" +
                    "**권한:** USER (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "marketId", description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "팔로우 취소 성공 (No Content)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 정보 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_AUTH_INFO\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
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
                                            value = "{\n" +
                                                    "  \"code\": \"MARKET_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 마켓입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> unfollowMarket(
            @Parameter(description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            Long marketId
    );

    @Operation(
            summary = "팔로우한 마켓 목록 조회",
            description = "사용자가 팔로우한 마켓 목록을 조회합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 최근 팔로우한 순서(최신순)대로 정렬되어 반환됩니다.\n\n" +
                    "**권한:** USER (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(
                                    name = "조회 성공",
                                    value = "{\n" +
                                            "  \"data\": [\n" +
                                            "    {\n" +
                                            "      \"shopId\": 1,\n" +
                                            "      \"shopName\": \"샘플 마켓\",\n" +
                                            "      \"shopImageUrl\": \"https://example.com/shop1.jpg\",\n" +
                                            "      \"shopType\": \"MARKET\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"shopId\": 2,\n" +
                                            "      \"shopName\": \"크리에이터 쇼룸\",\n" +
                                            "      \"shopImageUrl\": \"https://example.com/shop2.jpg\",\n" +
                                            "      \"shopType\": \"SHOWROOM\"\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"pageInfo\": {\n" +
                                            "    \"currentPage\": 1,\n" +
                                            "    \"pageSize\": 10,\n" +
                                            "    \"totalElements\": 25,\n" +
                                            "    \"totalPages\": 3,\n" +
                                            "    \"isLast\": false\n" +
                                            "  }\n" +
                                            "}"
                            )
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
                                            name = "인증 정보 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_AUTH_INFO\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<FollowingMarketResponse>> getFollowedMarkets(
            @Parameter(hidden = true) PagingRequest pagingRequest
    );
}


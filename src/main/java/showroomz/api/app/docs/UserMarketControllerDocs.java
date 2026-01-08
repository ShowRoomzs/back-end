package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketFollowResponse;

@Tag(name = "User - Market", description = "사용자용 마켓 API (조회/팔로우)")
public interface UserMarketControllerDocs {

    @Operation(
            summary = "마켓 상세 조회",
            description = "마켓 정보와 팔로워 수를 조회합니다. (비로그인 상태에서도 조회 가능)\n\n" +
                    "**응답 필드:**\n" +
                    "- `marketId`: 마켓 ID\n" +
                    "- `marketName`: 마켓명\n" +
                    "- `marketUrl`: 마켓 URL\n" +
                    "- `followerCount`: 이 마켓을 찜한 유저 수\n" +
                    "- `isFollowed`: 현재 유저가 찜했는지 여부 (비로그인 시 false)\n\n" +
                    "**권한:** 인증 불필요 (비로그인 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MarketDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시 (로그인 상태, 팔로우 중)",
                                            value = "{\n" +
                                                    "  \"marketId\": 1,\n" +
                                                    "  \"marketName\": \"쇼룸즈\",\n" +
                                                    "  \"marketUrl\": \"https://www.showroomz.co.kr/shop/showroomz\",\n" +
                                                    "  \"followerCount\": 150,\n" +
                                                    "  \"isFollowed\": true\n" +
                                                    "}",
                                            description = "로그인한 사용자가 해당 마켓을 팔로우한 경우"
                                    ),
                                    @ExampleObject(
                                            name = "성공 시 (비로그인 상태)",
                                            value = "{\n" +
                                                    "  \"marketId\": 1,\n" +
                                                    "  \"marketName\": \"쇼룸즈\",\n" +
                                                    "  \"marketUrl\": \"https://www.showroomz.co.kr/shop/showroomz\",\n" +
                                                    "  \"followerCount\": 150,\n" +
                                                    "  \"isFollowed\": false\n" +
                                                    "}",
                                            description = "비로그인 상태에서는 isFollowed가 항상 false"
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
    ResponseEntity<MarketDetailResponse> getMarketDetail(
            @Parameter(description = "마켓 ID", required = true, example = "1")
            Long marketId
    );

    @Operation(
            summary = "마켓 팔로우/언팔로우 토글",
            description = "마켓을 팔로우하거나 취소합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 이미 팔로우 중이면 → 언팔로우 (팔로우 취소)\n" +
                    "- 팔로우하지 않았으면 → 팔로우 (찜하기)\n\n" +
                    "**응답 필드:**\n" +
                    "- `isFollowed`: 최종 상태 (true: 팔로우됨, false: 취소됨)\n" +
                    "- `message`: 결과 메시지\n\n" +
                    "**권한:** USER (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "팔로우/언팔로우 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MarketFollowResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "팔로우 성공",
                                            value = "{\n" +
                                                    "  \"isFollowed\": true,\n" +
                                                    "  \"message\": \"마켓을 찜했습니다.\"\n" +
                                                    "}",
                                            description = "팔로우하지 않았던 마켓을 팔로우한 경우"
                                    ),
                                    @ExampleObject(
                                            name = "언팔로우 성공",
                                            value = "{\n" +
                                                    "  \"isFollowed\": false,\n" +
                                                    "  \"message\": \"마켓 찜을 취소했습니다.\"\n" +
                                                    "}",
                                            description = "이미 팔로우 중이던 마켓을 언팔로우한 경우"
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
                                            name = "인증 정보 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
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
    ResponseEntity<MarketFollowResponse> toggleFollow(
            @Parameter(description = "마켓 ID", required = true, example = "1")
            Long marketId
    );
}


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
import showroomz.api.app.market.DTO.MarketDetailResponse;

@Tag(name = "User - Market", description = "사용자용 마켓 API (조회/팔로우)")
public interface UserMarketControllerDocs {

    @Operation(
            summary = "마켓 상세 조회",
            description = "마켓 정보와 팔로워 수를 조회합니다. (비로그인 상태에서도 조회 가능)\n\n" +
                    "**응답 필드:**\n" +
                    "- `marketId`: 마켓 ID\n" +
                    "- `marketName`: 마켓명\n" +
                    "- `marketImageUrl`: 마켓 이미지 URL\n" +
                    "- `marketDescription`: 마켓 한줄 소개\n" +
                    "- `marketUrl`: 마켓 URL\n" +
                    "- `mainCategory`: 대표 카테고리\n" +
                    "- `csNumber`: 고객센터 번호\n" +
                    "- `snsLink1`: SNS 링크 1\n" +
                    "- `snsLink2`: SNS 링크 2\n" +
                    "- `snsLink3`: SNS 링크 3\n" +
                    "- `followerCount`: 이 마켓을 찜한 유저 수\n" +
                    "- `isFollowed`: 현재 유저가 찜했는지 여부 (비로그인 시 false)\n\n" +
                    "**권한:** 인증 불필요 (비로그인 가능)",
            parameters = {
                    @Parameter(name = "marketId", description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
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
                                                    "  \"marketImageUrl\": \"https://s3.amazonaws.com/bucket/market-image.jpg\",\n" +
                                                    "  \"marketDescription\": \"트렌디한 라이프스타일을 제안하는 마켓입니다.\",\n" +
                                                    "  \"marketUrl\": \"https://www.showroomz.co.kr/shop/showroomz\",\n" +
                                                    "  \"mainCategory\": \"패션/의류\",\n" +
                                                    "  \"csNumber\": \"1588-0000\",\n" +
                                                    "  \"snsLink1\": \"https://instagram.com/showroomz\",\n" +
                                                    "  \"snsLink2\": \"https://facebook.com/showroomz\",\n" +
                                                    "  \"snsLink3\": \"https://twitter.com/showroomz\",\n" +
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
                                                    "  \"marketImageUrl\": \"https://s3.amazonaws.com/bucket/market-image.jpg\",\n" +
                                                    "  \"marketDescription\": \"트렌디한 라이프스타일을 제안하는 마켓입니다.\",\n" +
                                                    "  \"marketUrl\": \"https://www.showroomz.co.kr/shop/showroomz\",\n" +
                                                    "  \"mainCategory\": \"패션/의류\",\n" +
                                                    "  \"csNumber\": \"1588-0000\",\n" +
                                                    "  \"snsLink1\": \"https://instagram.com/showroomz\",\n" +
                                                    "  \"snsLink2\": \"https://facebook.com/showroomz\",\n" +
                                                    "  \"snsLink3\": null,\n" +
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
            @Parameter(description = "마켓 ID", required = true, example = "1", in = ParameterIn.PATH)
            Long marketId
    );

    @Operation(
            summary = "마켓 팔로우 (찜 하기)",
            description = "마켓을 팔로우(찜)합니다.\n\n" +
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
            summary = "마켓 팔로우 취소 (찜 취소)",
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
}


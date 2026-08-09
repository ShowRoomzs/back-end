package showroomz.api.seller.connection.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.connection.dto.ConnectRequest;
import showroomz.api.seller.connection.dto.ConnectResponse;
import showroomz.api.seller.connection.dto.ConnectionCodeCheckResponse;
import showroomz.api.seller.connection.dto.ConnectionCreatorSearchItem;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Seller - Connection", description = "파트너센터 연결 요청 API (§13)")
public interface SellerConnectionControllerDocs {

    @Operation(
            summary = "쇼룸명 검색",
            description = "탐색·추천 없이 쇼룸명 부분 일치로만 인플루언서를 찾는다(§13-6).\n\n" +
                    "**권한:** SELLER\n\n" +
                    "결과마다 팔로워 수(`followerCount`), 프로필 이미지 URL(`profileImageUrl`), " +
                    "현재 연결 상태(`connectionStatus`)를 함께 내려준다 — null이면 연결 이력 없음(요청 가능), " +
                    "REQUESTED/CONNECTED면 화면에서 [요청] 버튼 대신 상태 배지로 대체해 중복 요청을 애초에 막는다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"creatorId\": 12,\n" +
                                                    "      \"showroomName\": \"민지의 쇼룸\",\n" +
                                                    "      \"followerCount\": 12000,\n" +
                                                    "      \"profileImageUrl\": \"https://cdn.example.com/profiles/12.png\",\n" +
                                                    "      \"connectionStatus\": null\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"creatorId\": 15,\n" +
                                                    "      \"showroomName\": \"민지픽\",\n" +
                                                    "      \"followerCount\": 8000,\n" +
                                                    "      \"profileImageUrl\": null,\n" +
                                                    "      \"connectionStatus\": \"REQUESTED\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"creatorId\": 21,\n" +
                                                    "      \"showroomName\": \"민지스타일\",\n" +
                                                    "      \"followerCount\": 6400,\n" +
                                                    "      \"profileImageUrl\": \"https://cdn.example.com/profiles/21.png\",\n" +
                                                    "      \"connectionStatus\": \"CONNECTED\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 3,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
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
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<ConnectionCreatorSearchItem>> searchCreators(
            @Parameter(description = "쇼룸명 검색어(미입력 시 전체)", example = "민지") @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "연결코드 확인",
            description = "인플루언서에게 전달받은 연결코드가 유효한지 확인한다(§13-6).\n\n" +
                    "**권한:** SELLER\n\n" +
                    "일치하는 코드가 없어도 예외를 던지지 않고 found=false로 응답한다 — 오타 등으로 흔히 발생하는 " +
                    "정상적인 케이스라 오류 취급하지 않는다.\n\n" +
                    "일치하면 확인 카드에 필요한 팔로워 수(`followerCount`)·프로필 이미지(`profileImageUrl`)와 함께 " +
                    "현재 연결 상태(`connectionStatus`)를 내려준다 — null이면 [요청 보내기] 활성, " +
                    "REQUESTED/CONNECTED면 이미 유효한 연결이 있어 요청할 수 없다(검색 응답과 동일한 규칙)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "확인 완료(found로 존재 여부 판단)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConnectionCodeCheckResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "코드 일치 — 요청 가능",
                                            value = "{\n" +
                                                    "  \"found\": true,\n" +
                                                    "  \"creatorId\": 12,\n" +
                                                    "  \"showroomName\": \"민지의 쇼룸\",\n" +
                                                    "  \"followerCount\": 8000,\n" +
                                                    "  \"profileImageUrl\": \"https://cdn.example.com/profiles/12.png\",\n" +
                                                    "  \"connectionStatus\": null\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "코드 일치 — 이미 연결된 상대",
                                            value = "{\n" +
                                                    "  \"found\": true,\n" +
                                                    "  \"creatorId\": 12,\n" +
                                                    "  \"showroomName\": \"민지의 쇼룸\",\n" +
                                                    "  \"followerCount\": 8000,\n" +
                                                    "  \"profileImageUrl\": null,\n" +
                                                    "  \"connectionStatus\": \"CONNECTED\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "코드 불일치",
                                            value = "{\n" +
                                                    "  \"found\": false,\n" +
                                                    "  \"creatorId\": null,\n" +
                                                    "  \"showroomName\": null,\n" +
                                                    "  \"followerCount\": null,\n" +
                                                    "  \"profileImageUrl\": null,\n" +
                                                    "  \"connectionStatus\": null\n" +
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
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ConnectionCodeCheckResponse> checkConnectionCode(
            @Parameter(description = "연결코드", required = true, example = "AB3K7M9X") @RequestParam("code") String code
    );

    @Operation(
            summary = "연결 요청",
            description = "creatorId 또는 connectionCode 중 정확히 하나로 상대를 지정해 연결을 요청한다(§13-6).\n\n" +
                    "**권한:** SELLER\n\n" +
                    "이미 CONNECTED/REQUESTED 상태면 409로 거부된다(검색·코드확인 응답의 상태값으로 화면에서 " +
                    "먼저 걸러지는 게 정상 흐름이며, 이 검증은 서버 측 최종 방어선이다)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "요청 생성/재요청 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConnectResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "요청 성공",
                                            value = "{\n" +
                                                    "  \"connectionId\": 101,\n" +
                                                    "  \"status\": \"REQUESTED\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "creatorId/connectionCode 둘 다 없거나 둘 다 있음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "대상 미지정",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_TARGET_REQUIRED\",\n" +
                                                    "  \"message\": \"creatorId 또는 connectionCode 중 하나는 필수입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "둘 다 지정",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_TARGET_AMBIGUOUS\",\n" +
                                                    "  \"message\": \"creatorId와 connectionCode는 동시에 지정할 수 없습니다.\"\n" +
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
                    description = "대상 인플루언서를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "크리에이터 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"CREATOR_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 크리에이터입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 연결됨/요청중인 상대",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "중복 요청",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_ALREADY_EXISTS\",\n" +
                                                    "  \"message\": \"이미 연결되었거나 요청중인 상대입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ConnectResponse> requestConnection(
            @Valid @RequestBody ConnectRequest request
    );
}

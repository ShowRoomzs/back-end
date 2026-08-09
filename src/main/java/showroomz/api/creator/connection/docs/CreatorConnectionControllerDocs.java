package showroomz.api.creator.connection.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.connection.dto.ConnectionCodeResponse;
import showroomz.api.creator.connection.dto.ConnectionRequestItem;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - Connection", description = "쇼룸 스튜디오 연결 요청함 API (§14)")
public interface CreatorConnectionControllerDocs {

    @Operation(
            summary = "받은 연결 요청 목록",
            description = "연결은 항상 브랜드가 발신한다(§13-6·§14-2) — 인플루언서는 수락/거절만 한다.\n\n" +
                    "요청 시각 내림차순이며, 카드 부가 정보(\"뷰티 브랜드 · 어제 요청\", S12)를 그릴 수 있도록 " +
                    "브랜드 대표 카테고리명(`mainCategoryName`)을 함께 내려준다.\n\n" +
                    "`keyword`는 목록 상단 \"브랜드명 검색\" 입력으로, 브랜드명 부분 일치다.\n\n" +
                    "**권한:** CREATOR"
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
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"connectionId\": 101,\n" +
                                                    "      \"marketId\": 7,\n" +
                                                    "      \"marketName\": \"쇼룸즈\",\n" +
                                                    "      \"marketImageUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket/market/7.jpg\",\n" +
                                                    "      \"mainCategoryName\": \"뷰티\",\n" +
                                                    "      \"requestedAt\": \"2026-08-06T14:22:10\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"connectionId\": 98,\n" +
                                                    "      \"marketId\": 3,\n" +
                                                    "      \"marketName\": \"트렌디샵\",\n" +
                                                    "      \"marketImageUrl\": null,\n" +
                                                    "      \"mainCategoryName\": null,\n" +
                                                    "      \"requestedAt\": \"2026-08-05T09:01:44\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 2,\n" +
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
    ResponseEntity<PageResponse<ConnectionRequestItem>> getRequests(
            @Parameter(description = "브랜드명 검색어 — 비우면 전체", example = "데일리코스")
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "연결 요청 수락",
            description = "카드별로 개별 처리한다(일괄 처리 없음, §14-4). 수락 시 CONNECTED로 전이한다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "수락 성공 (본문 없음)"),
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
                    responseCode = "403",
                    description = "본인에게 온 요청이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 연결 요청에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 연결 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "연결 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 연결 요청입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 요청(REQUESTED 상태 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "상태 불일치",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_INVALID_STATUS\",\n" +
                                                    "  \"message\": \"처리할 수 없는 연결 상태입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> accept(
            @Parameter(description = "연결 ID", required = true, example = "101") @PathVariable("connectionId") Long connectionId
    );

    @Operation(
            summary = "연결 요청 거절",
            description = "카드별로 개별 처리한다(일괄 처리 없음, §14-4). 거절 시 REJECTED로 전이한다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "거절 성공 (본문 없음)"),
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
                    responseCode = "403",
                    description = "본인에게 온 요청이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 연결 요청에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 연결 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "연결 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 연결 요청입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 요청(REQUESTED 상태 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "상태 불일치",
                                            value = "{\n" +
                                                    "  \"code\": \"CONNECTION_INVALID_STATUS\",\n" +
                                                    "  \"message\": \"처리할 수 없는 연결 상태입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> reject(
            @Parameter(description = "연결 ID", required = true, example = "101") @PathVariable("connectionId") Long connectionId
    );

    @Operation(
            summary = "내 연결코드 조회",
            description = "쇼룸별로 고정(영구) 발급된 연결코드를 조회한다(§13-6). 화면은 아직 미설계(§13-12 #1·§14-8)이나 " +
                    "API는 먼저 열어둔다.\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConnectionCodeResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"connectionCode\": \"AB3K7M9X\",\n" +
                                                    "  \"issuedAt\": \"2026-07-01T10:00:00\"\n" +
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
    ResponseEntity<ConnectionCodeResponse> getMyConnectionCode();

    @Operation(
            summary = "연결코드 재발급",
            description = "인플루언서가 원하면 언제든 재발급할 수 있다(§13-6). 기존 코드는 즉시 무효화된다.\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "재발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConnectionCodeResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "재발급 성공",
                                            value = "{\n" +
                                                    "  \"connectionCode\": \"Q2W8R5TY\",\n" +
                                                    "  \"issuedAt\": \"2026-08-08T15:30:00\"\n" +
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
    ResponseEntity<ConnectionCodeResponse> reissueConnectionCode();
}

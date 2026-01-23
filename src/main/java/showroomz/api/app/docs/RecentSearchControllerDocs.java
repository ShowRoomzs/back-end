package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.recentSearch.DTO.RecentSearchResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Search", description = "검색 관련 API")
public interface RecentSearchControllerDocs {

    @Operation(
            summary = "내 최근 검색 기록 조회",
            description = "로그인된 사용자의 최근 검색 기록을 페이징하여 조회합니다.\n\n" +

                    "**페이징 파라미터**\n" +
                    "- `page`: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- `size`: 페이지당 항목 수 (기본값: 20)\n\n" +
                    "**응답 구조**\n" +
                    "- `content`: 검색 기록 배열\n" +
                    "  - `id`: 검색 기록 ID\n" +
                    "  - `term`: 검색 키워드\n" +
                    "  - `createdAt`: 검색 시각 (UTC 기준)\n" +
                    "- `pageInfo`: 페이징 정보\n" +
                    "  - `page`: 현재 페이지 번호\n" +
                    "  - `size`: 페이지당 항목 수\n" +
                    "  - `totalElements`: 전체 항목 수\n" +
                    "  - `totalPages`: 전체 페이지 수\n" +
                    "  - `hasNext`: 다음 페이지 존재 여부\n" +
                    "  - `hasPrevious`: 이전 페이지 존재 여부\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시 (검색 기록 있음)",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 1,\n" +
                                                    "      \"term\": \"화이트 린넨 셔츠\",\n" +
                                                    "      \"createdAt\": \"2025-01-15T10:30:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"id\": 2,\n" +
                                                    "      \"term\": \"데님 팬츠\",\n" +
                                                    "      \"createdAt\": \"2025-01-14T15:20:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"id\": 3,\n" +
                                                    "      \"term\": \"니트 스웨터\",\n" +
                                                    "      \"createdAt\": \"2025-01-13T09:10:00Z\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"page\": 1,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"totalElements\": 15,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"hasNext\": false,\n" +
                                                    "    \"hasPrevious\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "성공 시 (검색 기록 없음)",
                                            value = "{\n" +
                                                    "  \"content\": [],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"page\": 1,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"totalElements\": 0,\n" +
                                                    "    \"totalPages\": 0,\n" +
                                                    "    \"hasNext\": false,\n" +
                                                    "    \"hasPrevious\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
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
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<RecentSearchResponse>> getMyRecentSearches(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User principal,
            @Parameter(
                    description = "페이징 요청 파라미터 (선택사항)",
                    schema = @Schema(implementation = PagingRequest.class)
            )
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "최근 검색 기록 삭제",
            description = "특정 최근 검색 기록을 삭제합니다.\n\n" +
                    "**기능 설명**\n" +
                    "- 사용자가 자신의 검색 기록 중 하나를 개별적으로 삭제할 수 있습니다.\n" +
                    "- 삭제하려는 검색 기록이 존재하고, 해당 기록의 소유자가 현재 로그인한 사용자인지 확인합니다.\n" +
                    "- 본인의 검색 기록이 아니거나 존재하지 않는 경우 삭제할 수 없습니다.\n\n" +
                    "**경로 파라미터**\n" +
                    "- `recentSearchId`: 삭제할 검색 기록의 ID (필수)\n\n" +
                    "**응답 코드**\n" +
                    "- `204 No Content`: 삭제 성공 (응답 본문 없음)\n" +
                    "- `400 Bad Request`: 잘못된 요청 (존재하지 않거나 본인의 검색 기록이 아님)\n" +
                    "- `401 Unauthorized`: 인증 정보가 유효하지 않음\n" +
                    "- `404 Not Found`: 사용자를 찾을 수 없음\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "삭제 성공 - Status: 204 No Content",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 - Status: 400 Bad Request\n" +
                            "- 검색 기록이 존재하지 않음\n" +
                            "- 본인의 검색 기록이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "존재하지 않는 검색 기록",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "본인의 검색 기록이 아님",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
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
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> deleteRecentSearch(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User principal,
            @Parameter(
                    name = "recentSearchId",
                    description = "삭제할 검색 기록의 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long recentSearchId
    );

    @Operation(
            summary = "최근 검색어 저장",
            description = "검색어를 최근 검색 기록에 저장합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 이미 존재하는 검색어라면 시간만 최신으로 갱신 (upsert)\n" +
                    "- 존재하지 않는 검색어라면 새로 생성\n" +
                    "- 검색어가 비어있거나 공백만 있는 경우 저장하지 않음\n\n" +
                    "**요청 파라미터**\n" +
                    "- `keyword`: 저장할 검색어 (필수)\n\n" +

                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "저장 성공 - Status: 204 No Content",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
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
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> saveRecentSearch(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User principal,
            @Parameter(
                    name = "keyword",
                    description = "저장할 검색어",
                    required = true,
                    example = "화이트 린넨 셔츠"
            )
            @RequestParam String keyword
    );
}

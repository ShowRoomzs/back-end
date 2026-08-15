package showroomz.api.app.recentSearch.docs;

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
import showroomz.api.app.auth.entity.UserPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.recentSearch.DTO.RecentSearchResponse;
import showroomz.api.app.recentSearch.DTO.RecentSearchSyncRequest;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Search", description = "검색 관련 API")
public interface RecentSearchControllerDocs {

    @Operation(
            summary = "내 최근 검색 기록 조회",
            description = "로그인된 사용자의 최근 검색 기록을 페이징하여 조회합니다.\n\n" +

                    "C14 최근 검색은 **쇼룸과 검색어가 한 목록에 시간순으로 섞인** 인스타 형식의 세로 리스트입니다. " +
                    "행 종류는 `type`으로 구분합니다.\n" +
                    "- `TERM`: 검색어 — 회색 원 안 돋보기 + 텍스트, 탭하면 그 단어로 재검색\n" +
                    "- `SHOWROOM`: 쇼룸 — 아바타 + 이름 + 아이디(@handle), 탭하면 바로 C4 쇼룸으로\n\n" +

                    "**페이징 파라미터**\n" +
                    "- `page`: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- `size`: 페이지당 항목 수 (기본값: 20)\n\n" +
                    "**응답 구조**\n" +
                    "- `content`: 검색 기록 배열\n" +
                    "  - `id`: 검색 기록 ID (개별 삭제 시 사용)\n" +
                    "  - `type`: 행 종류 (`TERM` | `SHOWROOM`)\n" +
                    "  - `term`: 검색 키워드. `SHOWROOM` 행에서는 저장 시점의 쇼룸명 스냅샷이므로 표시에는 쓰지 않습니다\n" +
                    "  - `showroom`: 쇼룸 정보 (`SHOWROOM` 행에만 채워짐, `TERM`이면 null)\n" +
                    "    - `showroomId`, `showroomName`, `showroomAddress`(@handle), `showroomImageUrl`\n" +
                    "    - `hasOngoingGroupBuy`: 진행 중 공구 보유 여부 — 아바타 로즈 링 표시용\n" +
                    "  - `createdAt`: 검색 시각 (UTC 기준)\n" +
                    "- `pageInfo`: 페이징 정보\n" +
                    "  - `currentPage`: 현재 페이지 번호\n" +
                    "  - `size`: 페이지당 항목 수\n" +
                    "  - `totalResults`: 전체 항목 수\n" +
                    "  - `totalPages`: 전체 페이지 수\n" +
                    "  - `hasNext`: 다음 페이지 존재 여부\n\n" +
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
                                            name = "성공 시 (검색 기록 있음 — 쇼룸·검색어 혼합)",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 1,\n" +
                                                    "      \"type\": \"SHOWROOM\",\n" +
                                                    "      \"term\": \"제니의 뷰티룸\",\n" +
                                                    "      \"showroom\": {\n" +
                                                    "        \"showroomId\": 12,\n" +
                                                    "        \"showroomName\": \"제니의 뷰티룸\",\n" +
                                                    "        \"showroomAddress\": \"jenny_beautyroom\",\n" +
                                                    "        \"showroomImageUrl\": \"https://cdn.showroomz.co.kr/showroom/profile/b.jpg\",\n" +
                                                    "        \"hasOngoingGroupBuy\": true\n" +
                                                    "      },\n" +
                                                    "      \"createdAt\": \"2026-08-15T10:30:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"id\": 2,\n" +
                                                    "      \"type\": \"TERM\",\n" +
                                                    "      \"term\": \"브라이\",\n" +
                                                    "      \"showroom\": null,\n" +
                                                    "      \"createdAt\": \"2026-08-14T15:20:00Z\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"id\": 3,\n" +
                                                    "      \"type\": \"TERM\",\n" +
                                                    "      \"term\": \"클린뷰티\",\n" +
                                                    "      \"showroom\": null,\n" +
                                                    "      \"createdAt\": \"2026-08-13T09:10:00Z\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 15,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "성공 시 (검색 기록 없음)",
                                            value = "{\n" +
                                                    "  \"content\": [],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 0,\n" +
                                                    "    \"totalResults\": 0,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
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
            @AuthenticationPrincipal UserPrincipal principal,
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
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(
                    name = "recentSearchId",
                    description = "삭제할 검색 기록의 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long recentSearchId
    );

    @Operation(
            summary = "최근 검색 전체 삭제",
            description = "로그인된 사용자의 최근 검색 기록을 모두 삭제합니다. 목록 상단의 [전체 삭제] 버튼용입니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 검색어(TERM)·쇼룸(SHOWROOM) 행을 가리지 않고 내 기록 전체를 지웁니다\n" +
                    "- 삭제 후 최근 검색 조회는 빈 목록을 반환합니다 (1d 최근 검색 없음 화면)\n\n" +
                    "**응답 코드**\n" +
                    "- `204 No Content`: 삭제 성공 (지울 기록이 없어도 204)\n\n" +
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
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "인증 실패",
                                    value = "{\n" +
                                            "  \"code\": \"UNAUTHORIZED\",\n" +
                                            "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "사용자 없음",
                                    value = "{\n" +
                                            "  \"code\": \"USER_NOT_FOUND\",\n" +
                                            "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<Void> deleteAllRecentSearches(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(
            summary = "최근 검색 저장 (검색어 또는 쇼룸)",
            description = "검색어 또는 쇼룸을 최근 검색 기록에 저장합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- `showroomId`를 보내면 **쇼룸(SHOWROOM) 행**으로 저장합니다 " +
                    "(검색 결과에서 쇼룸을 눌러 C4로 들어갈 때 호출)\n" +
                    "- `showroomId` 없이 `keyword`만 보내면 **검색어(TERM) 행**으로 저장합니다\n" +
                    "- 둘 다 보내면 `showroomId`가 우선입니다\n" +
                    "- 이미 있는 항목이면 시간만 최신으로 갱신 (upsert). 쇼룸은 쇼룸명이 바뀌어도 같은 행으로 합칩니다\n" +
                    "- 검색어가 비어있거나 공백만 있고 `showroomId`도 없으면 아무것도 저장하지 않음\n\n" +
                    "**요청 파라미터**\n" +
                    "- `keyword`: 저장할 검색어 (선택)\n" +
                    "- `showroomId`: 저장할 쇼룸(크리에이터) ID (선택)\n\n" +

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
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(
                    name = "keyword",
                    description = "저장할 검색어 (쇼룸을 저장할 때는 생략)",
                    example = "브라이"
            )
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(
                    name = "showroomId",
                    description = "저장할 쇼룸(크리에이터) ID — 검색 결과에서 쇼룸으로 진입할 때 사용",
                    example = "12"
            )
            @RequestParam(value = "showroomId", required = false) Long showroomId
    );

    @Operation(
            summary = "로컬 검색어 목록 서버 동기화",
            description = "클라이언트의 로컬 검색어 목록을 서버에 동기화합니다. 로그인 직후 호출합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 각 검색어마다 기존과 동일한 로직 적용 (있으면 갱신, 없으면 저장)\n" +
                    "- 빈 문자열이나 null은 무시\n\n" +
                    "**요청 본문:**\n" +
                    "- `keywords`: 동기화할 검색어 목록 (배열)\n" +
                    "  - `keyword` (필수): 검색어 문자열\n" +
                    "  - `createdAt` (선택): 검색 시각 (예: \"2026-02-05T10:15:40.673Z\"),\n" +
                    "    값이 없으면 서버 현재 시각이 사용됩니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "동기화 성공 - Status: 204 No Content",
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
    ResponseEntity<Void> syncRecentSearches(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "동기화할 검색어 목록", required = true)
            @RequestBody RecentSearchSyncRequest request
    );
}

package showroomz.api.app.showroom.docs;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.FollowingShowroomResponse;
import showroomz.api.app.showroom.type.FollowingShowroomSort;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Showroom Follow", description = "사용자 쇼룸 팔로우 API")
public interface ShowroomFollowControllerDocs {

    @Operation(
            summary = "쇼룸 팔로우",
            description = "쇼룸(크리에이터)을 팔로우합니다. 팔로우 대상은 쇼룸뿐이며 마켓(브랜드)은 팔로우할 수 없습니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 이미 팔로우 중이면 아무 동작도 하지 않음\n" +
                    "- 팔로우하지 않았으면 팔로우 추가\n\n" +
                    "**권한:** USER/CREATOR (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "showroomId", description = "쇼룸(크리에이터) ID", required = true, example = "1", in = ParameterIn.PATH)
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
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "쇼룸을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "쇼룸 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"CREATOR_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 크리에이터입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> followShowroom(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸(크리에이터) ID", required = true, example = "1", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId
    );

    @Operation(
            summary = "쇼룸 팔로우 취소",
            description = "쇼룸(크리에이터) 팔로우를 취소합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 팔로우 중이면 팔로우 삭제\n" +
                    "- 팔로우하지 않았으면 아무 동작도 하지 않음\n\n" +
                    "**권한:** USER/CREATOR (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "showroomId", description = "쇼룸(크리에이터) ID", required = true, example = "1", in = ParameterIn.PATH)
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
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "쇼룸을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "쇼룸 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"CREATOR_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 크리에이터입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> unfollowShowroom(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸(크리에이터) ID", required = true, example = "1", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId
    );

    @Operation(
            summary = "팔로우한 쇼룸 목록 조회",
            description = "사용자가 팔로우한 쇼룸 목록을 조회합니다. 마켓(브랜드)은 팔로우 대상이 아니므로 목록에 포함되지 않습니다.\n\n" +
                    "**정렬(sort):**\n" +
                    "- DEFAULT (기본): 최근에 게시물을 올린 쇼룸 순, 게시물이 없는 쇼룸은 뒤로 밀림\n" +
                    "- FOLLOW_LATEST: 팔로우한 날짜 최신순\n" +
                    "- FOLLOW_OLDEST: 팔로우한 날짜 오래된순\n\n" +
                    "**hasOngoingGroupBuy:** 쇼룸과 연결된 브랜드 상품 중 공구 상태가 진행중(IN_PROGRESS)인 건이 있으면 true (아바타 링 표시용)\n\n" +
                    "**팔로잉 수:** pageInfo.totalResults가 팔로우한 쇼룸 총 개수입니다.\n\n" +
                    "**권한:** USER/CREATOR (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "sort", description = "정렬 기준 (기본값 DEFAULT)", required = false,
                            example = "DEFAULT", in = ParameterIn.QUERY,
                            schema = @Schema(implementation = FollowingShowroomSort.class))
            }
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
                                            "  \"content\": [\n" +
                                            "    {\n" +
                                            "      \"showroomId\": 1,\n" +
                                            "      \"showroomName\": \"제니의 뷰티룸\",\n" +
                                            "      \"showroomImageUrl\": \"https://example.com/showroom1.jpg\",\n" +
                                            "      \"hasOngoingGroupBuy\": true,\n" +
                                            "      \"followedAt\": \"2026-08-01T10:20:30\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"showroomId\": 2,\n" +
                                            "      \"showroomName\": \"미아 스킨노트\",\n" +
                                            "      \"showroomImageUrl\": \"https://example.com/showroom2.jpg\",\n" +
                                            "      \"hasOngoingGroupBuy\": false,\n" +
                                            "      \"followedAt\": \"2026-07-28T09:00:00\"\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"pageInfo\": {\n" +
                                            "    \"currentPage\": 1,\n" +
                                            "    \"totalPages\": 3,\n" +
                                            "    \"totalResults\": 25,\n" +
                                            "    \"limit\": 10,\n" +
                                            "    \"hasNext\": true\n" +
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
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<FollowingShowroomResponse>> getFollowedShowrooms(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @RequestParam(name = "sort", required = false, defaultValue = "DEFAULT") FollowingShowroomSort sort,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    );
}

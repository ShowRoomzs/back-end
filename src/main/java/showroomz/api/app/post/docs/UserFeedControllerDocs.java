package showroomz.api.app.post.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.domain.post.type.LikedPostSort;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User Post", description = "유저 피드·좋아요 게시글 조회 API")
public interface UserFeedControllerDocs {

    @Operation(
            summary = "팔로잉 피드 조회",
            description = """
                    팔로우한 쇼룸들의 **게시중인** 게시물을 최신순으로 조회한다.
                    작성중(임시저장)·노출 중지·삭제된 게시물은 절대 나오지 않는다.

                    - **aspectRatio** — 카드 높이를 이 값으로 잡는다. 게시물마다 높이가 다르다(§24-2)
                    - **contentType** — 게시물 종류 판별자. 지금은 `GENERAL`뿐이다
                    - **isFollowing** — 이 목록은 정의상 전부 `true`다(팔로우한 쇼룸의 글만 모았으므로
                      다시 물어볼 필요가 없다). 카드 헤더에 팔로우 버튼을 그리지 않는다
                    - **hasOngoingGroupBuy** — 카드 헤더 **아바타의 로즈 링**. 게시물이 아니라 그 쇼룸이
                      지금 공구를 열고 있는지다(C2 팔로잉·C14 검색의 아바타 규칙과 같은 값)
                    - **likeLocked** — `true`면 마감된 공구다. 하트를 눌러도 새로 걸리지 않고 해제만 된다
                    - **팔로잉 0명** — 빈 목록(`content: []`)이 온다. 이때 화면은 발견 피드
                      (`/feed/recommended`)로 대체한다(C1 빈 상태)
                    - **권한:** USER
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "content": [
                                        {
                                          "contentType": "GENERAL",
                                          "post": {
                                            "postId": 123,
                                            "showroomId": 10,
                                            "showroomName": "리브의 방",
                                            "showroomImageUrl": "https://cdn.example.com/showrooms/10.png",
                                            "isFollowing": true,
                                            "hasOngoingGroupBuy": true,
                                            "content": "3주 루틴 기록",
                                            "imageUrls": ["https://cdn.example.com/posts/123-0.jpg"],
                                            "imageCount": 1,
                                            "aspectRatio": 0.8000,
                                            "impressionCount": 532,
                                            "isLiked": true,
                                            "likeCount": 12,
                                            "likeLocked": false,
                                            "publishedAt": "2026-03-04T12:34:56"
                                          }
                                        }
                                      ],
                                      "pageInfo": {
                                        "currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "토큰의 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getFollowingFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "추천 피드 조회 (C1 회원님을 위한 추천 · 발견 피드)",
            description = """
                    **팔로우하지 않은** 쇼룸의 게시중 게시물을 최신순으로 조회한다.
                    팔로잉 피드(`/feed/following`)의 여집합이라 두 목록에 같은 게시물이 겹치지 않는다.

                    - **화면 위치** — 팔로잉 피드를 끝까지 내리면 나오는 "새 게시물을 모두 확인했어요"
                      구분 블록 아래의 `[회원님을 위한 추천]` 영역이다. 팔로잉 피드의
                      `pageInfo.hasNext`가 false가 된 뒤 이 API를 이어 붙인다
                    - **팔로잉 0 (빈 상태)** — 같은 API가 그대로 **발견 피드**가 된다. 쇼룸 이름만
                      나열한 목록으로는 팔로우를 결정할 근거가 없어, 빈 상태에도 게시물을 보여준다
                    - **isFollowing** — 이 목록은 전부 `false`다. 카드 헤더의 **회색 팔로우 버튼**은
                      이 값이 false일 때만 그린다. 팔로우(`POST /v1/user/showrooms/{showroomId}/follow`)
                      후 버튼을 지우는 것은 클라이언트가 하고, 이미 나간 페이지를 다시 받지는 않는다
                    - **hasOngoingGroupBuy** — 카드 헤더 아바타의 로즈 링(진행 중 공구 보유 쇼룸)
                    - **본인 쇼룸 제외** — 크리에이터에게 자기 게시물은 추천하지 않는다
                    - **권한:** USER
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "content": [
                                        {
                                          "contentType": "GENERAL",
                                          "post": {
                                            "postId": 512,
                                            "showroomId": 31,
                                            "showroomName": "하루 코스메틱",
                                            "showroomImageUrl": "https://cdn.example.com/showrooms/31.png",
                                            "isFollowing": false,
                                            "hasOngoingGroupBuy": true,
                                            "content": "각질 정리부터 다시, 순한 성분만 골랐어요",
                                            "imageUrls": ["https://cdn.example.com/posts/512-0.jpg"],
                                            "imageCount": 1,
                                            "aspectRatio": 0.8000,
                                            "impressionCount": 1204,
                                            "isLiked": false,
                                            "likeCount": 88,
                                            "likeLocked": false,
                                            "publishedAt": "2026-03-04T07:10:00"
                                          }
                                        }
                                      ],
                                      "pageInfo": {
                                        "currentPage": 1, "totalPages": 3, "totalResults": 47, "limit": 20, "hasNext": true
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "토큰의 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getRecommendedFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "좋아요한 게시글 목록 조회 (C3 좋아요)",
            description = """
                    내가 좋아요한 게시물을 모아 조회한다. 공구·일반이 섞여 나오고 판별자는 `contentType`이다.
                    그 사이 내려간 게시물(삭제·노출 중지)은 목록에서 빠진다.

                    - **정렬(`sort`)** — 화면의 정렬 바텀시트와 1:1이다
                      - `DEFAULT` — 기본. 최근에 좋아요한 순서
                      - `LIKED_OLDEST` — 좋아요한 날짜: 오래된순
                      - `MOST_LIKED` — 좋아요 많은순 (게시물의 총 좋아요 수 기준)
                      - `GROUP_BUY_FIRST` — 공구 게시물 먼저. 공구 게시물이 아직 없어 현재는 `DEFAULT`와 같다
                    - **좋아요한 게시물 N** — 화면 상단 카운트는 `pageInfo.totalResults`다
                    - **isLiked** — 이 목록은 정의상 전부 `true`다(좋아요한 것만 모았으므로)
                    - **isFollowing** — 실제 팔로우 여부를 그대로 반영한다. 좋아요는 팔로우와
                      무관하게 누를 수 있어 이 목록엔 `true`/`false`가 섞여 나온다
                    - **마감된 공구** — 목록에서 지우지 않는다. 대신 `likeLocked=true`로 내려가며,
                      이 게시물은 **해제만** 된다(새 좋아요 요청은 서버가 거절한다)
                    - **좋아요 해제** — 해제해도 이 응답이 바로 바뀌지는 않는다.
                      다음 조회(당겨서 새로고침) 때 빠진다 — 오탭을 그 자리에서 되돌릴 수 있게 하기 위해서다
                    - **비로그인** — 401이다. 앱은 목록 대신 로그인 유도 화면을 그린다
                    - **권한:** USER
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "content": [
                                        {
                                          "contentType": "GENERAL",
                                          "post": {
                                            "postId": 123,
                                            "showroomId": 10,
                                            "showroomName": "미아 스킨노트",
                                            "showroomImageUrl": "https://cdn.example.com/showrooms/10.png",
                                            "isFollowing": false,
                                            "hasOngoingGroupBuy": false,
                                            "content": "요즘 아침 루틴 정리했어요",
                                            "imageUrls": ["https://cdn.example.com/posts/123-0.jpg"],
                                            "imageCount": 12,
                                            "aspectRatio": 0.8000,
                                            "impressionCount": 532,
                                            "isLiked": true,
                                            "likeCount": 42,
                                            "likeLocked": false,
                                            "publishedAt": "2026-03-04T12:34:56"
                                          }
                                        }
                                      ],
                                      "pageInfo": {
                                        "currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패 — 비로그인",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "토큰의 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getLikedPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "정렬 기준 (기본 DEFAULT)")
            @RequestParam(name = "sort", required = false, defaultValue = "DEFAULT") LikedPostSort sort,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);
}

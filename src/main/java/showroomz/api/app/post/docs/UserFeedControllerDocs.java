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

import static showroomz.api.app.post.docs.PostDocsExamples.*;

@Tag(name = "User Post", description = "소비자 피드·좋아요한 게시물 조회 API.")
public interface UserFeedControllerDocs {

    @Operation(
            summary = "팔로잉 피드 조회 (C1)",
            description = """
                    팔로우한 쇼룸들의 **게시중인** 게시물을 게시 시각 최신순으로 조회한다.
                    작성중(임시저장)·노출 중지·삭제된 게시물은 나오지 않는다.

                    **권한:** USER

                    **싣는 것** — 일반 게시물 + **진행 중인 공구 게시물**(판매 중 ∧ 종료 시각 전). 마감된 공구 게시물은 싣지 않는다.
                    공구 게시물은 공구가 오픈된 시각이 게시 시각이라 그 시점의 자리에 끼어든다.

                    - `contentType` — `GENERAL` / `GROUP_BUY`. `GROUP_BUY`면 `post.groupBuy`가 채워지고 사진이 없다(`imageUrls: []` · `aspectRatio: null`)
                    - `aspectRatio` — 카드 높이를 이 값으로 잡는다. 게시물마다 높이가 다르다(§24-2)
                    - `isFollowing` — 이 목록은 정의상 전부 `true`다(팔로우한 쇼룸의 글만 모았으므로). 카드 헤더에 팔로우 버튼을 그리지 않는다
                    - `hasOngoingGroupBuy` — 카드 헤더 **아바타의 로즈 링**. 게시물이 아니라 그 쇼룸이 지금 공구를 열고 있는지다 —
                      일반 게시물 카드에도 붙는다(C2 팔로잉·C14 검색의 아바타 규칙과 같은 값)
                    - `likeLocked` — 이 피드의 공구는 모두 진행 중이라 사실상 `false`다(페이지를 받은 뒤 종료 시각이 지나면 좋아요가 거절될 수 있다)
                    - **팔로잉 0명** — 빈 목록(`content: []`)이 온다. 이때 화면은 발견 피드(`/feed/recommended`)로 대체한다(C1 빈 상태)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "일반 + 진행 중 공구", value = PAGE_FOLLOWING),
                            @ExampleObject(name = "팔로잉 0명", value = PAGE_EMPTY)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패 — 비로그인",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND — 토큰의 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_USER_NOT_FOUND)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getFollowingFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "추천 피드 조회 (C1 회원님을 위한 추천 · 발견 피드)",
            description = """
                    **팔로우하지 않은** 쇼룸의 게시중 게시물을 게시 시각 최신순으로 조회한다.
                    팔로잉 피드(`/feed/following`)의 여집합이라 두 목록에 같은 게시물이 겹치지 않는다.

                    **권한:** 없음(비로그인 허용). 토큰을 실어 보내면 `isLiked`가 실제 값으로 채워진다.

                    **싣는 것** — 일반 게시물 + **진행 중인 공구 게시물**. 마감된 공구 게시물은 싣지 않는다(팔로잉 피드와 같은 규칙).

                    - **화면 위치** — 팔로잉 피드를 끝까지 내리면 나오는 "새 게시물을 모두 확인했어요" 구분 블록 아래의 `[회원님을 위한 추천]` 영역이다.
                      팔로잉 피드의 `pageInfo.hasNext`가 false가 된 뒤 이 API를 이어 붙인다
                    - **팔로잉 0 (빈 상태)** — 같은 API가 그대로 **발견 피드**가 된다. 쇼룸 이름만 나열한 목록으로는 팔로우를 결정할 근거가 없어,
                      빈 상태에도 게시물을 보여준다
                    - `isFollowing` — 이 목록은 전부 `false`다. 카드 헤더의 **회색 팔로우 버튼**은 이 값이 false일 때만 그린다.
                      팔로우(`POST /v1/user/showrooms/{showroomId}/follow`) 후 버튼을 지우는 것은 클라이언트가 하고, 이미 나간 페이지를 다시 받지는 않는다
                    - `hasOngoingGroupBuy` — 카드 헤더 아바타의 로즈 링(진행 중 공구 보유 쇼룸)
                    - **본인 쇼룸 제외** — 크리에이터에게 자기 게시물(공구 게시물 포함)은 추천하지 않는다
                    - **비로그인** — 뺄 팔로우도 본인 쇼룸도 없어 게시중 게시물 전체가 그대로 발견 피드가 되고, `isLiked` · `isFollowing`은 전부 `false`다.
                      하트·팔로우 버튼을 누르는 순간에만 로그인을 유도한다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "일반 + 진행 중 공구", value = PAGE_GENERAL_AND_ONGOING),
                            @ExampleObject(name = "추천할 게시물 없음", value = PAGE_EMPTY)
                    })),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND — 토큰의 사용자를 찾을 수 없음. 토큰을 보낸 경우에만 난다(토큰이 없으면 비로그인으로 200)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_USER_NOT_FOUND)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getRecommendedFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "좋아요한 게시글 목록 조회 (C3 좋아요)",
            description = """
                    내가 좋아요한 게시물을 모아 조회한다. 일반·공구 게시물이 섞여 나오고 판별자는 `contentType`이다.
                    그 사이 내려간 게시물(삭제·노출 중지, 공구의 숨김·종료 3일 경과·직권 중단)은 목록에서 빠진다.

                    **권한:** USER (비로그인은 401 — 앱은 목록 대신 로그인 유도 화면을 그린다)

                    **정렬(`sort`)** — 화면의 정렬 바텀시트와 1:1이다. 어떤 정렬이든 마지막 키는 좋아요한 시각이다.
                    - `DEFAULT` — 기본. 최근에 좋아요한 순서
                    - `LIKED_OLDEST` — 좋아요한 날짜: 오래된순
                    - `MOST_LIKED` — 좋아요 많은순(게시물의 총 좋아요 수 기준)
                    - `GROUP_BUY_FIRST` — 공구 게시물 먼저. **진행 중인** 공구(판매 중 ∧ 종료 시각 전)를 맨 위로 모으고, 그 안에서는 최근에 좋아요한 순서다.
                      마감된 공구 게시물은 앞으로 모으지 않는다 — 일반 게시물과 같은 줄에서 좋아요한 시각으로 섞인다

                    **공구 게시물**
                    - 진행 중이면 `post.groupBuy.products`가 전부 실리고 `likeLocked: false`다
                    - **마감된 공구(종료 후 3일 이내)도 목록에서 지우지 않는다** — `saleState: CLOSED` · `likeLocked: true` · `products: []`로 내려가며
                      **해제만** 된다(새 좋아요는 서버가 거절). 배지는 C3 문구 「공구 종료」로 그린다. 3일이 지나면 목록에서 빠진다

                    **그 밖의 필드**
                    - **좋아요한 게시물 N** — 화면 상단 카운트는 `pageInfo.totalResults`다(스크롤 위치와 무관)
                    - `isLiked` — 이 목록은 정의상 전부 `true`다
                    - `isFollowing` — 실제 팔로우 여부다. 좋아요는 팔로우와 무관하게 누를 수 있어 `true`/`false`가 섞인다
                    - **좋아요 해제** — 해제해도 이 응답이 바로 바뀌지는 않는다. 다음 조회(당겨서 새로고침) 때 빠진다 — 오탭을 그 자리에서 되돌릴 수 있게 하기 위해서다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "GROUP_BUY_FIRST — 진행 중 공구 · 일반 · 마감 공구",
                                    summary = "마감 공구는 likeLocked true · products []", value = PAGE_LIKED_GROUP_BUY_FIRST),
                            @ExampleObject(name = "좋아요한 게시물 없음", value = PAGE_EMPTY)
                    })),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `sort`에 정의되지 않은 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "401", description = "인증 실패 — 비로그인",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND — 토큰의 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_USER_NOT_FOUND)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getLikedPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "정렬 기준 — DEFAULT(기본) · LIKED_OLDEST · MOST_LIKED · GROUP_BUY_FIRST", example = "GROUP_BUY_FIRST")
            @RequestParam(name = "sort", required = false, defaultValue = "DEFAULT") LikedPostSort sort,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);
}

package showroomz.api.app.post.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
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
                                            "content": "3주 루틴 기록",
                                            "imageUrls": ["https://cdn.example.com/posts/123-0.jpg"],
                                            "imageCount": 1,
                                            "aspectRatio": 0.8000,
                                            "impressionCount": 532,
                                            "isLiked": true,
                                            "likeCount": 12,
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
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getFollowingFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)") PagingRequest pagingRequest);

    @Operation(
            summary = "좋아요한 게시글 목록 조회",
            description = """
                    내가 좋아요한 게시물을 최근 누른 순으로 조회한다.
                    그 사이 내려간 게시물(삭제·노출 중지)은 목록에서 빠진다.

                    응답 필드는 §24 용어를 따른다 — `likeCount` · `isLiked`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getLikedPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보") PagingRequest pagingRequest);
}

package showroomz.api.app.post.docs;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Post", description = "소비자 쇼룸 게시물 조회 API")
public interface PostControllerDocs {

    @Operation(
            summary = "게시글 상세 조회",
            description = """
                    게시중인 게시물만 조회된다. 작성중·노출 중지·삭제는 소비자에게 404다.

                    - **aspectRatio** — 게시물 비율(가로/세로). 게시물마다 높이가 다르므로
                      **고정 높이 카드로 그리면 안 되고** 이 값으로 자리를 잡는다(§24-2)
                    - **imageUrls** — 배열 순서가 노출 순서, 첫 장이 대표 사진
                    - 비로그인도 조회할 수 있고, 이 경우 `isLiked`는 false다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostDetailResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "postId": 123,
                                      "showroomId": 10,
                                      "showroomName": "리브의 방",
                                      "showroomImageUrl": "https://cdn.example.com/showrooms/10.jpg",
                                      "content": "3주 루틴 기록",
                                      "imageUrls": [
                                        "https://cdn.example.com/posts/123-0.jpg",
                                        "https://cdn.example.com/posts/123-1.jpg"
                                      ],
                                      "imageCount": 2,
                                      "aspectRatio": 0.8000,
                                      "impressionCount": 532,
                                      "isLiked": true,
                                      "likeCount": 12,
                                      "publishedAt": "2026-03-04T12:34:56",
                                      "modifiedAt": "2026-03-04T13:00:00"
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없거나 게시중이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "123", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(summary = "전체 게시글 목록 조회 (내부용)",
            description = "게시중인 게시물을 최신순으로 조회한다.")
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)") PagingRequest pagingRequest);

    @Operation(
            summary = "쇼룸별 게시글 목록 조회",
            description = """
                    한 쇼룸의 게시물을 최신순으로 조회한다. 게시중인 것만 나온다.

                    **contentType**이 게시물 종류 판별자다 — 지금은 `GENERAL`뿐이고, 공구 게시물이
                    들어오면 `GROUP_BUY`가 더해진다. 응답 구조는 그대로 유지된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "content": [
                                {
                                  "contentType": "GENERAL",
                                  "post": {
                                    "postId": 123,
                                    "showroomId": 10,
                                    "showroomName": "리브의 방",
                                    "showroomImageUrl": "https://cdn.example.com/showrooms/10.jpg",
                                    "content": "3주 루틴 기록",
                                    "imageUrls": ["https://cdn.example.com/posts/123-0.jpg"],
                                    "imageCount": 1,
                                    "aspectRatio": 0.8000,
                                    "impressionCount": 532,
                                    "isLiked": false,
                                    "likeCount": 12,
                                    "publishedAt": "2026-03-04T12:34:56"
                                  }
                                }
                              ],
                              "pageInfo": {
                                "currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false
                              }
                            }
                            """)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostListByShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸 ID", required = true, example = "10", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId,
            @Parameter(description = "페이징 정보") PagingRequest pagingRequest);

    @Operation(
            summary = "게시글 좋아요",
            description = "이미 눌러 뒀으면 아무 일도 일어나지 않고 204로 끝난다(멱등). 게시중이 아닌 게시물에는 누를 수 없다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "좋아요 완료"),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없거나 게시중이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> likePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "123", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(summary = "게시글 좋아요 취소", description = "누른 적이 없으면 그대로 204로 끝난다(멱등).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료"),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> unlikePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "123", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);
}

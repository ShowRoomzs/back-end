package showroomz.api.creator.post.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.post.DTO.PostDto;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - Post", description = "크리에이터 게시글 관리 API")
public interface PostControllerDocs {

    @Operation(
            summary = "게시글 작성",
            description = "크리에이터가 새로운 게시글을 작성합니다. 포스트에 본인 마켓 상품을 등록할 수 있습니다.\n\n" +
                    "- **productIds**: 등록할 상품 ID 목록 (본인 마켓 상품만 가능, 이미지 등록과 둘 중 하나만 가능)\n" +
                    "- **권한:** CREATOR\n" +
                    "- **요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "게시글 작성 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.CreatePostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PostDto.CreatePostResponse> createPost(
            @Valid @RequestBody PostDto.CreatePostRequest request);

    @Operation(
            summary = "게시글 상세 조회",
            description = "크리에이터가 자신의 게시글 상세 정보를 조회합니다.\n\n" +
                    "**권한:** CREATOR\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (다른 크리에이터의 게시글)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PostDto.PostDetailResponse> getPostById(@PathVariable("postId") Long postId);

    @Operation(
            summary = "게시글 목록 조회",
            description = "크리에이터가 자신의 게시글 목록을 조회합니다.\n\n" +
                    "**권한:** CREATOR\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PageResponse<PostDto.PostListItem>> getPostList(PagingRequest pagingRequest);

    @Operation(
            summary = "게시글 수정",
            description = "크리에이터가 자신의 게시글을 수정합니다. 포스트에 등록된 상품 목록을 변경할 수 있습니다.\n\n" +
                    "- **productIds**: 수정할 상품 ID 목록 (제공 시 기존 매핑 제거 후 재등록, 본인 마켓 상품만 가능)\n" +
                    "- **권한:** CREATOR\n" +
                    "- **요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.UpdatePostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (다른 크리에이터의 게시글)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PostDto.UpdatePostResponse> updatePost(
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostDto.UpdatePostRequest request);

    @Operation(
            summary = "게시글 삭제",
            description = "크리에이터가 자신의 게시글을 삭제합니다.\n\n" +
                    "**권한:** CREATOR\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (다른 크리에이터의 게시글)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> deletePost(@PathVariable("postId") Long postId);
}

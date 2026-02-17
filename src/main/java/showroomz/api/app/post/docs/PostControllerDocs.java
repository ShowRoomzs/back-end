package showroomz.api.app.post.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.global.dto.PageResponse;

@Tag(name = "User Post", description = "유저 게시글 조회 및 위시리스트 API")
public interface PostControllerDocs {

    @Operation(
            summary = "게시글 상세 조회",
            description = "게시글의 상세 정보를 조회합니다. 조회 시 조회수가 1 증가합니다.\n\n" +
                    "로그인한 사용자의 경우 위시리스트 여부(isWishlisted)가 포함됩니다.\n" +
                    "비로그인 사용자도 조회 가능하며, 이 경우 isWishlisted는 false로 반환됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId);

    @Operation(
            summary = "게시글 목록 조회",
            description = "전시 중인 게시글 목록을 조회합니다.\n\n" +
                    "로그인한 사용자의 경우 각 게시글의 위시리스트 여부(isWishlisted)가 포함됩니다.\n" +
                    "비로그인 사용자도 조회 가능하며, 이 경우 isWishlisted는 false로 반환됩니다.\n\n" +
                    "marketId를 전달하면 해당 마켓의 게시글만 조회됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))
            )
    })
    ResponseEntity<PageResponse<PostDto.PostListItem>> getPostList(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "limit", required = false) Integer limit,
            @Parameter(description = "마켓 ID (특정 마켓의 게시글만 조회)")
            @RequestParam(value = "marketId", required = false) Long marketId);

    @Operation(
            summary = "게시글 위시리스트 추가",
            description = "게시글을 위시리스트에 추가합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "추가 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 위시리스트에 추가된 게시글",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> addPostToWishlist(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId);

    @Operation(
            summary = "게시글 위시리스트 제거",
            description = "게시글을 위시리스트에서 제거합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "제거 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> removePostFromWishlist(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId);

    @Operation(
            summary = "위시리스트 게시글 목록 조회",
            description = "사용자가 위시리스트에 추가한 게시글 목록을 조회합니다.\n\n" +
                    "**권한:** USER\n" +
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
    ResponseEntity<PageResponse<PostDto.PostListItem>> getWishlistedPosts(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "limit", required = false) Integer limit);
}

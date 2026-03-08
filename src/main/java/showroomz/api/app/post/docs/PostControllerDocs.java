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
                    "- **registeredProducts**: 포스트에 등록된 상품 목록 (상품 ID, 이미지, 마켓명, 상품명, 할인율, 가격, 위시/리뷰 수 등)\n" +
                    "- 로그인한 사용자의 경우 위시리스트 여부(isWishlisted)가 포함됩니다.\n" +
                    "- 비로그인 사용자도 조회 가능하며, 이 경우 isWishlisted는 false로 반환됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PostDto.PostDetailResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "게시글 상세 조회 성공",
                                    value = """
                                            {
                                              "postId": 123,
                                              "showroomId": 10,
                                              "showroomName": "쇼룸 A",
                                              "showroomImageUrl": "https://cdn.example.com/showrooms/10.png",
                                              "title": "신상품 출시 소식",
                                              "content": "이번 주 신상품을 소개합니다.",
                                              "imageUrls": ["https://cdn.example.com/posts/123.jpg"],
                                              "viewCount": 532,
                                              "isWishlisted": true,
                                              "wishlistCount": 12,
                                              "registeredProducts": [
                                                {
                                                  "productId": 1,
                                                  "productImageUrl": "https://cdn.example.com/products/1.jpg",
                                                  "marketName": "쇼룸 A",
                                                  "productName": "프리미엄 린넨 셔츠",
                                                  "discountRate": 10,
                                                  "price": 29900,
                                                  "wishlistCount": 5,
                                                  "reviewCount": 12,
                                                  "isWishlisted": true
                                                }
                                              ],
                                              "createdAt": "2026-03-04T12:34:56",
                                              "modifiedAt": "2026-03-04T13:10:00"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "notFound",
                                    summary = "게시글 미존재",
                                    value = """
                                            {
                                              "code": "POST_NOT_FOUND",
                                              "message": "게시글을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId);

    @Operation(
            summary = "게시글 목록 조회",
            description = "전시 중인 게시글 목록을 조회합니다.\n\n" +
                    "로그인한 사용자의 경우 각 게시글의 위시리스트 여부(isWishlisted)가 포함됩니다.\n" +
                    "비로그인 사용자도 조회 가능하며, 이 경우 isWishlisted는 false로 반환됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "게시글 목록 조회 성공",
                                    value = """
                                            {
                                              "content": [
                                                {
                                                  "contentType": "POST",
                                                  "post": {
                                                    "postId": 123,
                                                    "showroomId": 10,
                                                    "showroomName": "쇼룸 A",
                                                    "showroomImageUrl": "https://cdn.example.com/showrooms/10.png",
                                                    "title": "신상품 출시 소식",
                                                    "imageUrls": ["https://cdn.example.com/posts/123.jpg"],
                                                    "viewCount": 532,
                                                    "isWishlisted": true,
                                                    "wishlistCount": 12,
                                                    "createdAt": "2026-03-04T12:34:56"
                                                  }
                                                },
                                                {
                                                  "contentType": "POST",
                                                  "post": {
                                                    "postId": 122,
                                                    "showroomId": 12,
                                                    "showroomName": "쇼룸 B",
                                                    "showroomImageUrl": "https://cdn.example.com/showrooms/12.png",
                                                    "title": "봄 시즌 할인 안내",
                                                    "imageUrls": ["https://cdn.example.com/posts/122.jpg"],
                                                    "viewCount": 214,
                                                    "isWishlisted": false,
                                                    "wishlistCount": 5,
                                                    "createdAt": "2026-03-03T09:00:00"
                                                  }
                                                }
                                              ],
                                              "pageInfo": {
                                                "currentPage": 1,
                                                "totalPages": 5,
                                                "totalResults": 87,
                                                "limit": 20,
                                                "hasNext": true
                                              }
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostList(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "limit", required = false) Integer limit);

    @Operation(
            summary = "쇼룸별 게시글 목록 조회",
            description = "전시 중인 게시글 목록을 쇼룸 기준으로 조회합니다.\n\n" +
                    "로그인한 사용자의 경우 각 게시글의 위시리스트 여부(isWishlisted)가 포함됩니다.\n" +
                    "비로그인 사용자도 조회 가능하며, 이 경우 isWishlisted는 false로 반환됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "쇼룸별 게시글 목록 조회 성공",
                                    value = """
                                            {
                                              "content": [
                                                {
                                                  "contentType": "POST",
                                                  "post": {
                                                    "postId": 123,
                                                    "showroomId": 10,
                                                    "showroomName": "쇼룸 A",
                                                    "showroomImageUrl": "https://cdn.example.com/showrooms/10.png",
                                                    "title": "신상품 출시 소식",
                                                    "imageUrls": ["https://cdn.example.com/posts/123.jpg"],
                                                    "viewCount": 532,
                                                    "isWishlisted": true,
                                                    "wishlistCount": 12,
                                                    "createdAt": "2026-03-04T12:34:56"
                                                  }
                                                }
                                              ],
                                              "pageInfo": {
                                                "currentPage": 1,
                                                "totalPages": 2,
                                                "totalResults": 21,
                                                "limit": 20,
                                                "hasNext": true
                                              }
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostListByShowroom(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸 ID", example = "1")
            @PathVariable Long showroomId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "limit", required = false) Integer limit);

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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "위시리스트 게시글 목록 조회 성공",
                                    value = """
                                            {
                                              "content": [
                                                {
                                                  "contentType": "POST",
                                                  "post": {
                                                    "postId": 123,
                                                    "showroomId": 10,
                                                    "showroomName": "쇼룸 A",
                                                    "showroomImageUrl": "https://cdn.example.com/showrooms/10.png",
                                                    "title": "신상품 출시 소식",
                                                    "imageUrls": ["https://cdn.example.com/posts/123.jpg"],
                                                    "viewCount": 532,
                                                    "isWishlisted": true,
                                                    "wishlistCount": 12,
                                                    "createdAt": "2026-03-04T12:34:56"
                                                  }
                                                }
                                              ],
                                              "pageInfo": {
                                                "currentPage": 1,
                                                "totalPages": 1,
                                                "totalResults": 1,
                                                "limit": 20,
                                                "hasNext": false
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getWishlistedPosts(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "limit", required = false) Integer limit);
}

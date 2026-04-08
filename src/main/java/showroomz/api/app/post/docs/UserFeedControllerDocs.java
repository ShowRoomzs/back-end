package showroomz.api.app.post.docs;

import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "User Post", description = "유저 게시글 조회 및 위시리스트 API")
public interface UserFeedControllerDocs {

    @Operation(
            summary = "팔로잉 피드 조회",
            description = "사용자가 팔로우하는 쇼룸들의 전시 중인 게시글 목록을 최신순으로 조회합니다.\n\n" +
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
                                    summary = "팔로잉 피드 조회 성공",
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
                                                    "createdAt": "2026-03-04T12:34:56"
                                                  }
                                                }
                                              ],
                                              "pageInfo": {
                                                "currentPage": 1,
                                                "totalPages": 2,
                                                "totalResults": 21,
                                                "size": 20,
                                                "hasNext": true
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
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getFollowingFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            PagingRequest pagingRequest);

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
                                                    "createdAt": "2026-03-04T12:34:56"
                                                  }
                                                }
                                              ],
                                              "pageInfo": {
                                                "currentPage": 1,
                                                "totalPages": 1,
                                                "totalResults": 1,
                                                "size": 20,
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
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            PagingRequest pagingRequest);
}


package showroomz.api.creator.post.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
                    "- **productIds**: 등록할 상품 ID 목록 (본인 마켓 상품만 가능, 이미지 등록과 둘 중 하나만 가능, 현재는 크리에이터도 셀러 상품 등록 API를 통해 상품 등록 가능합니다. 추후 변경될 수 있습니다.)\n" +
                    "- **권한:** CREATOR\n" +
                    "- **요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "게시글 작성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PostDto.CreatePostResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "게시글 작성 성공",
                                    value = """
                                            {
                                              "postId": 1
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "validationError",
                                    summary = "제목 누락",
                                    value = """
                                            {
                                              "code": "INVALID_INPUT",
                                              "message": "제목은 필수입니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (CREATOR 아님 / 본인 쇼룸 상품 또는 굿즈 아님)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "게시글 작성 정보 (이미지 또는 상품 등록 중 택1)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PostDto.CreatePostRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "이미지 게시글",
                                    value = """
                                            {
                                              "title": "신상품 출시 소식",
                                              "content": "이번 주 신상품을 소개합니다.",
                                              "imageUrls": ["https://cdn.example.com/posts/1.jpg"]
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "상품 등록 게시글",
                                    value = """
                                            {
                                              "title": "추천 상품 모음",
                                              "content": "이번 주 베스트 상품입니다.",
                                              "productIds": [101, 102, 103]
                                            }
                                            """
                            )
                    }
            )
    )
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PostDto.PostDetailResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "게시글 상세 조회 성공",
                                    value = """
                                            {
                                              "postId": 1,
                                              "marketId": 1,
                                              "marketName": "쇼룸 A",
                                              "title": "신상품 출시 소식",
                                              "content": "이번 주 신상품을 소개합니다.",
                                              "imageUrls": ["https://cdn.example.com/posts/1.jpg"],
                                              "viewCount": 10,
                                              "wishlistCount": 0,
                                              "isDisplay": true,
                                              "registeredProducts": [
                                                {
                                                  "productId": 101,
                                                  "productImageUrl": "https://cdn.example.com/products/101.jpg",
                                                  "marketName": "쇼룸 A",
                                                  "productName": "프리미엄 린넨 셔츠",
                                                  "discountRate": 10,
                                                  "price": 29900,
                                                  "wishlistCount": 5,
                                                  "reviewCount": 12,
                                                  "isWishlisted": false
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
                    responseCode = "403",
                    description = "권한 없음 (다른 크리에이터의 게시글 / 본인 쇼룸 상품 또는 굿즈 아님)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @Parameter(description = "게시글 ID", required = true, example = "1", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

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
                                                  "postId": 1,
                                                  "title": "신상품 출시 소식",
                                                  "imageUrls": ["https://cdn.example.com/posts/1.jpg"],
                                                  "viewCount": 10,
                                                  "wishlistCount": 0,
                                                  "isDisplay": true,
                                                  "createdAt": "2026-03-04T12:34:56"
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
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (CREATOR 아님)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<PageResponse<PostDto.PostListItem>> getPostList(
            @Parameter(description = "페이징 정보 (page: 1부터 시작, size: 기본 20)")
            PagingRequest pagingRequest);

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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PostDto.UpdatePostResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "게시글 수정 성공",
                                    value = """
                                            {
                                              "postId": 1
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "validationError",
                                    summary = "제목 길이 초과",
                                    value = """
                                            {
                                              "code": "INVALID_INPUT",
                                              "message": "제목은 최대 200자입니다."
                                            }
                                            """
                            )
                    )
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "게시글 수정 정보 (수정할 필드만 제공)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PostDto.UpdatePostRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "이미지 게시글 수정",
                                    summary = "이미지 URL 목록으로 수정",
                                    value = """
                                            {
                                              "title": "수정된 제목",
                                              "content": "수정된 본문",
                                              "imageUrls": [
                                                "https://cdn.example.com/posts/1-1.jpg",
                                                "https://cdn.example.com/posts/1-2.jpg"
                                              ],
                                              "isDisplay": true
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "상품 등록 게시글 수정",
                                    summary = "상품 ID 목록으로 매핑 변경",
                                    value = """
                                            {
                                              "title": "수정된 제목",
                                              "content": "수정된 본문",
                                              "productIds": [101, 102],
                                              "isDisplay": true
                                            }
                                            """
                            )
                    }
            )
    )
    ResponseEntity<PostDto.UpdatePostResponse> updatePost(
            @Parameter(description = "게시글 ID", required = true, example = "1", in = ParameterIn.PATH)
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
    ResponseEntity<Void> deletePost(
            @Parameter(description = "게시글 ID", required = true, example = "1", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);
}

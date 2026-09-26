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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "User - Post", description = "소비자 쇼룸 게시물 조회 API")
public interface PostControllerDocs {

    @Operation(
            summary = "게시글 상세 조회",
            description = """
                    게시중인 게시물만 조회된다. 작성중·노출 중지·삭제는 소비자에게 404다.

                    - **contentType** — `GENERAL` / `GROUP_BUY`. 공구 게시물이면 **groupBuy** 블록이 채워지고
                      사진이 없어 `imageUrls: []` · `aspectRatio: null`이다
                    - **aspectRatio** — 게시물 비율(가로/세로). 게시물마다 높이가 다르므로
                      **고정 높이 카드로 그리면 안 되고** 이 값으로 자리를 잡는다(§24-2)
                    - **imageUrls** — 배열 순서가 노출 순서, 첫 장이 대표 사진
                    - **likeLocked** — `true`면 마감된 공구다. 좋아요 버튼을 눌러도 새로 걸리지
                      않고 해제만 된다(C3 §마감·품절과 같은 규칙)
                    - 비로그인도 조회할 수 있고, 이 경우 `isLiked`는 false다

                    **공구 게시물 (C5)**
                    - 끝난 공구의 게시물은 **종료 후 3일(72시간)까지** `saleState: CLOSED`로 열리고, 그 뒤 404다.
                      직권 중단된 공구의 게시물은 즉시 404다
                    - 상세는 마감이어도 `products`를 **전부** 내린다 — 행 상태가 모두 `CLOSED`라 흑백 + 「공구 마감」으로 그린다
                    - `saleState` — `ON_SALE` · `PARTIALLY_SOLD_OUT`(배지 D-day 유지, 품절 행만 흑백) · `SOLD_OUT`(「품절」) · `CLOSED`
                    - `dDay` — KST 날짜 차이. 마감 당일 0, `CLOSED`면 null. 시분 단위가 필요하면 `endAt`
                    - `products[].detailAvailable` — `false`면 C7로 보내지 않는다(마감 · 미진열 · 공구 연결 해제)
                    - `adDisclosure` — 유료 광고 포함 배지와 대가관계 전문. 공구 게시물에 항상 붙는다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostDetailResponse.class),
                            examples = {@ExampleObject(name = "일반 게시물", value = """
                                    {
                                      "contentType": "GENERAL",
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
                                      "likeLocked": false,
                                      "publishedAt": "2026-03-04T12:34:56",
                                      "modifiedAt": "2026-03-04T13:00:00",
                                      "groupBuy": null
                                    }
                                    """),
                                    @ExampleObject(name = "공구 게시물", value = """
                                    {
                                      "contentType": "GROUP_BUY",
                                      "postId": 123,
                                      "showroomId": 5,
                                      "showroomName": "제니의 뷰티룸",
                                      "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
                                      "content": "제가 두 달 동안 …",
                                      "imageUrls": [],
                                      "imageCount": 0,
                                      "aspectRatio": null,
                                      "impressionCount": 1204,
                                      "isLiked": false,
                                      "likeCount": 24,
                                      "likeLocked": false,
                                      "publishedAt": "2026-09-24T10:00:00",
                                      "modifiedAt": "2026-09-24T10:00:00",
                                      "groupBuy": {
                                        "groupBuyId": 77,
                                        "title": "여름 끝 무너진 장벽, 3주면 돌아옵니다",
                                        "saleState": "PARTIALLY_SOLD_OUT",
                                        "dDay": 3,
                                        "endAt": "2026-09-29T23:59:59",
                                        "adDisclosure": {
                                          "label": "유료 광고 포함",
                                          "text": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다"
                                        },
                                        "productCount": 2,
                                        "products": [
                                          {
                                            "productId": 901,
                                            "name": "시카 리페어 앰플 30ml 리필 2개 세트 기획",
                                            "thumbnailUrl": "https://cdn.example.com/products/901.jpg",
                                            "regularPrice": 38000,
                                            "groupBuyPrice": 24900,
                                            "discountRate": 34,
                                            "state": "ON_SALE",
                                            "detailAvailable": true
                                          },
                                          {
                                            "productId": 902,
                                            "name": "시카 토너 200ml",
                                            "thumbnailUrl": "https://cdn.example.com/products/902.jpg",
                                            "regularPrice": 22000,
                                            "groupBuyPrice": 15900,
                                            "discountRate": 28,
                                            "state": "SOLD_OUT",
                                            "detailAvailable": true
                                          }
                                        ]
                                      }
                                    }
                                    """)})),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없거나 게시중이 아님 (공구는 종료 3일 경과 · 직권 중단 포함)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "123", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(summary = "전체 게시글 목록 조회 (내부용)",
            description = """
                    게시중인 게시물을 최신순으로 조회한다. 일반 게시물과 **진행 중인** 공구 게시물이 섞인다(C1과 같은 규칙).
                    비로그인도 조회할 수 있고, 이 경우 `isLiked`·`isFollowing`은 false다.

                    경로가 `GET /v1/user/showrooms` → `GET /v1/user/showrooms/posts`로 옮겨졌다 —
                    앞자리는 쇼룸 목록 조회가 쓴다.
                    """)
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)") PagingRequest pagingRequest);

    @Operation(
            summary = "쇼룸별 게시글 목록 조회",
            description = """
                    C4 쇼룸의 **아래 피드** — 한 쇼룸의 게시물을 최신순으로 조회한다. 게시중인 것만 나온다.

                    일반 게시물과 **마감된 공구 게시물(종료 후 3일 이내)**이 섞인다. 진행 중인 공구는
                    고정 섹션(`GET /v1/user/showrooms/{showroomId}/group-buy-posts`)이 따로 그리므로 여기서 뺀다 —
                    같은 게시물이 두 번 뜨지 않는다.

                    **contentType**이 게시물 종류 판별자다 — `GENERAL` / `GROUP_BUY`. 공구 게시물은 `post.groupBuy`가
                    채워지고, 마감(`saleState: CLOSED`)이라 `products`는 `[]`다(글만 표시, `productCount`는 실제 개수).

                    - **isFollowing** — 이 쇼룸을 지금 팔로우 중인지. 조회 대상 쇼룸이 하나로 고정돼
                      있어 목록 전체가 같은 값이다
                    - **hasOngoingGroupBuy** — 이 쇼룸이 진행 중인 공구를 가졌는지. 쇼룸 안(C4)에서는
                      모든 카드가 같은 쇼룸이라 아바타 링을 그리지 않는다 — 값은 프로필 영역이 쓴다
                    - **likeLocked** — `true`면 마감된 공구다. 새 좋아요는 서버가 거절하고 해제만 된다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "content": [
                                {
                                  "contentType": "GENERAL",
                                  "post": {
                                    "postId": 123,
                                    "showroomId": 10,
                                    "showroomName": "리브의 방",
                                    "showroomImageUrl": "https://cdn.example.com/showrooms/10.jpg",
                                    "isFollowing": false,
                                    "hasOngoingGroupBuy": false,
                                    "content": "3주 루틴 기록",
                                    "imageUrls": ["https://cdn.example.com/posts/123-0.jpg"],
                                    "imageCount": 1,
                                    "aspectRatio": 0.8000,
                                    "impressionCount": 532,
                                    "isLiked": false,
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
                            """)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostListByShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸 ID", required = true, example = "10", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId,
            @Parameter(description = "페이징 정보")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "쇼룸의 진행 중인 공구 게시물 (C4 고정 섹션)",
            description = """
                    C4 상단 **「진행 중인 공구」 고정 섹션**. 이 쇼룸의 진행 중 공구 게시물을 전부 내린다 —
                    페이징 없음, 공구 시작일 최신순.

                    - **빈 배열이면 섹션 자체를 감춘다**(C4 1c)
                    - 진행 중 = 게시중 ∧ 공구 판매 중 ∧ 종료 시각 전. 쇼룸 프로필의 `hasOngoingGroupBuy`(아바타 로즈 링)와
                      **같은 정의**라 링이 켜졌는데 섹션이 비는 일이 없다
                    - 마감된 공구 게시물은 여기 없고 아래 피드(`GET /{showroomId}/posts`)에 남는다(종료 후 3일)
                    - 각 항목의 `post.groupBuy.products`는 전부 실려 있다 — 대표 1개를 그리고 「상품 N개 더보기」는 추가 호출 없이 펼친다
                    - 비로그인도 조회할 수 있다(SNS 링크로 착지하는 화면)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 진행 중 공구가 없으면 빈 배열",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                            [
                              {
                                "contentType": "GROUP_BUY",
                                "post": {
                                  "postId": 123,
                                  "showroomId": 5,
                                  "showroomName": "제니의 뷰티룸",
                                  "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
                                  "isFollowing": true,
                                  "hasOngoingGroupBuy": true,
                                  "content": "제가 두 달 동안 …",
                                  "imageUrls": [],
                                  "imageCount": 0,
                                  "aspectRatio": null,
                                  "impressionCount": 1204,
                                  "isLiked": false,
                                  "likeCount": 24,
                                  "likeLocked": false,
                                  "publishedAt": "2026-09-24T10:00:00",
                                  "groupBuy": {
                                    "groupBuyId": 77,
                                    "title": "여름 끝 무너진 장벽, 3주면 돌아옵니다",
                                    "saleState": "ON_SALE",
                                    "dDay": 3,
                                    "endAt": "2026-09-29T23:59:59",
                                    "adDisclosure": {
                                      "label": "유료 광고 포함",
                                      "text": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다"
                                    },
                                    "productCount": 1,
                                    "products": [
                                      {
                                        "productId": 901,
                                        "name": "시카 리페어 앰플 30ml 리필 2개 세트 기획",
                                        "thumbnailUrl": "https://cdn.example.com/products/901.jpg",
                                        "regularPrice": 38000,
                                        "groupBuyPrice": 24900,
                                        "discountRate": 34,
                                        "state": "ON_SALE",
                                        "detailAvailable": true
                                      }
                                    ]
                                  }
                                }
                              }
                            ]
                            """))),
            @ApiResponse(responseCode = "404", description = "없거나 노출할 수 없는 쇼룸 (SHOWROOM_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<PostDto.FeedItemResponse>> getOngoingGroupBuyPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸 ID", required = true, example = "5", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId);

    @Operation(
            summary = "게시글 좋아요",
            description = """
                    이미 눌러 뒀으면 아무 일도 일어나지 않고 204로 끝난다(멱등). 게시중이 아닌 게시물에는 누를 수 없다.

                    목록 응답의 `likeLocked=true`인 게시물(마감된 공구 — 종료 시각이 지난 순간부터)도 거절한다 — 해제만 가능하다.
                    품절은 막지 않는다. 재입고·다음 공구로 되살아날 수 있다(C3 §마감·품절).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "좋아요 완료"),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없거나, 게시중이 아니거나, 좋아요가 막힌 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> likePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "123", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(
            summary = "게시글 좋아요 취소",
            description = """
                    누른 적이 없으면 그대로 204로 끝난다(멱등).
                    마감된 공구도 취소는 언제나 허용한다 — 막는 것은 새 좋아요뿐이다.
                    """
    )
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

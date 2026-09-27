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

import static showroomz.api.app.post.docs.PostDocsExamples.*;

@Tag(name = "User - Post", description = """
        소비자 쇼룸 게시물 조회·좋아요 API. 일반 게시물과 **공구 게시물**이 같은 응답 구조로 나간다.

        **게시물 종류** — `contentType`이 판별자다.
        - `GENERAL` 일반 게시물 — 사진(`imageUrls` · `aspectRatio`) + 본문. `groupBuy: null`
        - `GROUP_BUY` 공구 게시물 — **사진이 없다**(`imageUrls: []` · `imageCount: 0` · `aspectRatio: null`).
          제목·판매 상태·상품은 `groupBuy` 블록에 있고 본문은 `content`다

        **공구 게시물이 소비자에게 보이는 기간**
        - 공구가 **오픈(진행중)되는 순간** 게시된다 — 인플루언서 제출 → 운영자 승인 → 시작 시각 도달. 그 전(작성중·승인대기·예약)에는 404다
        - 진행 중에 운영자가 **숨기면** 즉시 내려가고(404 · 목록에서 빠짐), 해제하면 다시 나온다
        - 운영자 직권 중단 **예고(중단 예정)** 중에는 그대로 판매·노출된다
        - 공구가 **끝나면(기간 종료·조기 마감·요청 중단) 종료 후 3일(72시간)까지** 마감 상태(`saleState: CLOSED`)로 남고, 그 뒤 404다.
          **운영자 직권 중단**으로 끝나면 즉시 404다. 숨긴 채 끝난 게시물은 다시 나오지 않는다
        - 인플루언서가 승인 후 본문을 고치면 재승인 없이 **즉시** 반영된다

        **목록별로 싣는 공구 게시물** — 모든 목록은 게시중 게시물만 싣고, 공구 게시물 범위만 다르다.

        | 목록 | 일반 | 진행 중 공구 | 마감 공구(3일 이내) |
        |---|---|---|---|
        | C1 팔로잉 · 추천 피드, 전체 목록 | O | O | X |
        | C4 쇼룸 고정 섹션(`/{showroomId}/group-buy-posts`) | X | O | X |
        | C4 쇼룸 아래 피드(`/{showroomId}/posts`) | O | X | O |
        | C3 좋아요 목록 | O | O | O |
        | C5 상세 | O | O | O |

        진행 중 = 게시중 ∧ 공구 판매 중(진행중·중단 예정) ∧ 종료 시각 전. 아바타 로즈 링(`hasOngoingGroupBuy`)도 **같은 정의**다.

        **공구 블록(`groupBuy`) 읽는 법**
        - `saleState` — 저장값이 아니라 조회 시점에 파생한다. 배지 문구는 앱이 정한다(C3 「공구 종료」 / C5 「공구 마감」처럼 화면마다 다르다).

          | 값 | 조건 | 배지 | 상품 행 |
          |---|---|---|---|
          | `ON_SALE` | 판매 중 · 품절 없음 | 공동구매 D-n | 전부 `ON_SALE` |
          | `PARTIALLY_SOLD_OUT` | 판매 중 · 일부 품절 | 공동구매 D-n | 품절 행만 `SOLD_OUT`(흑백) |
          | `SOLD_OUT` | 판매 중 · 전부 품절 | 품절 | 전부 `SOLD_OUT` |
          | `CLOSED` | 공구 종결 또는 종료 시각 경과 | 공구 마감 | 전부 `CLOSED` |

        - `dDay` — KST 날짜 차이(마감 당일 0). `CLOSED`면 `null`. 시·분이 필요하면 `endAt`. 앱 시계로 다시 계산하지 않는다
        - `products` — 계약 상품 순서. **목록은 마감이면 `[]`**(글만 표시), **상세는 마감이어도 전부**(흑백 + 「공구 마감」). `productCount`는 항상 실제 개수다
        - 상품명·정가·공구가는 **계약 시점 값**이고, 썸네일·품절·상세 진입 가능 여부만 현재 상품에서 읽는다
        - `discountRate` = round((정가 − 공구가) ÷ 정가 × 100), 0~100. 정가가 없으면 0
        - `detailAvailable` — `false`면 C7 상품 상세로 보내지 않는다(마감 · 미진열 · 공구 연결 해제)
        - `adDisclosure` — 「유료 광고 포함」 배지(`label`)와 대가관계 전문(`text`). 공구 게시물에 **항상** 붙는다

        **좋아요** — `likeLocked = true`(마감된 공구)면 새 좋아요는 404로 거절되고 해제만 된다. 품절은 막지 않는다.
        """)
public interface PostControllerDocs {

    @Operation(
            summary = "게시글 상세 조회 (C5)",
            description = """
                    게시중인 게시물 한 건을 조회한다. 작성중·노출 중지·삭제는 소비자에게 **404**다(상태를 구분해 알려주지 않는다).

                    **권한:** 없음(비로그인 허용). 토큰을 보내면 `isLiked`가 실제 값으로 채워지고, 없으면 `false`다.
                    조회수를 올리지 않는다 — 노출은 `POST /v1/user/posts/impressions`가 따로 적재한다.

                    **공통 필드**
                    - `contentType` — `GENERAL` / `GROUP_BUY`
                    - `aspectRatio` — 게시물 비율(가로/세로, 1.9100 ~ 0.8000). 게시물마다 높이가 다르므로 **고정 높이 카드로 그리면 안 되고** 이 값으로 자리를 잡는다(§24-2).
                      공구 게시물은 사진이 없어 `null`
                    - `imageUrls` — 배열 순서가 노출 순서, 첫 장이 대표 사진. 공구 게시물은 `[]`
                    - `likeLocked` — `true`면 마감된 공구다. 하트를 눌러도 새로 걸리지 않고 해제만 된다(C3 §마감·품절과 같은 규칙)
                    - `publishedAt` — 처음 게시된 시각. 공구 게시물은 **공구가 오픈된 시각**이다(숨김·해제로 바뀌지 않는다)
                    - `modifiedAt` — 게시물 행의 마지막 변경 시각이다. 본문 수정뿐 아니라 좋아요 수·노출 상태 변경에도 갱신되므로
                      「수정됨」 표시의 근거로 쓰지 않는다

                    **공구 게시물 (`contentType = GROUP_BUY`)**
                    - 진행 중이면 `groupBuy.saleState`가 `ON_SALE` · `PARTIALLY_SOLD_OUT` · `SOLD_OUT` 중 하나이고 `dDay`가 채워진다.
                    - 끝난 공구는 **종료 후 3일(72시간)까지** `saleState: CLOSED` · `likeLocked: true`로 열리고, 그 뒤 404다. 직권 중단은 즉시 404다.
                    - 상세는 마감이어도 `products`를 **전부** 내린다 — 행 상태가 모두 `CLOSED`이고 `detailAvailable: false`라 흑백 + 「공구 마감」으로 그린다.
                    - 상품 행 탭 → `detailAvailable = true`일 때만 C7(`productId`)로 이동한다.
                    - `groupBuy.title`이 게시물 제목이다(일반 게시물에는 제목이 없다).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostDetailResponse.class), examples = {
                            @ExampleObject(name = "일반 게시물", value = DETAIL_GENERAL),
                            @ExampleObject(name = "공구 게시물 · 진행 중(일부 품절)",
                                    summary = "saleState PARTIALLY_SOLD_OUT · 품절 행만 SOLD_OUT", value = DETAIL_GROUP_BUY_ON_SALE),
                            @ExampleObject(name = "공구 게시물 · 마감(종료 3일 이내)",
                                    summary = "saleState CLOSED · 상품 전부 CLOSED · likeLocked", value = DETAIL_GROUP_BUY_CLOSED)
                    })),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `postId`가 숫자가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND — 없는 게시물 · 게시중 아님(작성중·노출 중지·삭제) · "
                    + "공구 게시물의 오픈 전·숨김·종료 3일 경과·직권 중단",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_POST_NOT_FOUND)))
    })
    ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "456", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(summary = "전체 게시글 목록 조회 (내부용)",
            description = """
                    게시중인 게시물을 게시 시각 최신순으로 조회한다. 일반 게시물과 **진행 중인** 공구 게시물이 섞인다(C1과 같은 규칙) —
                    마감된 공구 게시물은 싣지 않는다.

                    **권한:** 없음(비로그인 허용). 비로그인이면 `isLiked` · `isFollowing`은 `false`다.

                    - 진행 중 공구 항목은 `post.groupBuy.products`가 **전부** 실려 있다
                    - 경로가 `GET /v1/user/showrooms` → `GET /v1/user/showrooms/posts`로 옮겨졌다 — 앞자리는 쇼룸 목록 조회가 쓴다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "일반 + 진행 중 공구", value = PAGE_GENERAL_AND_ONGOING),
                            @ExampleObject(name = "게시물 없음", value = PAGE_EMPTY)
                    }))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)") PagingRequest pagingRequest);

    @Operation(
            summary = "쇼룸별 게시글 목록 조회 (C4 아래 피드)",
            description = """
                    C4 쇼룸의 **아래 피드** — 한 쇼룸의 게시중 게시물을 게시 시각 최신순으로 조회한다.

                    **권한:** 없음(비로그인 허용 — SNS 링크로 착지하는 화면). 비로그인이면 `isLiked` · `isFollowing`은 `false`다.

                    **싣는 것** — 일반 게시물 + **마감된 공구 게시물(종료 후 3일 이내)**.
                    진행 중인 공구는 상단 고정 섹션(`GET /v1/user/showrooms/{showroomId}/group-buy-posts`)이 따로 그리므로 **여기서 뺀다** —
                    같은 게시물이 두 번 뜨지 않는다. 공구가 끝나면 그 게시물은 고정 섹션에서 이 피드로 내려오고, 3일 뒤 사라진다.

                    **필드**
                    - `contentType` — `GENERAL` / `GROUP_BUY`. 여기 실리는 공구 게시물은 항상 마감이라
                      `post.groupBuy.saleState: CLOSED` · `products: []`(글만 표시) · `likeLocked: true`다. `productCount`는 실제 개수다
                    - `isFollowing` — 이 쇼룸을 지금 팔로우 중인지. 조회 대상 쇼룸이 하나라 목록 전체가 같은 값이다
                    - `hasOngoingGroupBuy` — 이 쇼룸이 진행 중인 공구를 가졌는지. C4 안에서는 모든 카드가 같은 쇼룸이라 아바타 링을 그리지 않는다 — 값은 프로필 영역이 쓴다

                    **존재하지 않는 쇼룸** — 에러가 아니라 빈 페이지(`content: []`)가 온다. 쇼룸 자체의 404는 쇼룸 프로필·고정 섹션 API가 판단한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "일반 + 마감 공구", summary = "마감 공구는 products [] · likeLocked true",
                                    value = PAGE_SHOWROOM),
                            @ExampleObject(name = "게시물 없음", value = PAGE_EMPTY)
                    })),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `showroomId`가 숫자가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_INPUT)))
    })
    ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostListByShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸 ID", required = true, example = "5", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId,
            @Parameter(description = "페이징 정보")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "쇼룸의 진행 중인 공구 게시물 (C4 고정 섹션)",
            description = """
                    C4 상단 **「진행 중인 공구」 고정 섹션**. 이 쇼룸의 진행 중 공구 게시물을 **전부** 내린다 —
                    페이징 없음(배열 그대로), **공구 오픈 시각 최신순**(동률은 게시물 id 내림차순).

                    **권한:** 없음(비로그인 허용 — SNS 링크로 착지하는 화면). 비로그인이면 `isLiked` · `isFollowing`은 `false`다.

                    - **빈 배열이면 섹션 자체를 감춘다**(C4 1c)
                    - 진행 중 = 게시중 ∧ 공구 판매 중(진행중 · 중단 예정) ∧ 종료 시각 전. 쇼룸 프로필의 `hasOngoingGroupBuy`(아바타 로즈 링)와
                      **같은 정의**라 링이 켜졌는데 섹션이 비는 일이 없다
                    - 모든 항목이 `contentType: GROUP_BUY`이고 `saleState`는 `ON_SALE` · `PARTIALLY_SOLD_OUT` · `SOLD_OUT` 중 하나다(`CLOSED`는 오지 않는다)
                    - 각 항목의 `post.groupBuy.products`는 **전부** 실려 있다 — 대표 1개를 그리고 「상품 N개 더보기」는 추가 호출 없이 펼친다
                    - 종료 시각이 지나면 다음 조회부터 이 섹션에서 빠지고 아래 피드(`GET /{showroomId}/posts`)에 마감 게시물로 남는다(종료 후 3일)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 진행 중 공구가 없으면 빈 배열",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "진행 중 공구 있음", value = LIST_ONGOING),
                            @ExampleObject(name = "진행 중 공구 없음(섹션 감춤)", value = "[]")
                    })),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `showroomId`가 숫자가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "404", description = "SHOWROOM_NOT_FOUND — 없거나 노출할 수 없는 쇼룸(쇼룸 프로필 조회와 같은 조건)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_SHOWROOM_NOT_FOUND)))
    })
    ResponseEntity<List<PostDto.FeedItemResponse>> getOngoingGroupBuyPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸 ID", required = true, example = "5", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId);

    @Operation(
            summary = "게시글 좋아요",
            description = """
                    게시물에 좋아요를 누른다. 이미 눌러 뒀으면 아무 일도 일어나지 않고 204로 끝난다(멱등).

                    **권한:** USER (비로그인은 401 — 앱은 하트를 누르는 순간 로그인을 유도한다)

                    **누를 수 있는 게시물** — 게시물 종류별 규칙이다.
                    - 일반 게시물 — 게시중이면 된다
                    - 공구 게시물 — **노출중이고 종료 시각 전**일 때만. 마감된 공구(`likeLocked = true` — 종료 시각이 지난 순간부터)는 거절한다.
                      품절은 막지 않는다 — 재입고·다음 공구로 되살아날 수 있다(C3 §마감·품절)

                    거절은 **404 `POST_NOT_FOUND`**로 온다(없는 게시물과 구분하지 않는다). 앱은 `likeLocked = true`면 하트를 회색으로 낮춰 요청 자체를 보내지 않는다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "좋아요 완료(이미 눌렀어도 204)"),
            @ApiResponse(responseCode = "401", description = "비로그인",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND — 없는 게시물 · 게시중 아님 · 마감된 공구(`likeLocked`) / "
                    + "USER_NOT_FOUND — 토큰의 사용자 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "없는 게시물 · 마감된 공구", value = ERR_POST_NOT_FOUND),
                            @ExampleObject(name = "토큰의 사용자 없음", value = ERR_USER_NOT_FOUND)
                    }))
    })
    ResponseEntity<Void> likePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "456", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(
            summary = "게시글 좋아요 취소",
            description = """
                    좋아요를 해제한다. 누른 적이 없으면 그대로 204로 끝난다(멱등).

                    **권한:** USER

                    **언제나 해제할 수 있다** — 마감된 공구(`likeLocked = true`)도, 그 사이 내려간 게시물도 해제는 허용한다. 막는 것은 새 좋아요뿐이다.
                    C3 좋아요 목록에서 해제해도 목록 응답이 바로 바뀌지는 않는다(다음 조회 때 빠진다).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료(누른 적이 없어도 204)"),
            @ApiResponse(responseCode = "401", description = "비로그인",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND — 게시물 자체가 없음 / USER_NOT_FOUND — 토큰의 사용자 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "없는 게시물", value = ERR_POST_NOT_FOUND),
                            @ExampleObject(name = "토큰의 사용자 없음", value = ERR_USER_NOT_FOUND)
                    }))
    })
    ResponseEntity<Void> unlikePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시글 ID", required = true, example = "456", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);
}

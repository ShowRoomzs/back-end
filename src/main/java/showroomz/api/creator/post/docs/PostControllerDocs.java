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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.post.DTO.PostDto;
import showroomz.api.creator.post.DTO.PostInsightDto;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.domain.post.type.PostStatus;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - Post", description = "쇼룸 스튜디오 게시물 관리 API (§24)")
public interface PostControllerDocs {

    @Operation(
            summary = "게시물 작성",
            description = """
                    사진과 본문으로 게시물을 만든다. **제목은 없다**(§24-3).

                    - **action** — `DRAFT`(임시저장) 또는 `PUBLISH`(게시하기)
                      - 임시저장: 사진 1장 **또는** 본문 1자 이상
                      - 게시하기: 사진 **최소 1장**
                    - **images** — 배열 순서가 곧 노출 순서이고 첫 장이 대표 사진이다. 최대 20장
                    - **비율** — 첫 사진의 `width/height`가 게시물 비율이 되며 1.91:1 ~ 4:5 범위여야 한다.
                      크롭은 클라이언트가 하고 서버는 결과를 검증만 한다
                    - **originalUrl** — 크롭 전 원본. 반려 후 유예 기간에 본인이 내려받는 용도다(§24-6)
                    - **권한:** CREATOR
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "작성 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostIdResponse.class),
                            examples = @ExampleObject(value = """
                                    { "postId": 301 }
                                    """))),
            @ApiResponse(responseCode = "400", description = "사진 20장 초과 · 본문 2,000자 초과 · 비율 범위 밖 · 게시 시 사진 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음 (CREATOR 아님)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.PostIdResponse> createPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PostDto.SavePostRequest request);

    @Operation(
            summary = "게시물 목록 조회",
            description = """
                    내 게시물을 최신순으로 조회한다. **응답에 상태 탭 건수가 함께 들어 있다**(§24-1) —
                    탭마다 따로 호출하면 요청이 4배가 되고 탭을 옮길 때마다 숫자가 흔들린다.

                    - **status** — 생략하면 전체 탭. `DRAFT` · `PUBLISHED` · `SUSPENDED`
                    - 삭제된 게시물은 어느 탭에도 나오지 않는다(§24-6 — 운영자 콘솔에서만 조회)
                    - 심사 중(`UNDER_REVIEW`)은 화면상 여전히 "노출 중지"로 센다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostPageResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        {
                                          "postId": 301,
                                          "status": "PUBLISHED",
                                          "thumbnailUrl": "https://cdn.example.com/posts/301-0.jpg",
                                          "imageCount": 5,
                                          "contentPreview": "여름 끝 무너진 장벽, 3주 루틴",
                                          "impressionCount": 2840,
                                          "likeCount": 24,
                                          "publishedAt": "2026-08-10T09:12:00",
                                          "createdAt": "2026-08-09T21:30:00",
                                          "appealDeadline": null
                                        }
                                      ],
                                      "pageInfo": {
                                        "currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false
                                      },
                                      "statusCounts": [
                                        { "status": null, "label": "전체", "count": 12 },
                                        { "status": "PUBLISHED", "label": "게시중", "count": 9 },
                                        { "status": "SUSPENDED", "label": "노출 중지", "count": 1 },
                                        { "status": "DRAFT", "label": "작성중", "count": 2 }
                                      ]
                                    }
                                    """)))
    })
    ResponseEntity<PostDto.PostPageResponse> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "상태 탭. 생략하면 전체", example = "PUBLISHED")
            @RequestParam(value = "status", required = false) PostStatus status,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "게시물 상세 조회",
            description = """
                    사진·본문·상태와 함께 **운영자 조치 정보**(사유 · 근거 규정 · 조치 시각 · 처리자 · 기한)를 내려준다(§24-5).

                    - `editable` — 중지·심사 중이면 false. 심사 대상이 도중에 바뀌면 안 된다
                    - `deletable` — 심사 중에만 false. 중지 중 본인 삭제는 허용한다
                    - 삭제된 게시물은 본인에게도 404다(§24-6 — 별도 화면을 두지 않는다)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostDetailResponse.class))),
            @ApiResponse(responseCode = "403", description = "다른 인플루언서의 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.PostDetailResponse> getPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(
            summary = "게시물 수정",
            description = """
                    게시 후에도 **제한 없이** 수정할 수 있다(§24-3 — 공구 게시물의 노출중 잠금과 다르다).
                    단 노출 중지·심사 중에는 409다.

                    - 사진은 **전체 교체**다. 보낸 배열이 곧 최종 상태이고 순서가 곧 노출 순서다
                    - `action: PUBLISH`를 실으면 작성중이던 게시물이 이 수정과 함께 게시된다
                    - 팔로워 알림은 **재발송하지 않는다**(§24-3)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "사진 20장 초과 · 본문 2,000자 초과 · 비율 범위 밖 · 사진·본문 모두 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "다른 인플루언서의 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "노출 중지·심사 중이라 수정 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.PostIdResponse> updatePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostDto.SavePostRequest request);

    @Operation(
            summary = "게시하기 (작성중 → 게시중)",
            description = "임시저장해 둔 게시물을 게시한다. 사진이 최소 1장 있어야 한다(§24-3). 게시 시각은 처음 한 번만 찍힌다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.PostIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "사진이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "다른 인플루언서의 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 게시됐거나 게시할 수 없는 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.PostIdResponse> publish(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(
            summary = "게시물 삭제",
            description = """
                    **되돌릴 수 없다.** 좋아요 수도 함께 사라진다(§24-3 모달 고지).

                    - 인플루언서가 스스로 내리는 방법은 삭제뿐이다 — 자율 숨김이 없다(§24-1)
                    - 노출 중지 중에도 삭제할 수 있다. 다투고 싶지 않은 사람에게 출구가 필요하다
                    - **심사 중에는 409** — 신청 후 도중에 지우면 처리 결과가 붕 뜬다(§24-5)
                    - 서버는 즉시 완전 삭제하지 않고 일정 기간 비공개로 보관한 뒤 파기한다(§24-6)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "다른 인플루언서의 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "심사 중이라 삭제 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(
            summary = "이의 신청",
            description = """
                    노출 중지 조치에 이의를 제기한다. **게시물당 1회**이며 기한 안에서만 가능하다(§24-5).

                    신청하면 상태가 심사 중으로 바뀌고, 그동안 수정·삭제가 모두 막힌다.
                    승인되면 재게시되고 좋아요·인사이트가 그대로 복원되며, 반려되면 영구 삭제된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 접수",
                    content = @Content(schema = @Schema(implementation = PostDto.AppealResponse.class))),
            @ApiResponse(responseCode = "403", description = "다른 인플루언서의 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 신청함 · 기한 경과 · 중지 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.AppealResponse> submitAppeal(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostDto.AppealRequest request);

    @Operation(
            summary = "원본 사진 내려받기 URL 조회",
            description = """
                    반려 통지 후 **유예 기간 동안 본인만** 사진 원본을 받을 수 있다(§24-6).
                    자기 콘텐츠 원본을 잃는 상황이 분쟁으로 이어지기 때문에 크롭본이 아니라 원본을 준다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostDto.OriginalImagesResponse.class))),
            @ApiResponse(responseCode = "403", description = "다른 인플루언서의 게시물이거나, 반려 후 유예 기간이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostDto.OriginalImagesResponse> getOriginalImages(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(
            summary = "게시물 인사이트",
            description = """
                    3단 지표 (§24-7).

                    1. **반응** — 노출 · 좋아요 · 좋아요율(좋아요 ÷ 노출). 노출이 0이면 좋아요율은 `null`이다
                    2. **행동** — 이 게시물을 본 뒤 **24시간 이내**의 쇼룸 방문 · 팔로우.
                       여러 게시물을 봤다면 **마지막에 본 게시물**에 귀속한다
                    3. **본 사람** — 연령대 · 성별. **집계값만** 내려가며 개별 목록은 어떤 화면에도 없다.
                       비로그인 조회는 "미확인"으로 분류된다

                    노출 중지된 게시물은 **중지 시각**이 집계 상한이 된다(`truncatedBySuspension`).
                    매출·구매는 판매 현황(#6) 소관이라 여기 없다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PostInsightDto.PostInsightResponse.class))),
            @ApiResponse(responseCode = "403", description = "다른 인플루언서의 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostInsightDto.PostInsightResponse> getInsights(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Parameter(description = "조회 기간", example = "DAYS_30")
            @RequestParam(value = "period", defaultValue = "DAYS_30") StatsPeriod period);
}

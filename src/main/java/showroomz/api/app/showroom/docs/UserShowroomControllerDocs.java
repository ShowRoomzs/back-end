package showroomz.api.app.showroom.docs;

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
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.ShowroomDetailResponse;
import showroomz.api.app.showroom.DTO.ShowroomListItem;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

/**
 * 구 샵 API({@code GET /v1/user/shops}, {@code GET /v1/user/shops/{shopId}})의 후신이다.
 * 소비자 앱에서 마켓(브랜드)은 더 이상 조회되지 않고 쇼룸만 조회된다.
 */
@Tag(name = "User - Showroom", description = "소비자 쇼룸 조회 API")
public interface UserShowroomControllerDocs {

    @Operation(
            summary = "쇼룸 목록 조회",
            description = """
                    노출 가능한 쇼룸을 신규 등록순으로 조회한다.

                    구 `GET /v1/user/shops`를 대체한다 — **마켓은 조회되지 않는다.** 이에 따라
                    `shopType` 판별자와 대표 카테고리 필터(`mainCategoryId`)가 사라졌다. 카테고리는
                    마켓이 가진 속성이지 쇼룸의 속성이 아니다.

                    **노출 대상** — 등록을 마쳐 쇼룸명·아이디가 확정되고 계정이 정상인 쇼룸

                    - **keyword** — 쇼룸명 또는 아이디(@handle) 부분 일치(대소문자 무시). `@`를 붙여
                      입력해도 아이디에 걸린다. 없으면 전체
                    - **hasOngoingGroupBuy** — 아바타 로즈 링 표시용(진행 중 공구 보유)
                    - **isFollowing** — `false`일 때만 팔로우 버튼을 그린다. 비로그인은 항상 `false`

                    **권한:** 인증 불필요 (비로그인 가능. 토큰이 실려 오면 `isFollowing`이 채워진다)

                    **참고:** C14 쇼룸 검색 화면은 이 API가 아니라 `GET /v1/user/search/showrooms`를
                    쓴다 — 그쪽은 "왜 걸렸는지" 순으로 정렬한다.
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
                                          "showroomId": 12,
                                          "showroomName": "제니의 뷰티룸",
                                          "showroomAddress": "jenny_beautyroom",
                                          "showroomImageUrl": "https://cdn.showroomz.co.kr/showroom/profile/b.jpg",
                                          "introduction": "매일 쓰는 것만 소개합니다",
                                          "hasOngoingGroupBuy": true,
                                          "isFollowing": false
                                        },
                                        {
                                          "showroomId": 18,
                                          "showroomName": "하루 코스메틱",
                                          "showroomAddress": "haru_cosmetic",
                                          "showroomImageUrl": null,
                                          "introduction": null,
                                          "hasOngoingGroupBuy": false,
                                          "isFollowing": true
                                        }
                                      ],
                                      "pageInfo": {
                                        "currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false
                                      }
                                    }
                                    """)))
    })
    ResponseEntity<PageResponse<ShowroomListItem>> getShowrooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(name = "keyword", description = "쇼룸명 또는 아이디(@handle) 검색어 (선택)",
                    example = "제니", in = ParameterIn.QUERY)
            @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "페이징 정보 (page: 1부터, size: 기본 20)")
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "쇼룸 상세 조회 (C4 프로필)",
            description = """
                    C4 쇼룸 화면 상단의 프로필 영역을 채운다 — 아바타(로즈 링) · 쇼룸명 · 아이디 ·
                    게시물 수/팔로워 수 · 소개 한 줄 · 인스타그램 링크.

                    구 `GET /v1/user/shops/{shopId}`를 대체한다. 사라진 필드 — `shopType`(마켓이
                    조회되지 않아 판별자가 필요 없다), 대표 카테고리, `snsLinks` 배열(쇼룸이 소비자에게
                    공개하는 채널은 인스타그램 하나다).

                    - **postCount** — 게시중인 게시물만 센다(작성중·노출 중지·삭제 제외)
                    - **followerCount** — 이 쇼룸을 팔로우한 사용자 수. 크리에이터가 가입 시 신고한
                      SNS 팔로워 수와는 다른 값이다
                    - **hasOngoingGroupBuy** — `false`면 아바타 로즈 링과 진행 중 공구 섹션을 모두 감춘다
                    - **instagramUrl** — 없으면 채널 버튼을 그리지 않는다

                    노출할 수 없는 쇼룸(탈퇴·정지·등록 미완료)은 없는 쇼룸과 같은 404다.

                    **권한:** 인증 불필요 (비로그인 가능. 이때 `isFollowing`은 `false`)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ShowroomDetailResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "showroomId": 12,
                                      "showroomName": "제니의 뷰티룸",
                                      "showroomAddress": "jenny_beautyroom",
                                      "showroomImageUrl": "https://cdn.showroomz.co.kr/showroom/profile/b.jpg",
                                      "introduction": "매일 쓰는 것만 소개합니다",
                                      "instagramUrl": "https://instagram.com/jenny_beautyroom",
                                      "postCount": 48,
                                      "followerCount": 1204,
                                      "hasOngoingGroupBuy": true,
                                      "isFollowing": false
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 노출할 수 없는 쇼룸",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "SHOWROOM_NOT_FOUND", "message": "존재하지 않는 쇼룸입니다." }
                                    """)))
    })
    ResponseEntity<ShowroomDetailResponse> getShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸(크리에이터) ID", required = true, example = "12", in = ParameterIn.PATH)
            @PathVariable("showroomId") Long showroomId);
}

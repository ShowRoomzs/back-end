package showroomz.api.app.home.docs;

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
import showroomz.api.app.home.dto.HomeSummaryResponse;

@Tag(name = "User - Home", description = "C1 홈 화면 API")
public interface HomeControllerDocs {

    @Operation(
            summary = "홈 요약 조회 (C1 헤더 배지 · 빈 상태 분기)",
            description = """
                    홈을 그리기 전에 한 번 부른다. 헤더의 장바구니 배지와, 팔로잉 피드/빈 상태 중
                    무엇을 그릴지가 이 응답 하나로 정해진다.

                    - **cartCount** — 헤더 장바구니 배지. 0이면 배지를 그리지 않고, 99를 넘으면 앱이
                      `99+`로 줄여 적는다(자르는 것은 표기 규칙이라 서버는 실제 수를 준다)
                    - **followingCount** — 0이면 **빈 상태**(팔로우한 쇼룸이 없어요 + 발견 피드),
                      1 이상이면 팔로잉 피드를 그린다.
                      "팔로잉 피드가 비었다"가 아니라 "팔로우한 쇼룸이 없다"가 기준이다 —
                      팔로우는 했는데 아직 새 글이 없는 경우까지 빈 상태로 그리면 안내가 거짓이 된다
                    - **이어지는 호출** — 팔로잉 피드 `GET /v1/user/feed/following`,
                      추천/발견 피드 `GET /v1/user/feed/recommended`
                    - **알림 점** — 헤더의 알림 배지는 아직 이 응답에 없다. 사용자별 알림함 도메인이
                      들어오면 필드가 추가된다
                    - **권한:** USER
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = HomeSummaryResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "cartCount": 3,
                                      "followingCount": 12
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<HomeSummaryResponse> getHomeSummary(@AuthenticationPrincipal UserPrincipal userPrincipal);
}

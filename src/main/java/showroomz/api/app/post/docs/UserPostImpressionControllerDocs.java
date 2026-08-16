package showroomz.api.app.post.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostImpressionRequest;

@Tag(name = "User - Post Impression", description = "게시물 노출 적재 API (§24-7)")
public interface UserPostImpressionControllerDocs {

    @Operation(
            summary = "게시물 노출 적재",
            description = """
                    뷰포트에 들어온 게시물들을 **모아서** 한 번에 보낸다. 카드 한 장마다 호출하면
                    피드 스크롤에서 요청이 폭발한다. 한 요청에 최대 50건까지 처리한다.

                    - **인증 불필요** — 비로그인 조회도 노출에 포함된다. 토큰이 있으면 사용자 기준으로 집계되고
                      연령·성별 표본이 된다(§24-7 ③)
                    - **visitorId** — 비로그인일 때 필수. **쇼룸 방문 기록과 같은 값**을 써야
                      "이 게시물을 보고 한 행동"(§24-7 ②)이 이어진다
                    - 같은 사람이 같은 게시물을 다시 봐도 **30분 안이면 세지 않는다**(쇼룸 방문과 같은 규칙)
                    - 이미 내려간 게시물의 노출은 조용히 버린다 — 화면에 떠 있던 카드가 그 사이 내려간 것뿐이다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "적재 완료(또는 중복이라 건너뜀)"),
            @ApiResponse(responseCode = "400", description = "게시물 ID 없음 · 비로그인인데 visitorId 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "게시물 ID 없음", value = """
                                            { "code": "INVALID_INPUT", "message": "노출된 게시물 ID가 필요합니다." }
                                            """),
                                    @ExampleObject(name = "비로그인인데 visitorId 없음", value = """
                                            { "code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." }
                                            """)
                            }))
    })
    ResponseEntity<Void> recordImpressions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PostImpressionRequest request);
}

package showroomz.api.app.showroom.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.ShowroomVisitRequest;

@Tag(name = "User - Showroom Visit", description = "쇼룸 방문 기록 API (§22-4 쇼룸 현황 지표 원천)")
public interface ShowroomVisitControllerDocs {

    @Operation(
            summary = "쇼룸 방문 기록",
            description = "소비자가 쇼룸 화면을 열 때 한 번 호출합니다. 인플루언서의 **쇼룸 현황**(#8) 지표가 이 로그로 계산됩니다.\n\n" +
                    "**인증 선택** — 비로그인 방문도 쇼룸 도달에 포함되므로 토큰 없이 호출할 수 있습니다.\n" +
                    "- 로그인 방문: 사용자 기준으로 셉니다(같은 사람이 여러 기기로 들어와도 방문자 1명).\n" +
                    "- 비로그인 방문: `visitorId`(디바이스 식별자)가 **필수**입니다. 없으면 방문자 수가 부풀어 400을 돌려줍니다.\n\n" +
                    "**30분 세션** — 같은 방문자가 30분 안에 다시 열면 순방문을 새로 세지 않습니다. " +
                    "이 경우에도 응답은 동일한 204입니다(클라이언트가 중복 호출을 걱정하지 않아도 됩니다).\n\n" +
                    "**소스 값** — 쇼룸 링크에 붙은 `?from=` 값을 그대로 넘깁니다(`ig` · `search` · `post` · `direct`). " +
                    "값이 없거나 모르는 값이면 직접 유입으로 집계되므로, 앱 딥링크가 이 값을 보존하도록 맞춰야 유입 경로가 의미를 갖습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "기록 완료(세션 중복이어서 새로 세지 않은 경우 포함)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "비로그인인데 디바이스 식별자가 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "식별자 누락",
                                    value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 쇼룸",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "쇼룸 없음",
                                    value = "{\"code\": \"SHOWROOM_NOT_FOUND\", \"message\": \"존재하지 않는 쇼룸입니다.\"}"
                            )
                    )
            )
    })
    ResponseEntity<Void> recordVisit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "쇼룸(크리에이터) ID", example = "5") Long showroomId,
            ShowroomVisitRequest request);
}

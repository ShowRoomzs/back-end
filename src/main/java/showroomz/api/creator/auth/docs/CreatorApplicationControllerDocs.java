package showroomz.api.creator.auth.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.auth.DTO.CreatorApplicationRequest;

@Tag(name = "Creator - Application", description = "크리에이터 권한 신청 API")
@SecurityRequirement(name = "Authorization")
public interface CreatorApplicationControllerDocs {

    @Operation(
            summary = "크리에이터 권한 신청",
            description = "로그인한 일반 유저(USER)가 크리에이터 권한을 신청합니다.\n\n" +
                    "**필수 정보:**\n" +
                    "- SNS 플랫폼, 채널 URL, 팔로워 수, 업무 이메일\n" +
                    "- 서비스 이용약관, 운영정책, 개인정보 수집·이용 동의 (true)\n" +
                    "- 마케팅 동의 여부 (선택)\n\n" +
                    "**권한:** ROLE_USER (로그인 필수)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "신청 접수 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 중복 신청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> applyForCreator(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CreatorApplicationRequest request);
}

package showroomz.api.creator.auth.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
                    "- `snsType`: SNS 플랫폼 (INSTAGRAM, TIKTOK, X, YOUTUBE)\n" +
                    "- `channelUrl`: 채널 주소(URL)\n" +
                    "- `followerCount`: 팔로워 수 (0 이상)\n" +
                    "- `businessEmail`: 업무 이메일\n" +
                    "- `agreeTermsOfService`: 서비스 이용약관 동의 (true 필수)\n" +
                    "- `agreeOperationalPolicy`: 서비스 운영정책 동의 (true 필수)\n" +
                    "- `agreePrivacyPolicy`: 개인정보 수집·이용 동의 (true 필수)\n" +
                    "- `agreeMarketingPolicy`: 마케팅 목적 개인정보 수집·이용 동의 (true/false)\n\n" +
                    "**신청 제한:**\n" +
                    "- 이미 CREATOR 권한인 유저는 신청 불가\n" +
                    "- PENDING 상태의 지원서가 이미 존재하면 중복 신청 불가\n\n" +
                    "**권한:** ROLE_USER (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "신청 접수 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류, 중복 신청, 또는 이미 크리에이터",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "필수 약관 미동의",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "중복 신청",
                                            value = "{\"code\": \"DUPLICATE_APPLICATION\", \"message\": \"이미 검수 대기 중인 신청이 있습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "이미 크리에이터",
                                            value = "{\"code\": \"ALREADY_REGISTERED\", \"message\": \"이미 회원가입이 완료된 사용자입니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다.\"}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "크리에이터 권한 신청 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreatorApplicationRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "신청 예시 (마케팅 동의)",
                                    value = "{\n" +
                                            "  \"snsType\": \"INSTAGRAM\",\n" +
                                            "  \"channelUrl\": \"https://instagram.com/my_channel\",\n" +
                                            "  \"followerCount\": 10000,\n" +
                                            "  \"businessEmail\": \"business@creator.com\",\n" +
                                            "  \"agreeTermsOfService\": true,\n" +
                                            "  \"agreeOperationalPolicy\": true,\n" +
                                            "  \"agreePrivacyPolicy\": true,\n" +
                                            "  \"agreeMarketingPolicy\": true\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "신청 예시 (마케팅 미동의)",
                                    value = "{\n" +
                                            "  \"snsType\": \"YOUTUBE\",\n" +
                                            "  \"channelUrl\": \"https://youtube.com/c/my_channel\",\n" +
                                            "  \"followerCount\": 50000,\n" +
                                            "  \"businessEmail\": \"contact@creator.com\",\n" +
                                            "  \"agreeTermsOfService\": true,\n" +
                                            "  \"agreeOperationalPolicy\": true,\n" +
                                            "  \"agreePrivacyPolicy\": true,\n" +
                                            "  \"agreeMarketingPolicy\": false\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> applyForCreator(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreatorApplicationRequest request);
}

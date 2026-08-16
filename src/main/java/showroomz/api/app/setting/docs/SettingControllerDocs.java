package showroomz.api.app.setting.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.setting.DTO.AccountInfoResponse;
import showroomz.api.app.setting.DTO.IdentityReverifyRequest;
import showroomz.api.app.setting.DTO.NotificationSettingRequest;
import showroomz.api.app.setting.DTO.NotificationSettingResponse;

@Tag(name = "User - Settings", description = "C15 설정 API")
public interface SettingControllerDocs {

    @Operation(
            summary = "C15 알림 설정 조회",
            description = "현재 로그인한 사용자의 알림 설정을 조회합니다.\n\n" +
                    "**알림 설정 종류:**\n" +
                    "- `followPostPushAgree`: 팔로우 쇼룸 새 게시물 알림 (기본값: true)\n" +
                    "- `marketingAgree`: 광고성 정보 수신 동의 — 가입 시 [선택] 동의와 같은 값 (기본값: false)\n" +
                    "- `marketingAgreeChangedAt`: 광고성 정보 수신 동의/철회를 마지막으로 바꾼 시각 (없으면 null)\n\n" +
                    "**주문·배송·문의 답변 등 거래 알림은 끌 수 없어 설정 항목으로 내려가지 않습니다.**\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NotificationSettingResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 시",
                                    value = "{\n" +
                                            "  \"followPostPushAgree\": true,\n" +
                                            "  \"marketingAgree\": false,\n" +
                                            "  \"marketingAgreeChangedAt\": \"2026-08-02T14:12:03\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<NotificationSettingResponse> getNotificationSettings();

    @Operation(
            summary = "C15 알림 설정 변경",
            description = "토글을 누를 때마다 호출합니다. 보내지 않은 필드(null)는 변경되지 않습니다.\n\n" +
                    "- `followPostPushAgree`: 팔로우 쇼룸 새 게시물 알림 (선택)\n" +
                    "- `marketingAgree`: 광고성 정보 수신 동의 (선택) — `users.marketingAgree`를 직접 바꾸므로 " +
                    "가입 시 [선택] 동의와 항상 같은 값입니다. 값이 실제로 바뀐 경우에만 동의/철회 일시를 이력에 남깁니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "변경 성공 - Status: 204 No Content"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "알림 설정 변경 요청 (모든 필드 선택, null인 필드는 변경되지 않음)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = NotificationSettingRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "새 게시물 알림 끄기",
                                    value = "{\n  \"followPostPushAgree\": false\n}"
                            ),
                            @ExampleObject(
                                    name = "광고성 정보 수신 철회",
                                    value = "{\n  \"marketingAgree\": false\n}",
                                    description = "철회 시각이 marketingAgreeChangedAt과 동의 이력에 기록됩니다"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateNotificationSettings(@RequestBody NotificationSettingRequest request);

    @Operation(
            summary = "C15-2 회원정보 조회 (조회 전용)",
            description = "본인인증(PASS)으로 확인된 이름·생년월일·휴대폰번호를 **마스킹해서** 반환합니다.\n\n" +
                    "화면에서 직접 수정할 수 있는 값이 없으므로 조회 전용입니다. " +
                    "값을 바꾸려면 `POST /v1/user/settings/account/verifications`로 재인증합니다.\n\n" +
                    "아직 본인인증을 하지 않았거나 값이 없으면 각 필드는 null입니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountInfoResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 시",
                                    value = "{\n" +
                                            "  \"name\": \"김수*\",\n" +
                                            "  \"birthday\": \"1998.04.**\",\n" +
                                            "  \"phoneNumber\": \"010-****-1234\",\n" +
                                            "  \"identityVerifiedAt\": \"2026-07-01T10:22:41\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<AccountInfoResponse> getAccountInfo();

    @Operation(
            summary = "C15-2 회원정보 변경 (PASS 재인증)",
            description = "이름·생년월일·휴대폰번호는 통신사 원장 값이라 직접 입력받지 않고, **재인증 결과로 갱신**합니다.\n\n" +
                    "- `agreeConsent`가 true여야 진행됩니다(가입 시 동의와 별개의 새 수집 행위라 매번 다시 받습니다). " +
                    "false면 400 `IDENTITY_CONSENT_REQUIRED`.\n" +
                    "- 동의 일시는 회원 동의 이력에 기록됩니다.\n" +
                    "- 인증이 끝나면 이름·생년월일·성별·휴대폰번호와 본인인증 시각이 갱신되고, 갱신된 마스킹 값을 반환합니다.\n\n" +
                    "**PASS 연동 전까지는 임시 인증 데이터로 갱신됩니다.**\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "재인증 및 갱신 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountInfoResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 시",
                                    value = "{\n" +
                                            "  \"name\": \"김수*\",\n" +
                                            "  \"birthday\": \"1998.04.**\",\n" +
                                            "  \"phoneNumber\": \"010-****-1234\",\n" +
                                            "  \"identityVerifiedAt\": \"2026-08-15T09:41:00\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "필수 동의 누락 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "동의 없음",
                                    value = "{\n" +
                                            "  \"code\": \"IDENTITY_CONSENT_REQUIRED\",\n" +
                                            "  \"message\": \"본인확인을 위한 개인정보 수집·이용에 동의해야 합니다.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "본인확인 수집·이용 동의",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = IdentityReverifyRequest.class),
                    examples = @ExampleObject(name = "동의 후 재인증", value = "{\n  \"agreeConsent\": true\n}")
            )
    )
    ResponseEntity<AccountInfoResponse> reverifyIdentity(@RequestBody IdentityReverifyRequest request);
}

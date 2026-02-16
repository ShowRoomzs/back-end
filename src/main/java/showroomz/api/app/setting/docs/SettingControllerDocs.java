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
import showroomz.api.app.setting.DTO.NotificationSettingRequest;
import showroomz.api.app.setting.DTO.NotificationSettingResponse;

@Tag(name = "User - Settings", description = "사용자 설정 관리 API")
public interface SettingControllerDocs {

    @Operation(
            summary = "알림 설정 조회",
            description = "현재 로그인한 사용자의 알림 설정 정보를 조회합니다.\n\n" +
                    "**알림 설정 종류:**\n" +
                    "- `smsAgree`: 문자 알림 동의 여부 (기본값: false)\n" +
                    "- `nightPushAgree`: 야간 푸시 알림 동의 여부 (기본값: false)\n" +
                    "- `showroomPushAgree`: 쇼룸 알림 동의 여부 (기본값: true)\n" +
                    "- `marketPushAgree`: 브랜드(마켓) 알림 동의 여부 (기본값: true)\n\n" +
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
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"smsAgree\": false,\n" +
                                                    "  \"nightPushAgree\": false,\n" +
                                                    "  \"showroomPushAgree\": true,\n" +
                                                    "  \"marketPushAgree\": true\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<NotificationSettingResponse> getNotificationSettings();

    @Operation(
            summary = "알림 설정 변경",
            description = "현재 로그인한 사용자의 알림 설정을 변경합니다. \n\n" +
                    "**알림 설정 종류:**\n" +
                    "- `smsAgree`: 문자 알림 동의 여부 (선택사항)\n" +
                    "- `nightPushAgree`: 야간 푸시 알림 동의 여부 (선택사항)\n" +
                    "- `showroomPushAgree`: 쇼룸 알림 동의 여부 (선택사항)\n" +
                    "- `marketPushAgree`: 브랜드(마켓) 알림 동의 여부 (선택사항)\n\n" +
                    "**부분 업데이트 지원:**\n" +
                    "- 요청에 포함되지 않은 필드(null)는 변경되지 않습니다.\n" +
                    "- 예: `{\"smsAgree\": true}`만 보내면 SMS 알림만 변경되고 나머지는 유지됩니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "변경 성공 - Status: 204 No Content (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "알림 설정 변경 요청 (모든 필드는 선택사항, null인 필드는 변경되지 않음)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = NotificationSettingRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "SMS 알림만 변경",
                                    value = "{\n" +
                                            "  \"smsAgree\": true\n" +
                                            "}",
                                    description = "SMS 알림만 켜고 나머지는 변경하지 않음"
                            ),
                            @ExampleObject(
                                    name = "야간 푸시 알림 끄기",
                                    value = "{\n" +
                                            "  \"nightPushAgree\": false\n" +
                                            "}",
                                    description = "야간 푸시 알림만 끄고 나머지는 변경하지 않음"
                            ),
                            @ExampleObject(
                                    name = "모든 설정 변경",
                                    value = "{\n" +
                                            "  \"smsAgree\": true,\n" +
                                            "  \"nightPushAgree\": false,\n" +
                                            "  \"showroomPushAgree\": true,\n" +
                                            "  \"marketPushAgree\": false\n" +
                                            "}",
                                    description = "모든 알림 설정을 한 번에 변경"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateNotificationSettings(@RequestBody NotificationSettingRequest request);
}

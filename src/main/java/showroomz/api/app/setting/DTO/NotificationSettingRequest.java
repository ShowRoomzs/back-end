package showroomz.api.app.setting.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C15 설정 · 알림 설정 변경 요청.
 * Boolean Wrapper라 보내지 않은 값(null)은 변경하지 않는다(토글 하나만 눌러도 그것만 보내면 된다).
 */
@Getter
@NoArgsConstructor
public class NotificationSettingRequest {

    @Schema(description = "팔로우 쇼룸 새 게시물 알림", example = "true")
    private Boolean followPostPushAgree;

    @Schema(description = "광고성 정보 수신 동의 — 가입 시 [선택] 동의와 같은 값", example = "false")
    private Boolean marketingAgree;
}

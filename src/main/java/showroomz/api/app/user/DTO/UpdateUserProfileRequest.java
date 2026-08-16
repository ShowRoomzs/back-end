package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C15 설정에서 사용자가 직접 바꿀 수 있는 값만 담는다.
 *
 * <p>이름·생년월일·성별·휴대폰번호는 본인인증(PASS) 결과라 여기서 수정하지 않는다 —
 * 갱신은 {@code POST /v1/user/settings/account/verifications}(재인증)로만 이뤄진다.
 * 광고성 정보 수신 동의도 알림 설정({@code PATCH /v1/user/settings/notifications})으로 옮겼다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    @Schema(description = "닉네임 (선택) — 한글·영문·숫자 2~10자", example = "수민이네")
    private String nickname;

    @Schema(description = "프로필 사진 URL (선택)", example = "https://cdn.showroomz.com/profile/1.jpg")
    private String profileImageUrl;
}

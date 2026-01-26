package showroomz.api.app.user.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationSettingRequest {
    // Boolean Wrapper 타입을 사용하여, 보내지 않은 값(null)은 변경하지 않도록 처리
    private Boolean smsAgree;
    private Boolean nightPushAgree;
    private Boolean showroomPushAgree;
    private Boolean marketPushAgree;
}

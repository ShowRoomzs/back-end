package showroomz.api.app.user.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingResponse {
    private boolean smsAgree;          // 문자 알림
    private boolean nightPushAgree;    // 야간 푸시 알림
    private boolean showroomPushAgree; // 쇼룸 알림 (서비스)
    private boolean marketPushAgree;   // 브랜드 알림 (마켓)
}

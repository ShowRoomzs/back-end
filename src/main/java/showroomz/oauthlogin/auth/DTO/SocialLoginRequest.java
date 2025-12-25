package showroomz.oauthlogin.auth.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialLoginRequest {
    @NotNull
    private String accessToken; // 소셜 Access Token (필수)

    // 푸시 알림용 FCM 토큰 (알림 기능 추가 전까지 null 허용)
    private String fcmToken;
}
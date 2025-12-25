package showroomz.oauthlogin.auth.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialLoginRequest {
    @NotNull(message = "providerType은 필수 입력값입니다.")
    private String providerType; // "KAKAO", "NAVER", "APPLE"

    @NotNull(message = "token은 필수 입력값입니다.")
    private String token; // 애플은 idToken, 카카오/네이버는 accessToken

    private String name; // (애플 로그인 시) 첫 로그인 시 이름

    private String fcmToken; // (optional) 푸시 알림 전송용 FCM 토큰
}
package showroomz.api.app.auth.DTO;

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

    // (optional) "ANDROID" / "IOS" / "WEB". 보내지 않으면 UNKNOWN으로 저장된다.
    // 발송 자체에는 필요 없지만(FCM은 토큰만으로 보낸다), 나중에 플랫폼별 알림 규칙을
    // 나눠야 할 때 이미 쌓인 토큰을 되짚어 분류할 방법이 없어 지금부터 받아 둔다.
    private String platform;
}
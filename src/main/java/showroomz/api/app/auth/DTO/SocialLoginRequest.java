package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "소셜 로그인 요청 (유저 앱 · 크리에이터 공용)")
public class SocialLoginRequest {
    @NotNull(message = "providerType은 필수 입력값입니다.")
    @Schema(description = "소셜 공급자 — KAKAO · NAVER · GOOGLE · APPLE (대소문자 무시)", example = "KAKAO")
    private String providerType; // "KAKAO", "NAVER", "APPLE"

    @NotNull(message = "token은 필수 입력값입니다.")
    @Schema(description = "공급자가 발급한 토큰 — APPLE은 idToken, KAKAO · NAVER · GOOGLE은 accessToken",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token; // 애플은 idToken, 카카오/네이버는 accessToken

    @Schema(description = "실명 (선택) — APPLE 로그인에서만 사용. 애플은 첫 로그인 때만 이름을 주므로 그때 보낸다. 다른 공급자는 무시",
            example = "홍길동")
    private String name; // (애플 로그인 시) 첫 로그인 시 이름

    @Schema(description = "푸시 알림용 FCM 토큰 (선택 · 유저 앱 로그인에서만 등록) — 보내면 이 기기가 발송 대상이 되고, "
            + "다른 계정에 등록돼 있던 같은 토큰은 이번 계정으로 옮겨진다", example = "dGVzdC1mY20tdG9rZW4:APA91bExampleToken")
    private String fcmToken; // (optional) 푸시 알림 전송용 FCM 토큰

    // (optional) "ANDROID" / "IOS" / "WEB". 보내지 않으면 UNKNOWN으로 저장된다.
    // 발송 자체에는 필요 없지만(FCM은 토큰만으로 보낸다), 나중에 플랫폼별 알림 규칙을
    // 나눠야 할 때 이미 쌓인 토큰을 되짚어 분류할 방법이 없어 지금부터 받아 둔다.
    @Schema(description = "기기 플랫폼 (선택) — ANDROID · IOS · WEB. 없거나 모르는 값이면 UNKNOWN으로 저장", example = "IOS")
    private String platform;
}
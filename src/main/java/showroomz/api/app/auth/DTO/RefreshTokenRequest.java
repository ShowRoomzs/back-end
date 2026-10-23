package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "토큰 재발급 · 로그아웃 요청")
public class RefreshTokenRequest {
    @Schema(description = "리프레시 토큰 — 재발급 · 로그아웃 모두 필수", example = "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4gZXhhbXBsZQ")
    private String refreshToken;

    /**
     * (optional) 로그아웃할 기기의 FCM 토큰.
     *
     * <p>보내지 않으면 그 기기는 발송 대상에 남는다 — 로그아웃했는데 알림이 계속 오는 상태다.
     * 토큰 갱신(refresh)에서는 쓰지 않는다.
     */
    @Schema(description = "로그아웃할 기기의 FCM 토큰 (선택 · 유저 앱 로그아웃에서만 사용) — 보내면 이 기기를 푸시 발송 대상에서 뺀다. "
            + "생략하면 로그아웃 후에도 그 기기로 알림이 간다. 토큰 재발급에서는 무시",
            example = "dGVzdC1mY20tdG9rZW4:APA91bExampleToken")
    private String fcmToken;
}
package showroomz.api.app.auth.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenRequest {
    private String refreshToken;

    /**
     * (optional) 로그아웃할 기기의 FCM 토큰.
     *
     * <p>보내지 않으면 그 기기는 발송 대상에 남는다 — 로그아웃했는데 알림이 계속 오는 상태다.
     * 토큰 갱신(refresh)에서는 쓰지 않는다.
     */
    private String fcmToken;
}
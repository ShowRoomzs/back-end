package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.notification.entity.DeviceToken;

/** 로그인 뒤 푸시 토큰 등록 · 갱신 — 알림 권한을 로그인 뒤에 받거나 토큰이 바뀌었을 때 부른다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {

    @Schema(description = "FCM 등록 토큰(iOS 도 APNs 토큰이 아니라 FCM 토큰)", example = "dGVzdC1mY20tdG9rZW4...")
    @NotBlank(message = "fcmToken 은 필수입니다.")
    @Size(max = DeviceToken.MAX_TOKEN_LENGTH, message = "fcmToken 이 너무 깁니다.")
    private String fcmToken;

    @Schema(description = "기기 플랫폼 — ANDROID · IOS · WEB. 없거나 모르는 값이면 UNKNOWN", example = "IOS", nullable = true)
    private String platform;
}

package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 본인인증 시작 응답(57 설계서 3-1) — 앱은 이 세 값을 그대로 RN SDK `<IdentityVerification request>` 에 넘긴다. 키를 앱에 박지 않는다. */
@Getter
@AllArgsConstructor
public class IdentityVerificationStartResponse {

    @Schema(description = "포트원 identityVerificationId — 영문·숫자 29자. SDK request.identityVerificationId", example = "idv01HXYZ3K8M2Q7R9T1V4W6Y8Z")
    private final String identityVerificationId;

    @Schema(description = "포트원 상점 ID — SDK request.storeId", example = "store-4ff4af41-85e3-4559-8eb8-0d08a2c6ceec")
    private final String storeId;

    @Schema(description = "KCP 휴대폰 본인확인 채널 키 — SDK request.channelKey", example = "channel-key-9987cb87-6458-4888-b94e-68d9a2da896d")
    private final String channelKey;

    @Schema(description = "이 시각까지 완료 API 를 불러야 한다(시작 + 10분). 지나면 다시 시작한다", example = "2026-10-24T10:10:00")
    private final LocalDateTime expiresAt;
}

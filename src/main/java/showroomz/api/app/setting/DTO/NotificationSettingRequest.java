package showroomz.api.app.setting.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C15 설정 · 알림 설정 변경 요청(1017 시안 — 광고성 토글 4개).
 * Boolean Wrapper라 보내지 않은 값(null)은 변경하지 않는다(토글 하나만 눌러도 그것만 보내면 된다).
 */
@Getter
@NoArgsConstructor
public class NotificationSettingRequest {

    @Schema(description = "팔로우 쇼룸 새 공구 — 광고성", example = "true")
    private Boolean followGroupBuyPushAgree;

    @Schema(description = "팔로우 쇼룸 새 게시물(하루 묶음) — 광고성", example = "true")
    private Boolean followPostPushAgree;

    @Schema(description = "공구 마감 임박(장바구니 · 좋아요 상품 · 마감 하루 전) — 광고성", example = "true")
    private Boolean groupBuyEndingPushAgree;

    @Schema(description = "혜택 · 이벤트 — 가입 시 [선택] 광고성 정보 수신 동의와 같은 값", example = "false")
    private Boolean marketingAgree;
}

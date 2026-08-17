package showroomz.api.app.setting.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * C15 설정 · 알림 설정 조회 응답.
 * 주문·배송·문의 답변 등 거래 알림은 끌 수 없어 설정 항목으로 내려가지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingResponse {

    @Schema(description = "팔로우 쇼룸 새 게시물 알림", example = "true")
    private boolean followPostPushAgree;

    @Schema(description = "광고성 정보 수신 동의 — 가입 시 [선택] 동의와 같은 값", example = "false")
    private boolean marketingAgree;

    @Schema(description = "광고성 정보 수신 동의/철회를 마지막으로 바꾼 시각 (한 번도 바꾼 적 없으면 null)")
    private LocalDateTime marketingAgreeChangedAt;
}

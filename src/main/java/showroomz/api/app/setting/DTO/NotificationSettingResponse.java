package showroomz.api.app.setting.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.NotificationToggle;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C15 설정 · 알림 설정 조회 응답(1017 시안). 주문 · 배송 · 문의 답변 등 거래 알림은 끌 수 없어 설정 항목으로 내려가지 않는다.
 *
 * <p>{@code toggles}가 화면 그대로의 4행이다(순서 · 라벨 · 설명 · 켜짐 · 「YYYY.MM.DD 수신 동의」). 개별 필드는 기존 클라이언트 호환용이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingResponse {

    @Schema(description = "팔로우 쇼룸 새 게시물 알림", example = "true")
    private boolean followPostPushAgree;

    @Schema(description = "혜택 · 이벤트(광고성 정보 수신 동의) — 가입 시 [선택] 동의와 같은 값", example = "false")
    private boolean marketingAgree;

    @Schema(description = "광고성 정보 수신 동의/철회를 마지막으로 바꾼 시각 (한 번도 바꾼 적 없으면 null)")
    private LocalDateTime marketingAgreeChangedAt;

    @Schema(description = "팔로우 쇼룸 새 공구 알림", example = "true")
    private boolean followGroupBuyPushAgree;

    @Schema(description = "공구 마감 임박 알림", example = "true")
    private boolean groupBuyEndingPushAgree;

    @Schema(description = "C15 알림 설정 4행 — 화면 순서")
    private List<Toggle> toggles;

    @Getter
    @AllArgsConstructor
    @Schema(name = "NotificationSettingToggle")
    public static class Toggle {
        @Schema(description = "FOLLOW_GROUP_BUY · FOLLOW_POST · GROUP_BUY_ENDING · BENEFIT")
        private NotificationToggle key;
        @Schema(example = "팔로우 쇼룸 새 공구")
        private String label;
        @Schema(example = "팔로우한 쇼룸에 새 공구가 열리면 알려드려요")
        private String description;
        private boolean enabled;
        @Schema(description = "켜져 있을 때 수신 동의 일시 — 「2026.10.16 수신 동의」. 꺼져 있으면 null", nullable = true)
        private LocalDateTime agreedAt;
    }
}

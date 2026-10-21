package showroomz.api.common.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 종 배지 — 파트너 · 스튜디오는 안 읽은 수(인디고 숫자 배지 · 60초 폴링). */
@Schema(description = "알림 배지")
public record NotificationSummary(
        @Schema(description = "안 읽은 알림 수(보관 기간 안) — 0이면 배지 없음", example = "3") long unreadCount) {
}

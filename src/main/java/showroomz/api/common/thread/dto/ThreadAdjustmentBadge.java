package showroomz.api.common.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 연결·소통 목록 줄의 정산 조정 협의 표시(44 이슈 스레드 설계서 3-6) — 「[이슈] 정산 조정 요청 · 내 응답 필요」와 배지 색을 FE 가 조립한다.
 * {@code kind = SETTLEMENT_ADJUSTMENT} 인 줄에만 있다.
 */
@Schema(description = "정산 조정 협의 — 스레드 종류가 SETTLEMENT_ADJUSTMENT 일 때만(그 밖은 null)")
public record ThreadAdjustmentBadge(
        Long adjustmentId,
        @Schema(description = "OPEN · AGREED · EXPIRED") String status,
        @Schema(example = "협의 중") String statusLabel,
        @Schema(description = "뷰어 기준 — MY_TURN · THEIR_TURN · OPEN_FLOOR · CLOSED") String turn,
        @Schema(example = "내 응답 필요") String turnLabel,
        @Schema(example = "WARNING") String turnTone,
        LocalDateTime deadlineAt,
        @Schema(description = "D-N — 종결 뒤 null", nullable = true) Integer remainingBusinessDays) {
}

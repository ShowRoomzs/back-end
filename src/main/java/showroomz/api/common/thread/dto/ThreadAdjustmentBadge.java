package showroomz.api.common.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 연결·소통 목록 줄의 정산 조정 협의 표시(44 이슈 스레드 설계서 3-6) — 「[이슈] 정산 조정 요청 · 내 응답 필요」와 배지 색을 FE 가 조립한다.
 * 앞말은 최신 제안의 종류다 — 상대가 다른 금액을 제안하면 「[이슈] 다른 금액 제안 · 내 응답 필요」(55 계획서 S5 · 시안 T2).
 * {@code kind = SETTLEMENT_ADJUSTMENT} 인 줄에만 있다.
 */
@Schema(description = "정산 조정 협의 — 스레드 종류가 SETTLEMENT_ADJUSTMENT 일 때만(그 밖은 null)")
public record ThreadAdjustmentBadge(
        Long adjustmentId,
        @Schema(description = "OPEN · AGREED · EXPIRED · WITHDRAWN") String status,
        @Schema(example = "협의 중") String statusLabel,
        @Schema(description = "뷰어 기준 — MY_TURN · THEIR_TURN · OPEN_FLOOR · CLOSED") String turn,
        @Schema(example = "내 응답 필요") String turnLabel,
        @Schema(example = "WARNING") String turnTone,
        LocalDateTime deadlineAt,
        @Schema(description = "D-N — 종결 뒤 null", nullable = true) Integer remainingBusinessDays,
        @Schema(description = "최신 제안의 순번 — 제안이 없으면 null", example = "2", nullable = true) Integer latestProposalSeq,
        @Schema(description = "최신 제안 종류 — REQUEST(seq 1 · 정산 조정 요청) · COUNTER(seq 2 이상 · 다른 금액 제안). 제안이 없으면 null",
                example = "COUNTER", nullable = true) String latestProposalKind,
        @Schema(description = "목록 줄 앞말 — 「정산 조정 요청」 · 「다른 금액 제안」(카드 제목과 같다)", example = "다른 금액 제안",
                nullable = true) String latestProposalKindLabel) {
}

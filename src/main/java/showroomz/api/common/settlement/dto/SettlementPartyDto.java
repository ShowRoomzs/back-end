package showroomz.api.common.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파트너 13 · 스튜디오 12 정산 응답이 같이 쓰는 조각(44 파트너 · 스튜디오 설계서) — 두 서피스가 같은 모양으로 내린다.
 */
public final class SettlementPartyDto {

    private SettlementPartyDto() {
    }

    @Schema(name = "SettlementAmountCount", description = "금액 + 건수(분해 행)")
    public record AmountCount(
            @Schema(example = "68000") long amount,
            @Schema(example = "2") int count
    ) {
    }

    @Schema(name = "SettlementKpi", description = "KPI 칸 — 건수 0 이면 amount = null(「0원은 실적처럼 읽힌다」 · FE 「—」)")
    public record Kpi(
            @Schema(nullable = true, example = "4712800") Long amount,
            @Schema(example = "1") long count
    ) {
        public static Kpi of(long amount, long count) {
            return new Kpi(count == 0 ? null : amount, count);
        }
    }

    @Schema(name = "SettlementStatusCounts", description = "상태 칩 숫자 — 분배 실패는 지급 완료에 접힌다")
    public record StatusCounts(long REVIEWING, long ADJUSTING, long PAYOUT_SCHEDULED, long PAID) {
    }

    @Schema(name = "SettlementAdjustmentBlock",
            description = "조정 내역(44 이슈 스레드 설계서 1-7 AdjustmentSummary) — 조정 협의가 열린 뒤 항상(종결 뒤에도 이력으로) · 없으면 null")
    public record AdjustmentBlock(
            Long adjustmentId,
            @Schema(description = "3자 이슈 스레드 — 연결·소통 화면으로 이동") Long threadId,
            @Schema(description = "OPEN · AGREED · EXPIRED") String status,
            @Schema(example = "협의 중") String statusLabel,
            @Schema(description = "처음 요청한 쪽 — SELLER · CREATOR") String requesterType,
            LocalDateTime openedAt,
            @Schema(description = "합의 기한 — 개설 + 10영업일 23:59:59 · 연장 없음") LocalDateTime deadlineAt,
            @Schema(description = "D-N — 종결 뒤 null", nullable = true) Integer remainingBusinessDays,
            @Schema(description = "원래 리워드(공급가)") long originalRewardAmount,
            @Schema(description = "제안 상한(개설 시 스냅샷)") long maxRewardAmount,
            @Schema(nullable = true) Long agreedRewardAmount,
            @Schema(nullable = true) Long finalRewardAmount,
            @Schema(description = "내 최신 제안 금액 — 없으면 null", nullable = true) Long myLatestAmount,
            @Schema(description = "상대 최신 제안 금액 — 없으면 null", nullable = true) Long counterpartLatestAmount,
            @Schema(nullable = true) LocalDateTime counterpartLatestAt,
            @Schema(description = "MY_TURN · THEIR_TURN · OPEN_FLOOR · CLOSED — 뷰어 기준") String turn,
            @Schema(example = "내 응답 필요") String turnLabel,
            @Schema(example = "WARNING") String turnTone,
            @Schema(description = "제안 — seq 오름차순 전량") List<AdjustmentProposal> proposals
    ) {
    }

    @Schema(name = "SettlementAdjustmentProposal")
    public record AdjustmentProposal(
            int seq,
            @Schema(description = "SELLER · CREATOR") String proposerType,
            @Schema(description = "내가 낸 제안인가") boolean mine,
            long rewardAmount,
            @Schema(nullable = true) String reason,
            @Schema(description = "PENDING · ACCEPTED · REJECTED · COUNTERED · CLOSED") String status,
            LocalDateTime proposedAt,
            @Schema(nullable = true) LocalDateTime respondedAt
    ) {
    }
}

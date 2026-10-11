package showroomz.api.common.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 조정 협의 API — 파트너센터 · 쇼룸 스튜디오 공용 요청 · 응답(44 이슈 스레드 설계서 3-2 ~ 3-5). 두 서피스가 같은 모양이다
 * ({@code MessageCardResponse}와 같은 규칙). 금액은 전부 리워드 공급가(부가세 별도)다 — 세후 · 부가세는 미리보기의 별도 필드로만.
 */
public final class SettlementAdjustmentDto {

    private SettlementAdjustmentDto() {
    }

    // ------------------------------------------------------------------ 3-2 미리보기

    @Schema(name = "SettlementAdjustmentPreview")
    public record PreviewResponse(
            Long settlementId,
            @Schema(example = "STL-2609-006") String settlementNumber,
            @Schema(description = "조정 요청 가능 — 확인 기간 안 ∧ 협의 없음. false 면 버튼 비활성(에러 문구 없이)") boolean canRequest,
            @Schema(description = "REVIEW_CLOSED · ALREADY_ADJUSTING — FE 는 쓰지 않아도 된다", nullable = true)
            String cannotRequestReason,
            @Schema(description = "확인 마감") LocalDateTime reviewEndsAt,
            long originalRewardAmount,
            @Schema(description = "조정 상한 — 「조정 후 브랜드 수취액 ≥ 0」 · 10원 미만 절사", example = "898600") long maxRewardAmount,
            @Schema(description = "입력 — rewardAmount 를 보내지 않으면 null", nullable = true) Input input,
            @Schema(description = "입력 금액의 자동 계산 — 입력이 없으면 null", nullable = true) Amounts preview,
            @Schema(description = "원래 금액의 계산") Amounts original,
            @Schema(description = "0 ≤ 입력 ≤ 상한", nullable = true) Boolean inRange
    ) {
    }

    @Schema(name = "SettlementAdjustmentInput")
    public record Input(long rewardAmount) {
    }

    @Schema(name = "SettlementAdjustmentAmounts", description = "정산 포트의 계산값 — 전 항목 절사")
    public record Amounts(long rewardAmount, long rewardVatAmount, long withholdingAmount,
                          @Schema(description = "인플루언서 실지급") long creatorNetAmount,
                          @Schema(description = "브랜드 수취액") long brandPayoutAmount) {
    }

    // ------------------------------------------------------------------ 3-3 조정 요청

    @Schema(name = "SettlementAdjustmentRequest", description = "근거 첨부 칸은 없다(2026.10.07 삭제 — 스레드 말풍선 첨부로)")
    public record RequestBody(
            @NotNull @Min(0) @Schema(description = "요청 리워드(공급가)", example = "180528") Long rewardAmount,
            @Schema(description = "사유 — 필수 · 1,000자. 공백이면 400 SETTLEMENT_ADJUSTMENT_REASON_REQUIRED(서비스 판정)",
                    requiredMode = Schema.RequiredMode.REQUIRED, example = "사전 제공 샘플 20개 중 12개 미도착(반송)") String reason
    ) {
    }

    @Schema(name = "SettlementAdjustmentRequestResponse")
    public record RequestResponse(Long adjustmentId,
                                  @Schema(description = "자동 개설된 3자 이슈 스레드 — 연결·소통 화면으로 이동") Long threadId,
                                  Long proposalId,
                                  @Schema(description = "합의 기한 — 개설 + 10영업일 23:59:59 · 연장 없음") LocalDateTime deadlineAt) {
    }

    // ------------------------------------------------------------------ 3-5 응답

    @Schema(name = "SettlementAdjustmentCounterRequest")
    public record CounterBody(
            @NotNull @Min(0) @Schema(description = "제안 리워드(공급가)", example = "165528") Long rewardAmount,
            @Schema(description = "사유(선택)", nullable = true) String reason
    ) {
    }

    // ------------------------------------------------------------------ 3-4 고정 카드 · 제안 목록

    @Schema(name = "SettlementAdjustmentView", description = "스레드 고정 카드 + 제안 목록 + 내 권한(뷰어 기준)")
    public record AdjustmentView(
            Long adjustmentId,
            Long threadId,
            @Schema(description = "OPEN · AGREED · EXPIRED") String status,
            @Schema(example = "협의 중") String statusLabel,
            @Schema(description = "MY_TURN · THEIR_TURN · OPEN_FLOOR · CLOSED") String turn,
            @Schema(example = "내 응답 필요") String turnLabel,
            @Schema(example = "WARNING") String turnTone,
            SettlementRef settlement,
            GroupBuyRef groupBuy,
            Counterpart counterpart,
            @Schema(description = "처음 요청한 쪽 — SELLER · CREATOR") String requesterType,
            long originalRewardAmount,
            long maxRewardAmount,
            ProposalView latestProposal,
            @Schema(nullable = true) Long agreedRewardAmount,
            @Schema(nullable = true) Long finalRewardAmount,
            LocalDateTime openedAt,
            LocalDateTime deadlineAt,
            @Schema(description = "D-N — 종결 뒤 null", nullable = true) Integer remainingBusinessDays,
            @Schema(nullable = true) LocalDateTime closedAt,
            Permissions permissions,
            @Schema(description = "제안 — seq 오름차순 전량") List<ProposalView> proposals
    ) {
    }

    @Schema(name = "SettlementAdjustmentSettlementRef")
    public record SettlementRef(Long settlementId, String settlementNumber) {
    }

    @Schema(name = "SettlementAdjustmentGroupBuyRef")
    public record GroupBuyRef(Long groupBuyId, String groupBuyNumber, String title) {
    }

    @Schema(name = "SettlementAdjustmentCounterpart")
    public record Counterpart(@Schema(description = "SELLER · CREATOR") String type, String name) {
    }

    @Schema(name = "SettlementAdjustmentProposalView")
    public record ProposalView(
            Long proposalId,
            int seq,
            @Schema(description = "SELLER · CREATOR") String proposerType,
            String proposerName,
            long rewardAmount,
            @Schema(description = "원래 리워드 대비 — 양수 증액 · 음수 감액") long deltaAmount,
            @Schema(nullable = true) String reason,
            @Schema(description = "PENDING · ACCEPTED · REJECTED · COUNTERED · CLOSED") String status,
            LocalDateTime proposedAt,
            @Schema(nullable = true) LocalDateTime respondedAt,
            @Schema(nullable = true) Long cardMessageId
    ) {
    }

    @Schema(name = "SettlementAdjustmentPermissions", description = "버튼 권한 — 서버가 판정한다(FE 는 식을 들지 않는다)")
    public record Permissions(boolean canAccept, boolean canReject, boolean canCounter,
                              @Schema(description = "메시지 전송 — 종결이면 false") boolean canSend) {
    }
}

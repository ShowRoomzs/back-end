package showroomz.api.common.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;

import java.time.LocalDate;

/**
 * 내 지급 행이 보류(BLOCKED)된 이유(1017 설계서 8-8 · ui-partner-15 S5 · ui-studio-13 S6). 정산 상태는 「지급 예정」 그대로다 — 「보류」라는
 * 말은 정산 조정 협의 전용이라 상태 라벨을 바꾸지 않고 이 보조 정보로 보인다. 보류 사유는 정산 도메인이 이미 행의 {@code fail_code}에
 * 남긴다(지급 배치 · 정지 · 세금계산서 · 계좌 확인) — 지금까지 화면에 드러나지 않던 값이다.
 *
 * @param scheduleLabel   지급일 문구 — 「정지 해제 다음 영업일 지급」 · 「발행 확인 후 + 3영업일」
 * @param originalDueDate 원래 지급 예정일 — 「원래 2026.09.09」
 * @param sanction        계정 이용 정지로 인한 보류인가 — 배지 「지급 정지 · 계정 이용 정지」
 */
@Schema(description = "내 지급 행 보류 이유 — 보류가 아니면 null")
public record PayoutHoldView(
        @Schema(description = "PayoutBlockReason 코드", example = "BRAND_SUSPENDED") String reason,
        @Schema(example = "계정 이용 정지 · 지급 정지") String label,
        @Schema(example = "정지 해제 다음 영업일 지급") String scheduleLabel,
        @Schema(nullable = true) LocalDate originalDueDate,
        @Schema(description = "계정 이용 정지로 인한 보류인가") boolean sanction) {

    /** @param payoutBusinessDays 세금계산서 확인 후 지급까지의 영업일(「발행 확인 후 + N영업일」) */
    public static PayoutHoldView of(SettlementPayout payout, int payoutBusinessDays) {
        if (payout == null || payout.getStatus() != PayoutStatus.BLOCKED) {
            return null;
        }
        return PayoutBlockReason.fromCode(payout.getFailCode())
                .map(reason -> new PayoutHoldView(reason.name(), reason.getLabel(),
                        reason.getDueNoteFormat().contains("%d")
                                ? reason.getDueNoteFormat().formatted(payoutBusinessDays) : reason.getDueNoteFormat(),
                        payout.getDueDate(),
                        reason == PayoutBlockReason.BRAND_SUSPENDED || reason == PayoutBlockReason.CREATOR_SUSPENDED))
                .orElse(null);
    }
}

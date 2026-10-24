package showroomz.api.common.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.settlement.type.PayoutBlockReason;

import java.time.LocalDate;

/**
 * 내 지급 행이 보류(BLOCKED)된 이유(1017 설계서 8-8 · ui-partner-15 S5 · ui-studio-13 S6). 정산 상태는 「지급 예정」 그대로다 — 「보류」라는
 * 말은 정산 조정 협의 전용이라 상태 라벨을 바꾸지 않고 이 보조 정보로 보인다. 사유는 행의 {@code fail_code}(지급 배치 · 정지 · 계좌 확인)가
 * 있으면 그것, 없으면(확정 때 정책 보류) 지급 보류 판정의 첫 사유다 — 조립은 {@code PayoutHoldViews} 한 곳이 한다.
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
    public static PayoutHoldView of(PayoutBlockReason reason, LocalDate originalDueDate, int payoutBusinessDays) {
        String dueNote = reason.getDueNoteFormat();
        return new PayoutHoldView(reason.name(), reason.getLabel(),
                dueNote.contains("%d") ? dueNote.formatted(payoutBusinessDays) : dueNote, originalDueDate,
                reason == PayoutBlockReason.BRAND_SUSPENDED || reason == PayoutBlockReason.CREATOR_SUSPENDED);
    }
}

package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 초안 승인 기록 추가(1021 기-30) — 기록 전용. */
@Schema(description = "초안 승인 기록")
public record ContractDraftApprovalRequest(
        @Schema(description = "대상 — 100자", example = "릴스 1차 초안", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String title,
        @Schema(description = "승인 일시 — 생략하면 지금 · 미래 불가", example = "2026-08-12T15:40:00", nullable = true)
        LocalDateTime approvedAt,
        @Schema(description = "근거 — 연결·소통 스레드에서 확인한 초안 메시지 ID(선택)", nullable = true) Long threadMessageId,
        @Schema(description = "메모 — 300자", nullable = true) @Size(max = 300) String note) {
}

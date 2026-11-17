package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사전 검수 초안 제출(B-13 · 계약서 제8조) — 제출 시각이 브랜드의 수령 시각이다. */
@Schema(description = "콘텐츠 초안 제출")
public record CreatorContractDraftSubmitRequest(
        @Schema(description = "대상 — 100자", example = "릴스 1차 초안", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String title,
        @Schema(description = "연결·소통 스레드에 올린 초안 메시지 ID(선택)", nullable = true) Long threadMessageId,
        @Schema(description = "메모 — 300자", nullable = true) @Size(max = 300) String note) {
}

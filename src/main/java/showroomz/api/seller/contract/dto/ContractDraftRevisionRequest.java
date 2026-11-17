package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사전 검수 수정 요청 회신(B-13) — 회신 기한(수령 + 3영업일) 안에 보내면 승인 간주되지 않는다. */
@Schema(description = "초안 수정 요청")
public record ContractDraftRevisionRequest(
        @Schema(description = "수정 요청 내용 — 300자 · 인플루언서에게 보인다", example = "제품 사용 장면을 3초 이상 넣어 주세요",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 300) String note) {
}

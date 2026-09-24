package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "생성된 계약")
public record ContractCreateResponse(

        @Schema(description = "계약 ID", example = "128") Long contractId,

        @Schema(description = "낙관적 락 버전 — 첫 임시저장에 그대로 되돌려 보낸다", example = "0") Long version
) {
}

package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.contract.type.ContractCloseReasonCode;

/**
 * 계약 취소(C4) — <b>항상 종결</b>이다. 검토 대기의 [요청 취소]와 다른 경로다(설계서 3-2).
 */
@Schema(description = "계약 취소 요청")
public record ContractCancelRequest(

        @Schema(description = "취소 사유 5종", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ContractCloseReasonCode reasonCode,

        @Schema(description = "상대에게 전달되는 메모 — ETC(기타)면 필수다", nullable = true)
        @Size(max = 1000)
        String memo
) {
}

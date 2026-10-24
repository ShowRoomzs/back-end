package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.SuspensionReasonCode;

/** 해지 요청(파트너 08 C9 · 09 C4 · 1021 기-05) — 사유는 공구 중단 요청과 같은 4종. 메모는 상대 · 운영자에게 그대로 전달된다. */
@Schema(description = "계약 해지 요청")
public record ContractTerminationRequestRequest(
        @Schema(description = "QUALITY_ISSUE(상품 품질 이슈) · PRICE_TERMS_ERROR(가격·조건 오기) · NEGOTIATION_BROKEN(인플루언서와 협의 결렬) · ETC(기타 — memo 필수)",
                example = "QUALITY_ISSUE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull SuspensionReasonCode reasonCode,
        @Schema(description = "해지가 필요한 사정 — 인플루언서 · 운영자에게 그대로 전달된다 · 1000자", example = "공급 일정 차질로 오픈 일정을 맞출 수 없습니다.",
                nullable = true)
        @Size(max = 1000) String memo) {
}

package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.entity.ContractTerminationRequest;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractTerminationRequestStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonCode;

import java.time.LocalDateTime;

/** 공구 계약의 가장 최근 해지 요청(1021 설계서 1-4 · 58 계획 6-3) — 계약 상세와 같은 출처. 요청이 없으면 블록이 null. */
@Schema(description = "해지 요청 — 이 공구 계약의 가장 최근 요청(상태 무관)")
public record GroupBuyTerminationRequestView(
        @Schema(description = "PENDING(처리 대기) · HANDLED(해지 처리됨) · WITHDRAWN(요청 철회) · LAPSED(자동 종료 — 공구 오픈)",
                example = "PENDING")
        ContractTerminationRequestStatus status,
        @Schema(example = "처리 대기") String statusLabel,
        @Schema(description = "사유 — QUALITY_ISSUE · PRICE_TERMS_ERROR · NEGOTIATION_BROKEN · ETC", example = "NEGOTIATION_BROKEN")
        SuspensionReasonCode reasonCode,
        @Schema(example = "인플루언서와 협의 결렬") String reasonLabel,
        @Schema(description = "요청자 — 지금은 SELLER(브랜드)만 요청할 수 있다", example = "SELLER") ContractActorType requesterType,
        LocalDateTime requestedAt) {

    public static GroupBuyTerminationRequestView of(ContractTerminationRequest r) {
        return r == null ? null : new GroupBuyTerminationRequestView(r.getStatus(), r.getStatus().getLabel(),
                r.getReasonCode(), r.getReasonCode().getLabel(), r.getRequesterType(), r.getRequestedAt());
    }
}

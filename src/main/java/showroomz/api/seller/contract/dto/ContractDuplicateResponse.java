package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재작성(§26-5) 결과.
 *
 * <p>복사 직후 재검증 결과를 함께 싣는다 — 상품이 미진열로 바뀌었거나 정가가 내려갔을 수 있다.
 * 이때 스냅샷을 현재 상품 값으로 갱신하고 위반을 알린다. 옛 정가를 들고 있으면 H1이 통과해버린다.
 */
@Schema(description = "재작성 결과")
public record ContractDuplicateResponse(

        @Schema(description = "새로 만들어진 계약 ID", example = "131") Long contractId,

        @Schema(description = "복사 출처 계약 ID — 원 계약은 그대로 종결 상태로 남는다", example = "128")
        Long sourceContractId,

        @Schema(description = "복사 직후 재검증 결과") ContractValidationResponse validation
) {
}

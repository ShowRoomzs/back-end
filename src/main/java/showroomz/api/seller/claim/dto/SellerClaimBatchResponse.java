package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 클레임 다건 액션의 부분 성공 응답 — 처리되지 않은 행만 사유와 함께 제외하고 나머지는 진행한다(35 설계서 2절).
 * 주문 관리의 {@code BatchActionResponse}와 같은 모양이고 키만 클레임 id 다.
 */
public record SellerClaimBatchResponse(
        @Schema(description = "처리된 건수", example = "2") int succeeded,
        @Schema(description = "제외된 행과 사유 — 전부 성공이면 빈 배열") List<Skipped> skipped
) {

    @Schema(name = "SellerClaimBatchSkipped")
    public record Skipped(
            @Schema(description = "제외된 클레임 id", example = "3022") Long claimId,
            @Schema(description = "CLAIM_STATE_CHANGED · CLAIM_NOT_FOUND", example = "CLAIM_STATE_CHANGED") String code,
            @Schema(description = "결과 배너에 그대로 쓰는 안내 문구") String message
    ) {
    }
}

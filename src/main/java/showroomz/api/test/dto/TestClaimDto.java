package showroomz.api.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;

import java.time.LocalDateTime;

public class TestClaimDto {

    @Schema(description = "재발송 도착 처리 결과 (테스트)")
    public record ReshipDeliveredResponse(
            @Schema(description = "클레임 id", example = "3021")
            Long claimId,

            @Schema(description = "접수번호", example = "CLM-3021")
            String claimNumber,

            @Schema(description = "처리 뒤 상태 — COMPLETED", example = "COMPLETED")
            ClaimStatus status,

            @Schema(description = "EXCHANGED(교환 완료) · REJECTED(반려 반송 완료)", example = "EXCHANGED")
            ClaimResult result,

            @Schema(description = "재발송 도착 시각 — 교환 완료면 구매확정 타이머가 여기서 다시 시작한다", example = "2026-10-29T05:30:00Z")
            LocalDateTime deliveredAt
    ) {
    }
}

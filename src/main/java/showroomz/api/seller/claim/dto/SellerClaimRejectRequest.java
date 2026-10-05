package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.ClaimRejectReason;

import java.util.List;

/**
 * 검수 거절(35 설계서 3-3) — 사유 · 설명 · 증빙 세 가지가 전부 필수다. 제출 = 즉시 확정이고 되돌릴 수 없다.
 * 누락 검사는 서비스가 한다 — 셋 중 무엇이 빠졌든 같은 오류(CLAIM_REJECT_INCOMPLETE)로 답한다.
 */
public record SellerClaimRejectRequest(
        @Schema(description = "거절 사유", example = "USED") ClaimRejectReason reasonCode,
        @Schema(description = "상세 설명 — 소비자에게 그대로 전달된다", example = "용기 입구에 사용 흔적이 있습니다.") String detail,
        @Schema(description = "증빙 사진 URL — 1장 이상 5장 이하. 이미지 업로드 API 가 준 값")
        List<String> evidenceImageUrls
) {
}

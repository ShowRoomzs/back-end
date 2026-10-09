package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.ClaimRejectLegalBasis;
import showroomz.domain.order.type.ClaimRejectReason;

import java.util.List;

/**
 * 검수 반려 6항목(1009 기획 수정본 5-b · 파트너 11 B1r) — 사유 · 법적 근거 · 범위(전체/일부) · 귀책 변경 · 증빙 · 소비자 메시지.
 * 소비자 반품·교환 상세(C10-5)에 노출되는 값과 1:1 이다. 제출 = 즉시 확정이고 되돌릴 수 없다. 필수 항목 누락은 서비스가 검사한다 —
 * 무엇이 빠졌든 같은 오류(CLAIM_REJECT_INCOMPLETE)로 답한다.
 */
public record SellerClaimRejectRequest(
        @Schema(description = "반려 사유 — 필수", example = "USED") ClaimRejectReason reasonCode,
        @Schema(description = "상세 설명 — 필수 · 브랜드 · 어드민 기록용", example = "용기 입구에 사용 흔적이 있습니다.") String detail,
        @Schema(description = "법적 근거 — 필수 · 전자상거래법 제17조② 각 호", example = "ART17_2_2")
        ClaimRejectLegalBasis legalBasis,
        @Schema(description = "반려 수량 — 생략하거나 신청 수량과 같으면 전체 반려, 작으면 일부 반려(나머지는 검수 통과)",
                example = "1", nullable = true) Integer rejectedQuantity,
        @Schema(description = "귀책 변경 — true 면 브랜드 귀책으로 인정(반품 배송비 차감 환원 · 반려 재발송비 브랜드 부담). 기본 false",
                example = "false", nullable = true) Boolean faultChangedToSeller,
        @Schema(description = "소비자에게 보낼 메시지 — 필수 · 그대로 전달된다", example = "개봉 후 사용 흔적이 있어 반품이 어렵습니다.")
        String consumerMessage,
        @Schema(description = "증빙 사진 URL — 1장 이상 5장 이하. 이미지 업로드 API 가 준 값")
        List<String> evidenceImageUrls
) {
}

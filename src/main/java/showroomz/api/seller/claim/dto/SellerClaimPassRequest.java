package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검수 통과 확인(파트너 11 M1 · 1021 결정 노트 기-17 · 기-93) — 귀책은 신청 때 귀책이 기본이고, 여기서 브랜드 귀책으로 바꿀 수 있다.
 * 본문을 보내지 않으면 신청 때 귀책 그대로 통과한다.
 */
@Schema(description = "검수 통과 — 귀책 선택")
public record SellerClaimPassRequest(
        @Schema(description = "브랜드 귀책으로 인정 — 선불(소비자 귀책) 신청일 때만 의미가 있다. 선불 운임(기본 배송비 정액)과 차감한 최초 배송비가 "
                + "소비자 환불에 더해지고 브랜드 정산에서 「− 운임 정산」으로 빠진다. 원래 결제액을 넘는 환급분은 운영자가 계좌로 송금한다",
                example = "false", nullable = true) Boolean faultToSeller) {
}

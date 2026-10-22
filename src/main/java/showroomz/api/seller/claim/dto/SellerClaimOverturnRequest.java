package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 검수 「사유 번복」(파트너 11 B2b · 1021 결정 노트 기-17 【잠정】) — 착불(브랜드 귀책)로 신청했지만 검수 결과 소비자 귀책인 경우. */
@Schema(description = "검수 사유 번복")
public record SellerClaimOverturnRequest(
        @Schema(description = "판정 근거 설명 — 필수 · 소비자에게 그대로 전달된다", example = "외관 손상이 없고 정상 작동합니다. 단순 변심으로 확인됩니다.")
        String explanation,
        @Schema(description = "증빙 사진 URL — 1장 이상 5장 이하") List<String> evidenceImageUrls) {
}

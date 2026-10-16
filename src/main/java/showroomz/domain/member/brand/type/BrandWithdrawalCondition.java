package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 탈퇴 조건 4종(§51-5 · 파트너 06 4-F) — 판정식은 {@code BrandWithdrawalEligibility} 하나다. */
@Getter
@RequiredArgsConstructor
public enum BrandWithdrawalCondition {
    ACTIVE_GROUP_BUYS("진행 중 · 준비 중 공구"),
    CONTRACTS_BEFORE_CONCLUSION("체결 전 계약"),
    UNCLOSED_ORDERS("미종결 주문(배송 · 반품 · 교환)"),
    UNSETTLED("진행 중 정산");

    private final String label;
}

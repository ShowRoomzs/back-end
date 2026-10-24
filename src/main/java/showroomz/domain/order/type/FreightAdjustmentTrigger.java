package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 귀책 변경 운임 정산의 발생 지점(1021 설계서 4-1 · 결정 노트 기-93). */
@Getter
@RequiredArgsConstructor
public enum FreightAdjustmentTrigger {
    /** 검수 사유 번복 — 브랜드 → 소비자 귀책. 브랜드가 낸 착불 운임을 소비자가 부담한다. */
    REASON_OVERTURNED("사유 번복"),
    /** 검수 통과 때 브랜드 귀책으로 인정 — 소비자 → 브랜드. 선불 운임 · 차감한 최초 배송비를 돌려준다. */
    FAULT_TO_SELLER_ON_PASS("귀책 변경 · 통과"),
    /** 착불(브랜드 귀책)로 신청한 건의 검수 반려 — 착불 운임을 소비자가 부담한다. */
    REJECT_WITH_COLLECT("반려 · 착불 운임"),
    /** 반려 이의 인용 — 소비자 → 브랜드. */
    DISPUTE_ACCEPTED("이의 인용 · 귀책 변경");

    private final String label;
}

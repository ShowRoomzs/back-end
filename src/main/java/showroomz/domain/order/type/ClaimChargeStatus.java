package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 추가 결제의 정산 상태(앱 클레임 설계서 1-5). 문구는 파트너센터 반품·교환 목록 「재발송비」 열이 쓴다. */
@Getter
@RequiredArgsConstructor
public enum ClaimChargeStatus {
    PENDING("결제 대기"),
    /** PG 결제. */
    PAID("결제됨"),
    /** 같은 요청의 환불액에서 차감. */
    DEDUCTED("환불액에서 차감"),
    /** 교환 선결제분으로 충당. */
    COVERED("교환 결제분으로 충당"),
    /** 폐기·취소로 소멸. */
    VOID("소멸"),
    /** 결제 취소로 돌려줌. */
    REFUNDED("결제 취소"),
    /** 검수에서 브랜드 귀책으로 인정 — 반려 재발송비를 브랜드가 진다(1009 기획 수정본 5-b). */
    WAIVED("브랜드 부담");

    private final String label;
}

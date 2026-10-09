package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 운영자 사유 환불의 사유(어드민 06a B5 · 06b B2 · 06b 검수 무응답) — 정산의 분류 키다. */
@Getter
@RequiredArgsConstructor
public enum OperatorRefundReason {
    DISPUTE_ACCEPTED("반려 이의 인용"),
    POST_CONFIRM_DEFECT("구매확정 후 하자"),
    RECALL("위해성 리콜"),
    /** 검수 지연 자동 알림 N회 무응답 — 상품은 브랜드에 있고 소비자는 환불을 받는다(41 보고 4번 · 어드민 06b). */
    INSPECTION_UNANSWERED("검수 무응답");

    private final String label;

    /**
     * 클레임을 닫으며 편입되는 사유 — 어드민 06b 전용(환불액 서버 계산). 06a 사유 환불(운영자 입력 금액)로는 받지 않고,
     * 편입 철회도 하지 않는다 — 편입 때 클레임이 이미 환불로 종결됐고 반품 수량 · 재발송비 청구 · 교환 재고가 정리돼 되살릴 수 없다.
     */
    public boolean isClaimBound() {
        return this == DISPUTE_ACCEPTED || this == INSPECTION_UNANSWERED;
    }
}

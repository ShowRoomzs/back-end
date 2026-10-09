package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 운영자 사유 환불의 사유(어드민 06a B5 · 06b B2) — 이 셋만 운영자가 돈을 내보낸다. */
@Getter
@RequiredArgsConstructor
public enum OperatorRefundReason {
    DISPUTE_ACCEPTED("반려 이의 인용"),
    POST_CONFIRM_DEFECT("구매확정 후 하자"),
    RECALL("위해성 리콜"),
    /** 검수 지연 자동 알림 N회 무응답 — 상품은 브랜드에 있고 소비자는 환불을 받는다(41 보고 4번 · 어드민 06b). */
    INSPECTION_UNANSWERED("검수 무응답");

    private final String label;
}

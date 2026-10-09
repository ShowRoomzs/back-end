package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 운영자 사유 환불의 사유(어드민 06a B5 · 06b B2) — 이 셋만 운영자가 돈을 내보낸다. */
@Getter
@RequiredArgsConstructor
public enum OperatorRefundReason {
    DISPUTE_ACCEPTED("반려 이의 인용"),
    POST_CONFIRM_DEFECT("구매확정 후 하자"),
    RECALL("위해성 리콜");

    private final String label;
}

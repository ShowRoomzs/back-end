package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 명세 「상태」 열(44 어드민 설계서 1-1 · 2-2). 교환 완료 항목은 수량이 줄지 않으므로 CONFIRMED 다(13절 신규 #4).
 */
@Getter
@RequiredArgsConstructor
public enum SettlementItemStatus {
    CONFIRMED("구매확정"),
    PARTIAL_RETURNED("부분 반품"),
    RETURNED("반품"),
    CANCELLED("취소"),
    /** 반송 완료 · 분실 처리 · 정산 전 집행된 운영자 사유 환불(전 수량). */
    DELIVERY_EXCEPTION("배송 예외");

    private final String label;
}

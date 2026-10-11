package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 환불이 취소하는 결제(39 설계서 1-3) — 원래 주문 결제 / 클레임 추가 결제(교환 · 반려 재발송비). PG 거래가 따로라 취소도 따로다. */
@Getter
@RequiredArgsConstructor
public enum RefundPaymentKind {
    ORIGINAL("원래"),
    ADDITIONAL("추가");

    private final String label;
}

package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소비자 앱 주문 항목의 버튼(C10 설계서 1-6). 노출 규칙은 {@code UserOrderItemAssembler}가 갖고,
 * API 가 실재하는 것만 내린다(0-5 · {@code ENABLED_ACTIONS}).
 */
@Getter
@RequiredArgsConstructor
public enum UserOrderAction {

    /** 전액 취소 — {@code POST /v1/user/orders/{orderId}/cancel}. */
    CANCEL("주문 취소"),
    CANCEL_REQUEST("취소 요청"),
    TRACK_DELIVERY("배송 조회"),
    /** 목록 전용 — 주문 상세로 보낸다. */
    RETURN_EXCHANGE("반품 · 교환"),
    RETURN_REQUEST("반품 요청"),
    EXCHANGE_REQUEST("교환 요청"),
    /** 라벨은 클레임 유형으로 갈린다 — 교환이면 {@link #EXCHANGE_DETAIL_LABEL}. */
    CLAIM_DETAIL("반품 상세"),
    CANCEL_DETAIL("취소 상세");

    public static final String EXCHANGE_DETAIL_LABEL = "교환 상세";

    private final String label;
}

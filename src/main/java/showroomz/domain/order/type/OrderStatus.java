package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 주문 상태(결제 계획서 4-1).
 *
 * <pre>
 * PAYMENT_PENDING ──complete/webhook(PAID·금액 일치)──▶ PAID ──사용자 취소(배송 전)──▶ CANCELLED
 *       ├── 만료 스케줄러(expires_at 경과) ──▶ EXPIRED
 *       └── 사용자 취소(결제 전) ──────────▶ CANCELLED
 * </pre>
 *
 * <p>결제 실패는 주문 상태를 바꾸지 않는다 — {@code payment} 행만 FAILED가 되고 다시 시도할 수 있다.
 * 배송·반품·교환 상태는 이번 범위 밖이다(다음 단계에서 {@code order_product}에 둔다).
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PAYMENT_PENDING("결제 대기"),
    PAID("결제 완료"),
    CANCELLED("취소"),
    EXPIRED("만료");

    private final String label;

    /**
     * 「결제가 된 적 있는」 주문 — 어드민 누적 주문·판매 실적이 세는 범위(선행 수정 계획서 3-4).
     * 배송 상태가 생기면 여기에 더한다. 결제 후 취소된 주문은 {@code order_product.status = CANCELLED}로 걸러진다.
     */
    public static final Set<OrderStatus> PAID_OR_LATER = Set.of(PAID);

    public boolean isClosed() {
        return this == CANCELLED || this == EXPIRED;
    }
}

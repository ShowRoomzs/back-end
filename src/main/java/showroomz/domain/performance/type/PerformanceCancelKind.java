package showroomz.domain.performance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.order.type.OrderCancelType;

/**
 * 취소 분해의 세부(48 파트너 설계서 2-4) — 하위주문 · 항목의 취소 유형을 성과 화면의 단계 이름으로 읽는다. 분실({@code LOST})은 취소가
 * 아니라 배송 예외다({@link PerformanceExceptionKind#LOST}).
 */
@Getter
@RequiredArgsConstructor
public enum PerformanceCancelKind {

    AFTER_PAYMENT("결제 직후"),
    PREPARING("상품준비중"),
    SELLER_DIRECT("직권 취소"),
    OTHER("기타");

    private final String label;

    public static PerformanceCancelKind of(OrderCancelType type) {
        if (type == null) {
            return OTHER;
        }
        return switch (type) {
            case CONSUMER -> AFTER_PAYMENT;
            case REQUEST_APPROVED -> PREPARING;
            case SELLER_DIRECT -> SELLER_DIRECT;
            case LOST -> OTHER;
        };
    }
}

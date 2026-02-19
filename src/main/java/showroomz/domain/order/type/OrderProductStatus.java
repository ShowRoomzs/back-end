package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderProductStatus {
    PENDING("주문 대기"),
    PURCHASE_CONFIRMED("구매 확정"),
    CANCELLED("취소");

    private final String description;
}

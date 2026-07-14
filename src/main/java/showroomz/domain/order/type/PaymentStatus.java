package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 결제 축 상태 (거래 플로우 §10 축 1).
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PENDING("결제대기"),
    PAID("결제완료"),
    FAILED("결제실패"),
    CANCELED("취소·환불");

    private final String description;
}

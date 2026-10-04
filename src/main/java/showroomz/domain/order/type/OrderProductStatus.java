package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderProductStatus {
    /** 결제 대기 — 주문 생성 뒤 결제 확정 전. 결제 만료·취소 시 CANCELLED로 내려간다(결제 계획서 4-1). */
    PENDING("결제 대기"),
    /** 결제 완료 — 주문이 PAID로 전이될 때 함께 바뀐다. 배송 상태는 다음 단계다. */
    PAID("결제 완료"),
    PURCHASE_CONFIRMED("구매 확정"),
    CANCELLED("취소");

    private final String description;
}

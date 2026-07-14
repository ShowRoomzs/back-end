package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 하위주문(공구별) 배송/이행 축 상태 (거래 플로우 §10 축 2).
 * PAID → 브랜드 "준비 시작" 전 구간이 소비자 단순 취소 구간.
 * 반품(요청·회수·반송)은 주문 항목의 취소·반품({@code CancelReturn})에서 추적.
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryStatus {
    PAID("결제완료·준비 전"),
    PREPARING("상품준비중"),
    SHIPPING("배송중"),
    DELIVERED("배송완료"),
    CONFIRMED("구매확정");

    private final String description;
}

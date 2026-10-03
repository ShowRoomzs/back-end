package showroomz.domain.order.repository;

import showroomz.domain.order.entity.OrderDeliveryGroup;

import java.time.LocalDateTime;

/**
 * 목록 한 행 — 그룹 엔티티 + 주문·계약에서 조인으로 읽는 값. 공구명은 계약의 불변 원본에서 읽는다
 * (공구는 복사본을 갖지 않는다 — 30 설계서 0-3).
 */
public record SellerOrderRow(
        OrderDeliveryGroup group,
        String orderNumber,
        LocalDateTime paidAt,
        String recipientName,
        String groupBuyTitle
) {
}

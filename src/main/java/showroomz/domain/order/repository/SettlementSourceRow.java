package showroomz.domain.order.repository;

import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;

import java.time.LocalDateTime;

/**
 * 정산 원천 한 줄 — 공구 하위주문의 주문 항목과 그 하위주문의 종결 사실(44 어드민 설계서 2-2). 정산 모듈이 명세 행
 * ({@code settlement_item})을 만드는 재료다. 주문 모듈은 행을 내주기만 하고 분류 · 집계는 정산이 한다.
 */
public record SettlementSourceRow(
        Long orderProductId,
        Long orderId,
        String orderNumber,
        Long consumerUserId,
        String consumerName,
        String consumerNickname,
        Long deliveryGroupId,
        String subOrderNumber,
        Long productId,
        String productName,
        String optionName,
        Integer quantity,
        Integer returnedQuantity,
        Integer price,
        OrderProductStatus itemStatus,
        OrderCancelType itemCancelType,
        FulfillmentStatus groupStatus,
        OrderCancelType groupCancelType,
        Integer deliveryFee,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime returnCompletedAt) {
}

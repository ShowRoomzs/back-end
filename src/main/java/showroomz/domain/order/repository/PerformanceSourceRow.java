package showroomz.domain.order.repository;

import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;

import java.time.LocalDateTime;

/**
 * 성과 원천 한 줄(48 파트너 설계서 0-6 · 0-7) — 공구 하위주문(결제 완료)의 주문 항목 · 그 하위주문의 이행 시각 · 소비자의 분포 값.
 * {@code attributedPostId}는 결제 완료 시 라스트 터치 게시물(47 공통 결정 #7). 주문 모듈은 행을 내주기만 하고 분류 · 집계는 성과 모듈이 한다. 소비자 식별값은 {@code consumerUserId}뿐이고 집계 밖으로 나가지 않는다.
 */
public record PerformanceSourceRow(
        Long orderProductId,
        Long orderId,
        Long consumerUserId,
        String consumerGender,
        String consumerBirthday,
        LocalDateTime paidAt,
        Long deliveryGroupId,
        Long productId,
        Integer quantity,
        Integer returnedQuantity,
        Integer price,
        OrderProductStatus itemStatus,
        OrderCancelType itemCancelType,
        FulfillmentStatus groupStatus,
        OrderCancelType groupCancelType,
        LocalDateTime shipDueAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime confirmedAt,
        LocalDateTime returnCompletedAt,
        Long attributedPostId) {
}

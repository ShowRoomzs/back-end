package showroomz.api.app.order.service;

import java.time.LocalDateTime;

/**
 * 주문이 PAID 로 전이됐다 — T4 안에서 등록하고 커밋 뒤 발행한다(결제 계획서 4-7). 알림·정산 훅이 붙을 자리다(9-1 ④).
 * 수신자는 {@code @TransactionalEventListener(AFTER_COMMIT)}로 받는다 — 커밋 전에 받으면 아직 없는 주문을 읽는다.
 */
public record OrderPaidEvent(Long orderId, String orderNumber, String paymentId, Long userId, LocalDateTime paidAt) {
}

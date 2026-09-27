package showroomz.api.app.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 후속 — 지금은 로그뿐이다. 결제 완료 알림(푸시·알림함)은 범위 밖이고(9-1 ④) 여기에 붙인다.
 * 여기서 던지는 예외는 결제 확정(T4)을 되돌리지 않는다 — 이미 커밋된 뒤다.
 */
@Slf4j
@Component
public class OrderPaidEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaid(OrderPaidEvent event) {
        log.info("주문 결제 완료 - orderId: {}, orderNumber: {}, paymentId: {}",
                event.orderId(), event.orderNumber(), event.paymentId());
    }
}

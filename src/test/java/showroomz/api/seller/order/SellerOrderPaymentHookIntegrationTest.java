package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 훅의 비정상 경로(보강 시나리오 PAY) — 하위주문은 PAID 전이에서만 태어나고(설계서 5-1), 결제가 안 된 주문은
 * 셀러 화면 밖이며, PG 가 취소를 거절하면 브랜드의 작업이 다시 열린다.
 */
@IntegrationTest
class SellerOrderPaymentHookIntegrationTest extends SellerOrderTestSupport {

    @Test
    @DisplayName("[PAY-01] 소비자 취소를 PG 가 거절하면 결제는 PAID 로 복귀 — 그 뒤 준비 시작이 정상 진행 · 재고 불변 · 앱 취소 버튼 닫힘")
    void preparationReopensAfterPgRejectsCancel() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        int stockBefore = stockOf(creamVariant);
        fake.willFailCancel(group.getOrder().getPaidPaymentId(), FakePaymentGateway.Failure.REJECTED);

        cancel(group.getOrder().getId())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_FAILED"));
        assertThat(payment(group.getOrder().getPaidPaymentId()).getStatus()).isEqualTo(PaymentStatus.PAID);

        prepareStart(group.getId())
                .andExpect(jsonPath("$.succeeded").value(1));

        assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(order(group.getOrder().getId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(creamVariant)).isEqualTo(stockBefore);
        assertThat(historyCount(group, FulfillmentEventType.CANCELLED_BY_CONSUMER)).isZero();
        detail(group.getOrder().getId()).andExpect(jsonPath("$.cancellable").value(false));
    }

    @Test
    @DisplayName("[PAY-02] 웹훅으로만 결제가 확정돼도 하위주문이 태어난다 — 웹훅 중복·complete 재호출에도 PAID 이력은 1회")
    void webhookActivatesOnce() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);

        webhook("wh-hook-1", "Transaction.Paid", created.paymentId()).andExpect(status().isOk());
        webhook("wh-hook-2", "Transaction.Paid", created.paymentId()).andExpect(status().isOk());
        complete(created.paymentId()).andExpect(status().isOk());

        OrderDeliveryGroup group = onlyGroupOf(created.orderId());
        assertThat(group.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(group.getSubOrderNumber()).isEqualTo(group.getOrder().getOrderNumber() + "-01");
        assertThat(group.getShipDueAt()).isEqualTo(group.getOrder().getPaidAt().plusDays(SHIPPING_LEAD_DAYS));
        assertThat(historyCount(group, FulfillmentEventType.PAID)).isEqualTo(1);
        sellerGet(SELLER_ORDERS + "?tab=NEW")
                .andExpect(jsonPath("$.content[*].deliveryGroupId", contains(group.getId().intValue())));
    }

    @Test
    @DisplayName("[PAY-03] 결제 없이 만료된 주문 — 하위주문은 PENDING 그대로 · 셀러 화면 밖(목록·요약 0 · 상세 404 · 액션 제외)")
    void expiredOrderStaysOutOfScreen() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);
        fake.willReturnNotFound(created.paymentId());
        assertThat(expirationService.expire(created.orderId(), LocalDateTime.now().plusMinutes(31))).isTrue();
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        OrderDeliveryGroup group = onlyGroupOf(created.orderId());
        assertThat(group.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PENDING);

        sellerGet(SELLER_ORDERS + "?tab=ALL").andExpect(jsonPath("$.content.length()").value(0));
        sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.tabCounts.ALL").value(0));
        orderDetail(group.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        prepareStart(group.getId())
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));
        sellerPost(SELLER_ORDERS + "/purchase-order",
                Map.of("deliveryGroupIds", List.of(group.getId()), "columns", List.of("RECIPIENT", "PHONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
        directCancel(List.of(group.getId()), "SOLD_OUT", "품절")
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_GROUP_NOT_FOUND"));
        updateShipment(group.getId(), "CJ", "750010002000").andExpect(status().isNotFound());

        assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PENDING);
        assertThat(history(group)).isEmpty();
        assertThat(refundTasks(group)).isEmpty();
    }
}

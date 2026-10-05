package showroomz.api.app.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.payment.portone.PortOneStatus;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수량 일부 신청(보강 시나리오 2절 PQ-01 ~ PQ-10). 앱 API 로 요청하고 판정은 파트너센터 API 로 한다.
 * 판정 대기 N1 · N2 에 걸린 케이스는 권장안을 기대값으로 두고 비활성으로 보관한다 — 현행 동작을 통과로 적지 않는다.
 */
@IntegrationTest
class UserClaimPartialQuantityIntegrationTest extends ClaimTestSupport {

    @Autowired private GroupBuySalesReader salesReader;

    @Test
    @DisplayName("[PQ-01 · PQ-02] 교환 수량 일부 — 3개 중 2개를 다른 옵션으로 요청하면 그 옵션 재고를 2개 잡고 재발송비는 한 번이다. 결제 없이 30분이 지나면 2개가 돌아온다")
    void exchangePartialReservesAndReleases() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 3);
        Long orderProductId = items(group).get(0).getId();

        Map<String, Object> body = claimBody(group, "EXCHANGE", "CHANGE_OF_MIND", orderProductId, 2,
                refill.getVariantId());
        body.put("expectedFee", DELIVERY_FEE);
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.payment.totalAmount").value(DELIVERY_FEE)));
        Long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();

        assertThat(claimRow(claimId)).containsEntry("quantity", 2);
        assertThat(stockOf(refill)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT amount FROM order_claim_charge", Integer.class)).isEqualTo(DELIVERY_FEE);

        fake.willReturnStatus(paymentId, PortOneStatus.FAILED, DELIVERY_FEE);
        claimPaymentService.reconcile(LocalDateTime.now().plusMinutes(31));

        assertThat(count("order_claim")).isZero();
        assertThat(stockOf(refill)).isEqualTo(5);
    }

    @Test
    @DisplayName("[PQ-02] 교환 수량 일부 — 결제까지 끝낸 요청을 철회하면 잡은 2개가 돌아오고 선결제가 취소된다")
    void exchangePartialWithdrawRestores() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 3);
        Map<String, Object> body = claimBody(group, "EXCHANGE", "CHANGE_OF_MIND", items(group).get(0).getId(), 2,
                refill.getVariantId());
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));
        Long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();
        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("REQUESTED"));
        assertThat(stockOf(refill)).isEqualTo(3);

        userPost(USER_CLAIMS + "/" + claimId + "/withdraw", Map.of()).andExpect(status().isOk());

        assertThat(stockOf(refill)).isEqualTo(5);
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("[PQ-03] 교환 수량이 새 옵션 재고보다 많으면 409 — 초안도 재고 변화도 남지 않는다")
    void exchangePartialOutOfStock() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 1);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 3);

        userPost(USER_CLAIMS, claimBody(group, "EXCHANGE", "DAMAGED_OR_DEFECTIVE", items(group).get(0).getId(), 2,
                refill.getVariantId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_EXCHANGE_OUT_OF_STOCK"));

        assertThat(count("order_claim_collection")).isZero();
        assertThat(stockOf(refill)).isEqualTo(1);
    }

    @Test
    @DisplayName("[PQ-04] 3개 중 1개 반품 진행 중에 1개를 교환 요청 — 둘 다 접수되고 남은 수량은 1, 주문 내역은 나중에 신청한 교환으로 보인다")
    void returnAndExchangeOnSameItem() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 3);
        Long orderProductId = items(group).get(0).getId();
        String formUrl = USER_CLAIMS + "/form?orderProductId=" + orderProductId + "&type=RETURN";

        Map<String, Object> returnBody = claimBody(group, "RETURN", "CHANGE_OF_MIND", orderProductId, 1, null);
        returnBody.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
        userPost(USER_CLAIMS, returnBody).andExpect(status().isCreated());
        Long exchangeId = json(userPost(USER_CLAIMS, claimBody(group, "EXCHANGE", "DAMAGED_OR_DEFECTIVE",
                orderProductId, 1, refill.getVariantId())).andExpect(status().isCreated()))
                .get("claimIds").get(0).asLong();

        userGet(formUrl).andExpect(jsonPath("$.items[0].claimableQuantity").value(1));
        detail(group.getOrder().getId())
                .andExpect(jsonPath("$.items[0].claim.claimId").value(exchangeId))
                .andExpect(jsonPath("$.items[0].claim.type").value("EXCHANGE"))
                .andExpect(jsonPath("$.items[0].claim.quantity").value(1));
    }

    @Test
    @DisplayName("[PQ-05] 무료배송 주문(2개)에서 1개 고객 귀책 반품 — 남은 금액이 무료배송 기준 아래로 떨어져도 차감은 주문 시점 배송비 한 번이다")
    void partialReturnKeepsOrderTimeFee() throws Exception {
        freeShippingFrom(50_000);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        Long orderProductId = items(group).get(0).getId();
        userGet(USER_CLAIMS + "/form?orderProductId=" + orderProductId + "&type=RETURN")
                .andExpect(jsonPath("$.fees.consumerFault").value(DELIVERY_FEE));

        Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", orderProductId, 1, null);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
        body.put("expectedFee", DELIVERY_FEE);
        Long claimId = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated()))
                .get("claimIds").get(0).asLong();
        passed(claimId);

        assertThat(collectionRow(collectionIdOf(claimId))).containsEntry("return_deduction", DELIVERY_FEE);
        assertThat(refundTasks(group)).singleElement().satisfies(task ->
                assertThat(((Number) task.get("refund_amount")).intValue()).isEqualTo(CREAM_PRICE - DELIVERY_FEE));
    }

    @Disabled("판정 대기 — 보강 시나리오 N1. 현행은 요청마다 3,000원을 뺀다(두 번째 환불 24,200)")
    @Test
    @DisplayName("[PQ-06] 같은 주문을 두 번에 나눠 고객 귀책 반품 — 배송비 차감은 하위주문당 한 번이다(권장안)")
    void deductionOncePerDeliveryGroup() throws Exception {
        freeShippingFrom(50_000);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 3);
        Long orderProductId = items(group).get(0).getId();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", orderProductId, 1, null);
            body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
            passed(json(userPost(USER_CLAIMS, body).andExpect(status().isCreated())).get("claimIds").get(0).asLong());
        }

        assertThat(refundTasks(group)).extracting(task -> ((Number) task.get("refund_amount")).intValue())
                .containsExactly(CREAM_PRICE - DELIVERY_FEE, CREAM_PRICE);
    }

    @Test
    @DisplayName("[PQ-07] 두 항목 요청에서 한쪽만 수량을 넘기면 요청 전체를 받지 않는다")
    void oneItemExceedsRejectsWhole() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        List<OrderProduct> products = items(group);
        Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", products.get(0).getId(), 1, null);
        body.put("items", List.of(Map.of("orderProductId", products.get(0).getId(), "quantity", 1),
                Map.of("orderProductId", products.get(1).getId(), "quantity", 2)));

        userPost(USER_CLAIMS, body).andExpect(jsonPath("$.code").value("CLAIM_QUANTITY_EXCEEDED"));

        assertThat(count("order_claim")).isZero();
        assertThat(count("order_claim_collection")).isZero();
    }

    @Disabled("판정 대기 — 보강 시나리오 N2. 현행은 같은 항목의 두 줄을 따로 검사해 둘 다 접수한다")
    @Test
    @DisplayName("[PQ-08] 한 요청에 같은 항목을 두 줄로 보내면 400 — 수량 합이 잔여를 넘는 신청이 생기지 않는다(권장안)")
    void duplicateItemRejected() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long orderProductId = items(group).get(0).getId();
        Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", orderProductId, 1, null);
        body.put("items", List.of(Map.of("orderProductId", orderProductId, "quantity", 1),
                Map.of("orderProductId", orderProductId, "quantity", 1)));

        userPost(USER_CLAIMS, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(count("order_claim")).isZero();
    }

    @Test
    @DisplayName("[PQ-09] 수량이 음수 · 숫자가 아님이면 400 — 클레임이 생기지 않는다")
    void invalidQuantityFormat() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        Long orderProductId = items(group).get(0).getId();
        for (Object quantity : List.<Object>of(-1, "abc")) {
            Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", orderProductId, null, null);
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("orderProductId", orderProductId);
            item.put("quantity", quantity);
            body.put("items", new ArrayList<>(List.of(item)));
            userPost(USER_CLAIMS, body).andExpect(status().isBadRequest());
        }
        assertThat(count("order_claim")).isZero();
    }

    @Test
    @DisplayName("[PQ-10] 2개 중 1개 반품이 환불로 끝나면 — 항목은 배송완료 + 반품 1개 · 남은 1개에 [반품 요청]이 남고 · 판매 집계는 1개다")
    void partialRefundedThroughApi() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        Long orderProductId = items(group).get(0).getId();
        Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", orderProductId, 1, null);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
        passed(json(userPost(USER_CLAIMS, body).andExpect(status().isCreated())).get("claimIds").get(0).asLong());
        claimService.completeRefund(refundTaskIds(group).get(0), CREAM_PRICE, 1L, LocalDateTime.now());

        detail(group.getOrder().getId())
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].returnedQuantity").value(1))
                .andExpect(jsonPath("$.items[0].actions[*].type", hasItem("RETURN_REQUEST")));
        userGet(USER_CLAIMS + "/form?orderProductId=" + orderProductId + "&type=RETURN")
                .andExpect(jsonPath("$.items[0].claimableQuantity").value(1));
        assertThat(salesReader.readSales(groupBuy.getId()).orElseThrow().itemQuantities()).singleElement()
                .satisfies(quantity -> assertThat(quantity.quantity()).isEqualTo(1));
    }
}

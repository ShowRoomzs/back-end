package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.type.PaymentCancelStatus;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오 M — <b>주문 → 취소 · 환불, 돈의 행방</b>. 주문이 돈을 돌려주며 끝나는 길을 주문 생성부터 한 파일에서 따라간다.
 *
 * <p>경로별 화면 규칙(탭 · 배지 · 버튼)은 {@code OrderCancelScenarioIntegrationTest} · {@code OrderShippingScenarioIntegrationTest} ·
 * {@code OrderClaimScenarioIntegrationTest}가 본다. 여기는 경로마다 같은 질문 하나를 던진다 — 「받은 돈이 어디로, 얼마나,
 * 누구 손으로 돌아가는가」. 그래서 매 경로의 끝에서 같은 장부({@link #assertLedger})를 맞춘다.
 *
 * <pre>
 * 결제 전 취소          돌려줄 돈이 없다 — PG 호출 없음
 * 결제 후 · 준비 전 취소  PG 전액 취소(payment_cancel) — 환불 큐 없음
 * 준비 후 취소 요청 승인  환불 큐 → 커밋 직후 PG 부분 취소(1009 기획 수정본 2절 · PG 즉시 자동)
 * 브랜드 직권 취소       환불 큐 → PG 부분 취소
 * 배송 중 반송          환불 큐 → PG 부분 취소
 * 배송완료 후 반품       검수 통과 → 환불 큐 → PG 부분 취소
 * </pre>
 *
 * <p>장부의 「남은 환불」은 큐에 올랐지만 아직 돈이 나가지 않은 금액(대기 · 집행 중 · 실패)이다 — PG 자동이라 정상 경로에서는 0 이다.
 */
@IntegrationTest
@DisplayName("[시나리오 M] 주문 → 취소 · 환불 — 돈의 행방")
class OrderToRefundScenarioIntegrationTest extends OrderFlowTestSupport {

    private static final String USER_CLAIMS = "/v1/user/claims";
    private static final String SELLER_CLAIMS = "/v1/seller/claims";


    // ================================================================== 취소

    @Test
    @DisplayName("[M-01] 결제 전 취소 — 예약 재고만 돌아온다 · PG 호출 없음 · 하위주문은 활성화되지 않고 환불 흔적 없음 · 주문 내역에도 없다")
    void cancelBeforePayment() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);
        assertThat(stockOf(creamVariant)).isEqualTo(9);

        cancel(created.orderId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        // 받은 돈이 없으니 돌려줄 것도 없다.
        assertThat(fake.cancelCalls()).isEmpty();
        assertThat(paymentCancelRepository.count()).isZero();
        // 하위주문은 결제 대기로 만들어져 있을 뿐 한 번도 활성화되지 않았다 — 브랜드 화면에 오르지 않는다.
        assertThat(deliveryGroupRepository.findByOrderId(created.orderId()))
                .allSatisfy(group -> assertThat(group.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PENDING));
        sellerOrders(null).andExpect(jsonPath("$.content.length()").value(0));
        assertThat(queuedRefund(created.orderId())).isZero();
        userGet(ORDERS).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));

        // 닫힌 주문은 다시 취소되지 않는다 — 재고가 두 번 늘지 않는다.
        cancel(created.orderId()).andExpect(status().isConflict());
        assertThat(stockOf(creamVariant)).isEqualTo(10);
    }

    @Test
    @DisplayName("[M-02] 결제 후 · 준비 전 취소 — PG 가 배송비까지 전액 환불 · payment_cancel 1행 · 환불 큐 없음 · 앱 「환불 27,200원 · 완료」")
    void cancelRightAfterPayment() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        assertLedger(purchase.orderId(), PaymentStatus.PAID, 0, 0);

        cancel(purchase.orderId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        assertThat(fake.cancelCalls()).containsExactly(purchase.paymentId());
        assertThat(paymentCancelRepository.findByPayment_PaymentIdOrderByIdAsc(purchase.paymentId()))
                .singleElement().satisfies(cancel -> {
                    assertThat(cancel.getStatus()).isEqualTo(PaymentCancelStatus.SUCCEEDED);
                    assertThat(cancel.getAmount()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
                    assertThat(cancel.getPgCancellationId()).isNotNull();
                });
        // 환불은 PG 가 이미 했다 — 큐에 쌓이면 운영자가 한 번 더 환불한다.
        assertLedger(purchase.orderId(), PaymentStatus.CANCELLED, CREAM_PRICE + DELIVERY_FEE, 0);
        assertThat(order(purchase.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reloadGroup(purchase.group()).getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
        assertThat(stockOf(creamVariant)).isEqualTo(10);

        userGet(ORDERS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].items[0].statusSub").value("완료"))
                .andExpect(jsonPath("$.content[0].items[0].amountLabel").value("환불 27,200원"));

        // 두 번째 취소는 PG 로 나가지 않는다.
        cancel(purchase.orderId()).andExpect(status().isConflict());
        assertThat(fake.cancelCalls()).hasSize(1);
    }

    @Test
    @DisplayName("[M-03] 준비 시작 후 — 앱 취소는 409, 취소 요청을 브랜드가 승인하면 PG 가 전액+배송비를 즉시 부분 취소 · 큐 DONE · 전액이라 결제 CANCELLED · 앱 「완료」")
    void cancelRequestApprovedAfterPreparation() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = preparing(purchase.group());

        cancel(purchase.orderId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));
        assertLedger(purchase.orderId(), PaymentStatus.PAID, 0, 0);

        OrderCancelRequest request = seedCancelRequest(group, itemsOf(group));
        // 요청만으로는 돈도 재고도 움직이지 않는다.
        assertLedger(purchase.orderId(), PaymentStatus.PAID, 0, 0);
        assertThat(stockOf(creamVariant)).isEqualTo(9);

        approveRequest(request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CANCEL_REQUEST_APPROVED", CREAM_PRICE + DELIVERY_FEE, "DONE"));
        // 환불은 PG 즉시 자동 — 전액 취소가 아니라 부분 취소로 나간다(주문 전체 취소 연쇄 없음).
        assertThat(fake.cancelCalls()).isEmpty();
        assertThat(fake.partialCancelCalls()).containsExactly(purchase.paymentId() + ":" + (CREAM_PRICE + DELIVERY_FEE));
        assertLedger(purchase.orderId(), PaymentStatus.CANCELLED, CREAM_PRICE + DELIVERY_FEE, 0);
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        userGet(ORDERS).andExpect(jsonPath("$.content[0].items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].items[0].statusSub").value("완료"))
                .andExpect(jsonPath("$.content[0].items[0].amountLabel").value("환불 27,200원"));
    }

    @Test
    @DisplayName("[M-04] 브랜드 직권 취소 — PG 즉시 부분 취소(전액+배송비) · 그 뒤 소비자 취소는 이중 환불 없이 막힌다")
    void sellerDirectCancel() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);

        directCancel(List.of(purchase.group().getId()), "SOLD_OUT", "품절로 발송이 어렵습니다. 전액 환불됩니다.")
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));

        assertThat(refundTasks(purchase.group())).containsExactly(
                new RefundTask("SELLER_DIRECT_CANCEL", CREAM_PRICE + DELIVERY_FEE, "DONE"));
        assertLedger(purchase.orderId(), PaymentStatus.CANCELLED, CREAM_PRICE + DELIVERY_FEE, 0);
        assertThat(stockOf(creamVariant)).isEqualTo(10);

        // 이미 환불된 주문을 소비자가 다시 취소해도 PG 환불이 겹치지 않는다.
        cancel(purchase.orderId()).andExpect(status().isConflict());
        assertThat(fake.cancelCalls()).isEmpty();
        assertThat(fake.partialCancelCalls()).hasSize(1);
        assertLedger(purchase.orderId(), PaymentStatus.CANCELLED, CREAM_PRICE + DELIVERY_FEE, 0);
        assertThat(stockOf(creamVariant)).isEqualTo(10);
    }

    // ================================================================== 발송 뒤

    @Test
    @DisplayName("[M-05] 배송 중 반송 — 입고가 감지되면 PG 부분 취소(전액+배송비) 1회 · 다시 감지돼도 늘지 않는다 · 취소 경로는 전부 닫혀 있다")
    void returnedInTransit() throws Exception {
        OrderDeliveryGroup group = shippingGroup("400050006001");
        Long orderId = group.getOrder().getId();
        LocalDateTime now = LocalDateTime.now();

        track(group, new TrackSnapshot(now, null, true, false), now);
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
        // 반송이 시작된 것만으로는 환불이 서지 않는다 — 브랜드에 돌아와야 한다.
        assertLedger(orderId, PaymentStatus.PAID, 0, 0);

        track(group, new TrackSnapshot(now, null, true, true), now);
        track(group, new TrackSnapshot(now, null, true, true), now);

        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("RETURN_COMPLETED", CREAM_PRICE + DELIVERY_FEE, "DONE"));
        assertLedger(orderId, PaymentStatus.CANCELLED, CREAM_PRICE + DELIVERY_FEE, 0);

        cancel(orderId).andExpect(status().isConflict());
        directCancel(List.of(group.getId()), "SOLD_OUT", "품절")
                .andExpect(jsonPath("$.succeeded").value(0));
        assertThat(fake.cancelCalls()).isEmpty();
        assertThat(fake.partialCancelCalls()).hasSize(1);
        assertLedger(orderId, PaymentStatus.CANCELLED, CREAM_PRICE + DELIVERY_FEE, 0);
    }

    @Test
    @DisplayName("[M-06] 한 주문의 두 갈래 — 크림은 준비 중 부분 취소(PG 27,200), 세럼은 배송완료 뒤 반품(검수 통과 → PG 24,000) · 환불 합계 = 결제액 − 배송비 · 재고는 항목마다 한 번")
    void partialCancelThenReturnOfTheRest() throws Exception {
        // 무료배송 기준을 올려 배송비 3,000 이 붙게 한다 — 부분 취소도 반품도 낸 배송비는 돌려주지 않는다.
        jdbc.update("UPDATE market SET free_shipping_threshold = ? WHERE market_id = ?", 100_000, brand.marketId());
        OrderDeliveryGroup group = preparing(paidGroupWithTwoItems());
        Long orderId = group.getOrder().getId();
        int paid = CREAM_PRICE + SERUM_PRICE + DELIVERY_FEE;
        assertThat(paidAmount(orderId)).isEqualTo(paid);
        assertThat(stockOf(creamVariant)).isEqualTo(9);
        assertThat(stockOf(serumVariant)).isEqualTo(9);

        // ── 크림: 취소 요청 → 승인. 하위주문은 살아 있고 세럼은 그대로 나간다.
        approveRequest(seedCancelRequest(group, List.of(itemOf(group, cream))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREPARING"));
        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CANCEL_REQUEST_APPROVED", CREAM_PRICE, "DONE"));
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        assertLedger(orderId, PaymentStatus.PAID, CREAM_PRICE, 0);

        // ── 세럼: 발송 → 배송완료.
        registerShipment(group, "CJ", "400050006002").andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime deliveredAt = LocalDateTime.now().minusHours(1).withNano(0);
        track(group, new TrackSnapshot(deliveredAt, deliveredAt, false, false), LocalDateTime.now());
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);

        // ── 세럼: 앱 반품 요청(고객 귀책 · 회수 송장 동시). 취소된 크림은 반품 대상이 아니다.
        OrderProduct serumItem = itemOf(group, serum);
        userPost(USER_CLAIMS, returnBody(group, itemOf(group, cream).getId())).andExpect(status().is4xxClientError());
        Long claimId = json(userPost(USER_CLAIMS, returnBody(group, serumItem.getId()))
                .andExpect(status().isCreated())).get("claimIds").get(0).asLong();
        // 요청만으로는 환불이 서지 않는다 — 검수가 통과해야 한다.
        assertLedger(orderId, PaymentStatus.PAID, CREAM_PRICE, 0);

        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        // 검수 통과 = PG 즉시 자동 환불 — 응답 시점에 이미 환불이 끝나 클레임이 종결돼 있다.
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("COMPLETED"));

        // 배송비를 내고 받은 주문 — 반품 환불에서 더 빼는 것은 없다.
        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CANCEL_REQUEST_APPROVED", CREAM_PRICE, "DONE"),
                new RefundTask("CLAIM_RETURN_PASSED", SERUM_PRICE, "DONE"));
        // 크림 재고는 취소 때 한 번 돌아온 그대로다.
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        assertThat(itemOf(group, cream).getStatus()).isEqualTo(OrderProductStatus.CANCELLED);
        assertThat(itemOf(group, serum).getStatus()).isEqualTo(OrderProductStatus.RETURNED);
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.refund.amount").value(SERUM_PRICE))
                .andExpect(jsonPath("$.refund.confirmed").value(true));
        appOrder(orderId)
                .andExpect(jsonPath("$.items[?(@.status == 'CANCELLED')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.status == 'RETURNED')]", hasSize(1)));

        // 돈은 두 갈래(부분 취소 2회)로 나갔고 합쳐도 결제액을 넘지 않는다 — 남는 것은 돌려주지 않는 배송비뿐이다.
        assertLedger(orderId, PaymentStatus.PAID, paid - DELIVERY_FEE, 0);
        assertThat(fake.cancelCalls()).isEmpty();
        assertThat(fake.partialCancelCalls()).hasSize(2);
    }

    // ------------------------------------------------------------------ 장부

    /**
     * 주문 한 건의 돈 — 결제 상태 · PG 가 돌려준 금액(payment_cancel 성공분 — 전액 취소 + 부분 취소) · 큐에 올랐지만 아직 나가지
     * 않은 금액(대기 · 집행 중 · 실패). 둘을 합친 환불이 결제액을 넘으면 이중 환불이다.
     */
    private void assertLedger(Long orderId, PaymentStatus paymentStatus, int pgRefunded, int queued) {
        String paymentId = order(orderId).getPaidPaymentId();
        assertThat(payment(paymentId).getStatus()).isEqualTo(paymentStatus);
        assertThat(pgRefund(paymentId)).isEqualTo(pgRefunded);
        assertThat(queuedRefund(orderId)).isEqualTo(queued);
        assertThat(pgRefunded + queued).isLessThanOrEqualTo(paidAmount(orderId));
    }

    private int paidAmount(Long orderId) {
        return payment(order(orderId).getPaidPaymentId()).getAmount();
    }

    private int pgRefund(String paymentId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payment_cancel WHERE payment_id = ? "
                + "AND status = 'SUCCEEDED'", Integer.class, paymentId);
    }

    private int queuedRefund(Long orderId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(refund_amount), 0) FROM order_refund_task WHERE order_id = ? "
                + "AND status IN ('PENDING', 'EXECUTING', 'FAILED')", Integer.class, orderId);
    }

    // ------------------------------------------------------------------ 요청

    /** 고객 귀책(단순 변심) 반품 · 회수 송장 동시 제출. */
    private Map<String, Object> returnBody(OrderDeliveryGroup group, Long orderProductId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", "RETURN");
        body.put("deliveryGroupId", group.getId());
        body.put("items", List.of(Map.of("orderProductId", orderProductId)));
        body.put("reasonCode", "CHANGE_OF_MIND");
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", "400050006102"));
        return body;
    }

    private ResultActions userGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions userPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}

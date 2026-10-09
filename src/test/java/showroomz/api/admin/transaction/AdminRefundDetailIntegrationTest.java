package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.service.OrderCancelRequestService;
import showroomz.domain.order.service.RefundExecutor;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 환불 관리(06c) 세부 — 43 테스트 상세 4절의 경로 라벨 · 참조 번호(일부 반려 · 반송 완료 · 분실 · 취소 요청 승인 / 자동 승인),
 * 결제 열 · 취소 대상(간편결제 · 부분/전액), 실패 기록의 시도별 목록(자동 재시도 → 운영자 재시도), 같은 하위주문의 환불 둘의 이력 분리,
 * 처리 중 행 · 완료 탭 기간 경계, 요약 합계 · 출처별 건수. 대표 경로는 {@code AdminRefundIntegrationTest}.
 */
@IntegrationTest
class AdminRefundDetailIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_REFUNDS = "/v1/admin/refunds";
    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final int DELIVERY_FEE = 3_000;

    @Autowired private RefundExecutor refundExecutor;
    @Autowired private OrderCancelRequestService cancelRequestService;
    @Autowired private OrderCancelRequestRepository cancelRequestRepository;
    @Autowired private OrderDeliveryGroupRepository deliveryGroupRepository;

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("refunds-detail@showroomz.test", "김운영"));
    }

    // ------------------------------------------------------------------ 경로 라벨

    @Test
    @DisplayName("[OC-L07] 반품 일부 반려 — 통과분 PG 자동 환불은 「반품 · 일부 반려」 · 금액 = 통과분 − 차감 재발송비 · 경로 반품 · 참조 CLM-")
    void partialRejectLabel() throws Exception {
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 2)));
        Map<String, Object> body = new HashMap<>(rejectBody());
        body.put("rejectedQuantity", 1);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", body).andExpect(status().isOk());
        Long split = jdbc.queryForObject("SELECT claim_id FROM order_claim WHERE split_from_claim_id = ?", Long.class, claimId);
        int fee = jdbc.queryForObject("SELECT amount FROM order_claim_charge WHERE collection_id = ?", Integer.class,
                collectionIdOf(split));

        JsonNode row = only(rows("?tab=DONE&route=RETURN"));
        assertThat(row.get("sourceLabel").asText()).isEqualTo("반품 · 일부 반려");
        assertThat(row.get("route").asText()).isEqualTo("RETURN");
        assertThat(row.get("sourceRef").asText()).startsWith("CLM-");
        assertThat(row.get("originLabel").asText()).isEqualTo("PG 자동");
        assertThat(row.get("amount").asInt()).isEqualTo(CREAM_PRICE - fee);
        assertThat(row.get("statusLabel").asText()).isEqualTo("환불 완료");
        assertThat(row.get("statusNote").isNull() || row.get("statusNote").asText().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("[OC-L07] 반송 완료 · 분실 처리 — 「반송 완료」 경로 반송 · 「배송 분실 처리」 경로 취소 · 둘 다 PG 자동 · 하위주문 전액")
    void returnShipmentAndLostLabels() throws Exception {
        OrderDeliveryGroup returning = shipped(prepared(paidGroup(creamVariant, 1)), "CJ", newInvoice());
        LocalDateTime now = LocalDateTime.now();
        track(returning, new TrackSnapshot(now, null, true, false), now);
        track(returning, new TrackSnapshot(now, null, true, true), now);

        OrderDeliveryGroup lost = shipped(prepared(paidGroup(creamVariant, 1)), "CJ", newInvoice());
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ?, shipped_at = ? "
                + "WHERE delivery_group_id = ?", now.minusDays(29), now.minusDays(30), lost.getId());
        adminPost(ADMIN_ORDERS + "/groups/" + lost.getId() + "/lost", Map.of("reason", "택배사 분실 확인")).andExpect(status().isOk());

        JsonNode returnRow = only(rows("?tab=DONE&route=RETURN_SHIPMENT"));
        assertThat(returnRow.get("sourceLabel").asText()).isEqualTo("반송 완료");
        assertThat(returnRow.get("deliveryGroupId").asLong()).isEqualTo(returning.getId());
        JsonNode lostRow = only(rows("?tab=DONE&route=CANCEL"));
        assertThat(lostRow.get("sourceLabel").asText()).isEqualTo("배송 분실 처리");
        assertThat(lostRow.get("deliveryGroupId").asLong()).isEqualTo(lost.getId());
        assertThat(lostRow.get("amount").asInt()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        assertThat(lostRow.get("paymentLabel").asText()).isEqualTo("카드 · 원래");
        for (JsonNode row : List.of(returnRow, lostRow)) {
            assertThat(row.get("origin").asText()).isEqualTo("PG_AUTO");
        }
    }

    @Test
    @DisplayName("[OC-L07] 취소 요청 — 브랜드 승인은 「취소 요청 승인」 · 응답 기한 경과는 「취소 요청 자동 승인」 · 참조 CRQ-{요청 id}")
    void cancelRequestLabels() throws Exception {
        OrderDeliveryGroup manual = prepared(paidGroup(creamVariant, 1));
        long manualRequest = cancelRequest(manual);
        sellerPost(SELLER_ORDERS + "/cancel-requests/" + manualRequest + "/approve", Map.of()).andExpect(status().isOk());

        OrderDeliveryGroup auto = prepared(paidGroup(creamVariant, 1));
        long autoRequest = cancelRequest(auto);
        OrderCancelRequest request = cancelRequestRepository.findById(autoRequest).orElseThrow();
        assertThat(cancelRequestService.autoApproveIfOverdue(autoRequest, request.getRespondDueAt().plusMinutes(1))).isTrue();

        List<JsonNode> rows = rows("?tab=DONE&route=CANCEL");
        assertThat(rowOfGroup(rows, manual).get("sourceLabel").asText()).isEqualTo("취소 요청 승인");
        assertThat(rowOfGroup(rows, manual).get("sourceRef").asText()).isEqualTo("CRQ-" + manualRequest);
        assertThat(rowOfGroup(rows, auto).get("sourceLabel").asText()).isEqualTo("취소 요청 자동 승인");
        assertThat(rowOfGroup(rows, auto).get("sourceRef").asText()).isEqualTo("CRQ-" + autoRequest);
        long taskId = rowOfGroup(rows, auto).get("refundTaskId").asLong();
        JsonNode detail = json(adminGet(ADMIN_REFUNDS + "/" + taskId).andExpect(status().isOk()));
        assertThat(detail.at("/source/cancelRequestId").asLong()).isEqualTo(autoRequest);
        assertThat(detail.at("/source/label").asText()).isEqualTo("취소 요청 자동 승인");
    }

    // ------------------------------------------------------------------ 결제 열 · 취소 대상

    @Test
    @DisplayName("[OC-L08] 간편결제 — 목록 「간편결제 · 원래 부분」 · 상세 수단 「카카오페이」 · 결제액 · 취소 가능액 · 전액이면 「부분」 없음")
    void easyPayPaymentColumns() throws Exception {
        OrderDeliveryGroup group = easyPayDeliveredGroup();
        int paid = CREAM_PRICE + DELIVERY_FEE;
        long partialTask = operatorRefund(group, 5_000);

        JsonNode row = rowOfTask(rows(""), partialTask);
        assertThat(row.get("paymentLabel").asText()).isEqualTo("간편결제 · 원래 부분");
        assertThat(row.get("partial").asBoolean()).isTrue();
        JsonNode target = json(adminGet(ADMIN_REFUNDS + "/" + partialTask)).get("target");
        assertThat(target.get("methodLabel").asText()).isEqualTo("카카오페이");
        assertThat(target.get("paymentAmount").asInt()).isEqualTo(paid);
        assertThat(target.get("cancelledAmount").asInt()).isZero();
        assertThat(target.get("cancellableAmount").asInt()).isEqualTo(paid);
        assertThat(target.get("amount").asInt()).isEqualTo(5_000);
        assertThat(target.get("paymentKind").asText()).isEqualTo("ORIGINAL");
        assertThat(target.get("paymentStatus").asText()).isEqualTo("PAID");

        adminPost(ADMIN_REFUNDS + "/" + partialTask + "/void", Map.of("reason", "전액으로 다시")).andExpect(status().isOk());
        long fullTask = operatorRefund(group, paid);
        JsonNode full = rowOfTask(rows(""), fullTask);
        assertThat(full.get("paymentLabel").asText()).isEqualTo("간편결제 · 원래");
        assertThat(full.get("partial").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------ 실패 기록

    @Test
    @DisplayName("[OC-D03 · OC-L02] 실패 기록 — 자동 재시도 실패가 시도별로 쌓인다(SYSTEM) · 운영자 재시도 실패는 ADMIN · 상태 문구 「재시도 N회 실패」 · 코드는 상세에만")
    void failureAttemptsAccumulate() throws Exception {
        OrderDeliveryGroup group = paidGroup(creamVariant, 1);
        String paymentId = paymentIdOf(group);
        fake.willFailCancel(paymentId, FakePaymentGateway.Failure.REJECTED);
        directCancel(List.of(group.getId()), "SOLD_OUT", "품절").andExpect(status().isOk());
        long taskId = jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ?",
                Long.class, group.getId());
        assertThat(rowOfTask(rows("?tab=FAILED"), taskId).get("statusNote").asText()).doesNotContain("재시도");

        assertThat(refundExecutor.execute(taskId, null)).isEqualTo(RefundExecutor.Outcome.FAILED);
        JsonNode afterAuto = rowOfTask(rows("?tab=FAILED"), taskId);
        assertThat(afterAuto.get("attempt").asInt()).isEqualTo(2);
        assertThat(afterAuto.get("statusNote").asText()).contains("PG 거절").endsWith("재시도 1회 실패");

        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(status().isOk());
        JsonNode failure = json(adminGet(ADMIN_REFUNDS + "/" + taskId)).get("failure");
        assertThat(failure.get("code").asText()).isEqualTo("PG_PROVIDER");
        assertThat(failure.get("message").asText()).contains("PG 거절");
        assertThat(failure.get("attempts")).extracting(attempt -> attempt.get("actorType").asText())
                .containsExactly("SYSTEM", "SYSTEM", "ADMIN");
        assertThat(failure.get("attempts")).allSatisfy(attempt -> assertThat(attempt.get("message").asText()).contains("PG 거절"));
        assertThat(rowOfTask(rows("?tab=FAILED"), taskId).get("statusNote").asText()).endsWith("재시도 2회 실패");
        assertThat(rowOfTask(rows("?tab=FAILED"), taskId).has("lastErrorCode")).isFalse();
    }

    // ------------------------------------------------------------------ 이력 분리

    @Test
    @DisplayName("[OC-D05] 같은 하위주문의 환불 둘 — 각 상세의 이력은 자기 환불번호 것만(편입 · 집행이 섞이지 않는다)")
    void historyPerRefundTask() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        long first = operatorRefund(group, 3_000);
        long second = operatorRefund(group, 2_000);
        adminPost(ADMIN_REFUNDS + "/" + first + "/execute", Map.of()).andExpect(status().isOk());

        JsonNode firstHistory = json(adminGet(ADMIN_REFUNDS + "/" + first)).get("history");
        JsonNode secondHistory = json(adminGet(ADMIN_REFUNDS + "/" + second)).get("history");
        assertThat(firstHistory).extracting(h -> h.get("type").asText())
                .containsExactly("REFUND_ENQUEUED_BY_OPERATOR", "REFUND_EXECUTED");
        assertThat(secondHistory).extracting(h -> h.get("type").asText()).containsExactly("REFUND_ENQUEUED_BY_OPERATOR");
        assertThat(firstHistory.get(0).get("detail").asText()).contains("3,000원");
        assertThat(secondHistory.get(0).get("detail").asText()).contains("2,000원");
    }

    // ------------------------------------------------------------------ 처리 중 행 · 기간 경계

    @Test
    @DisplayName("[OC-L01 · L03] 처리 중 행 — 집행 대기 탭 「환불 처리 중 · PG 응답 확인 중」 · 버튼 셋 다 없음 / 완료 탭 days — 0 은 1로 · 경계 안팎")
    void executingRowAndDoneWindow() throws Exception {
        long executing = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        jdbc.update("UPDATE order_refund_task SET status = 'EXECUTING' WHERE refund_task_id = ?", executing);
        JsonNode row = rowOfTask(rows(""), executing);
        assertThat(row.get("statusLabel").asText()).isEqualTo("환불 처리 중");
        assertThat(row.get("statusNote").asText()).isEqualTo("PG 응답 확인 중");
        assertThat(row.get("executable").asBoolean()).isFalse();
        assertThat(row.get("voidable").asBoolean()).isFalse();
        assertThat(row.get("manuallyCompletable").asBoolean()).isFalse();

        long recent = operatorRefund(deliveredGroup(creamVariant, 1), 4_000);
        long older = operatorRefund(deliveredGroup(creamVariant, 1), 3_000);
        adminPost(ADMIN_REFUNDS + "/" + recent + "/execute", Map.of()).andExpect(status().isOk());
        adminPost(ADMIN_REFUNDS + "/" + older + "/execute", Map.of()).andExpect(status().isOk());
        jdbc.update("UPDATE order_refund_task SET executed_at = ? WHERE refund_task_id = ?", LocalDateTime.now().minusHours(1), recent);
        jdbc.update("UPDATE order_refund_task SET executed_at = ? WHERE refund_task_id = ?", LocalDateTime.now().minusDays(2), older);

        assertThat(taskIds(rows("?tab=DONE&days=0"))).containsExactly(recent);
        assertThat(taskIds(rows("?tab=DONE&days=1"))).containsExactly(recent);
        assertThat(taskIds(rows("?tab=DONE&days=3"))).containsExactly(recent, older);
        assertThat(json(adminGet(ADMIN_REFUNDS + "/summary?days=0")).at("/tabs/DONE/count").asLong()).isEqualTo(1);
        assertThat(json(adminGet(ADMIN_REFUNDS + "/summary?days=0")).at("/tabs/DONE/days").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 요약

    @Test
    @DisplayName("[OC-L10] 요약 — 대기(건수 · 합계 · 처리 중 포함) · 실패(합계) · 완료(합계 · 출처별) · 배지 = 대기 + 실패 · 각 값이 DB 와 같다")
    void summaryTotals() throws Exception {
        operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        long executing = operatorRefund(deliveredGroup(creamVariant, 1), 7_000);
        jdbc.update("UPDATE order_refund_task SET status = 'EXECUTING' WHERE refund_task_id = ?", executing);
        long operatorDone = operatorRefund(deliveredGroup(creamVariant, 1), 3_000);
        adminPost(ADMIN_REFUNDS + "/" + operatorDone + "/execute", Map.of()).andExpect(status().isOk());
        passed(returnClaim(deliveredGroup(creamVariant, 1)));
        OrderDeliveryGroup failing = paidGroup(creamVariant, 1);
        fake.willFailCancel(paymentIdOf(failing), FakePaymentGateway.Failure.REJECTED);
        directCancel(List.of(failing.getId()), "SOLD_OUT", "품절").andExpect(status().isOk());

        JsonNode tabs = json(adminGet(ADMIN_REFUNDS + "/summary").andExpect(status().isOk())).get("tabs");
        assertThat(tabs.at("/PENDING/count").asLong()).isEqualTo(2);
        assertThat(tabs.at("/PENDING/amount").asLong()).isEqualTo(12_000);
        assertThat(tabs.at("/FAILED/count").asLong()).isEqualTo(1);
        assertThat(tabs.at("/FAILED/amount").asLong()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        assertThat(tabs.at("/DONE/count").asLong()).isEqualTo(2);
        assertThat(tabs.at("/DONE/amount").asLong()).isEqualTo(jdbc.queryForObject(
                "SELECT SUM(refund_amount) FROM order_refund_task WHERE status = 'DONE'", Long.class));
        assertThat(tabs.at("/DONE/byOrigin/PG_AUTO").asLong()).isEqualTo(1);
        assertThat(tabs.at("/DONE/byOrigin/OPERATOR").asLong()).isEqualTo(1);
        assertThat(json(adminGet(ADMIN_REFUNDS + "/summary")).get("badge").asLong()).isEqualTo(3);
        assertThat(json(adminGet(ADMIN_REFUNDS + "?tab=PENDING")).at("/pageInfo/totalResults").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("[OC-A11 경계] 수동 완료 기록 — 취소번호 100자 · 근거 500자를 채워도 기록되고 이력에 둘 다 잘리지 않고 남는다")
    void manualCompleteMaxInputs() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        long taskId = operatorRefund(group, 5_000);
        String pgCancellationId = "c".repeat(100);
        String note = "포트원 콘솔에서 부분 취소 " + "다".repeat(500 - "포트원 콘솔에서 부분 취소 ".length());

        adminPost(ADMIN_REFUNDS + "/" + taskId + "/manual-complete", Map.of("pgCancellationId", pgCancellationId, "note", note))
                .andExpect(status().isOk());

        String history = jdbc.queryForObject("SELECT detail FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = 'REFUND_RECORDED_MANUALLY'", String.class, group.getId());
        assertThat(history).startsWith("RFD-" + taskId + " · ").contains(pgCancellationId).endsWith(note);
        assertThat(jdbc.queryForObject("SELECT pg_cancellation_id FROM payment_cancel WHERE refund_task_id = ?", String.class,
                taskId)).isEqualTo(pgCancellationId);
    }

    // ------------------------------------------------------------------ 도우미

    private OrderDeliveryGroup easyPayDeliveredGroup() throws Exception {
        Created created = created(createOrder(directOrder(newKey(), creamVariant, 1)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        complete(created.paymentId()).andExpect(status().isOk());
        OrderDeliveryGroup group = deliveryGroupRepository.findByOrderId(created.orderId()).get(0);
        return delivered(shipped(prepared(group), "CJ", newInvoice()), LocalDateTime.now().minusDays(1));
    }

    private long cancelRequest(OrderDeliveryGroup group) throws Exception {
        Long orderId = jdbc.queryForObject("SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?", Long.class,
                group.getId());
        return json(mockMvc.perform(post("/v1/user/orders/" + orderId + "/cancel-requests")
                .header(HttpHeaders.AUTHORIZATION, consumerToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("deliveryGroupId", group.getId(), "orderProductIds", List.of(items(group).get(0).getId()),
                        "reasonCode", "CHANGE_OF_MIND")))).andExpect(status().isCreated())).get("cancelRequestId").asLong();
    }

    private long operatorRefund(OrderDeliveryGroup group, int amount) throws Exception {
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "RECALL", "amount", amount, "detail", "위해성 리콜")).andExpect(status().isOk());
        return jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task WHERE delivery_group_id = ?",
                Long.class, group.getId());
    }

    private String paymentIdOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT o.paid_payment_id FROM orders o JOIN order_delivery_group g "
                + "ON g.order_id = o.order_id WHERE g.delivery_group_id = ?", String.class, group.getId());
    }

    private List<JsonNode> rows(String query) throws Exception {
        JsonNode content = json(adminGet(ADMIN_REFUNDS + query).andExpect(status().isOk())).get("content");
        return StreamSupport.stream(content.spliterator(), false).toList();
    }

    private static List<Long> taskIds(List<JsonNode> rows) {
        return rows.stream().map(row -> row.get("refundTaskId").asLong()).toList();
    }

    private static JsonNode only(List<JsonNode> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static JsonNode rowOfTask(List<JsonNode> rows, long taskId) {
        return rows.stream().filter(row -> row.get("refundTaskId").asLong() == taskId).findFirst()
                .orElseThrow(() -> new AssertionError("행 없음: RFD-" + taskId));
    }

    private static JsonNode rowOfGroup(List<JsonNode> rows, OrderDeliveryGroup group) {
        return rows.stream().filter(row -> row.get("deliveryGroupId").asLong() == group.getId()).findFirst()
                .orElseThrow(() -> new AssertionError("행 없음: 하위주문 " + group.getId()));
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}

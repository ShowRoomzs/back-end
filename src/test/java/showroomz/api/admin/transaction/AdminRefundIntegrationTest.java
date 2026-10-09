package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 환불 관리(06c · 39 설계서 6절 P1) — 탭 · 검색 3축 · 경로 · 정렬 · 요약 · 상세(M1 · M2) · 집행 · 재시도 · 결제 없음 · 권한.
 * 큐 행은 실제 경로(운영자 사유 편입 · 반품 검수 통과 · 직권 취소 · 반려 이의 인용)로만 만들고, SQL 은 시각 소급과 결제 분리에만 쓴다.
 */
@IntegrationTest
class AdminRefundIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_REFUNDS = "/v1/admin/refunds";
    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");

    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpAdmin() {
        operator = fixture.createAdmin("refunds-ops@showroomz.test", "김운영");
        admin = adminToken(operator);
    }

    // ------------------------------------------------------------------ 탭

    @Test
    @DisplayName("[R-01] 탭 — 집행 대기는 운영자 사유만 · 완료 탭 기간은 집행 시각 · 소멸은 어느 탭에도 없다")
    void tabs() throws Exception {
        long pending = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        Long passedClaim = passed(returnClaim(deliveredGroup(creamVariant, 1)));
        long pgAuto = taskIdOfSource("CLAIM_RETURN_PASSED");
        assertThat(claimStatus(passedClaim)).isEqualTo("COMPLETED");

        assertThat(ids(adminGet(ADMIN_REFUNDS))).containsExactly(pending);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE"))).containsExactly(pgAuto);

        // 오래전에 적재돼 오늘 집행된 건도 완료 탭에 보인다 — 기간은 집행 시각.
        jdbc.update("UPDATE order_refund_task SET created_at = ? WHERE refund_task_id = ?",
                LocalDateTime.now().minusDays(90), pgAuto);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE"))).containsExactly(pgAuto);
        jdbc.update("UPDATE order_refund_task SET executed_at = ? WHERE refund_task_id = ?",
                LocalDateTime.now().minusDays(40), pgAuto);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE"))).isEmpty();
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE&days=60"))).containsExactly(pgAuto);

        jdbc.update("UPDATE order_refund_task SET status = 'VOID' WHERE refund_task_id = ?", pending);
        for (String tab : List.of("PENDING", "FAILED", "DONE")) {
            assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=" + tab + "&days=60"))).doesNotContain(pending);
        }
        adminGet(ADMIN_REFUNDS + "/" + pending).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID"));
    }

    // ------------------------------------------------------------------ 검색 · 경로 · 정렬

    @Test
    @DisplayName("[R-02] 검색 — 환불번호(RFD- · 숫자) · 주문번호 · PG 거래번호(paymentId · pgTxId) · RFD-abc 는 0건")
    void search() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        long target = operatorRefund(group, 5_000);
        operatorRefund(deliveredGroup(creamVariant, 1), 6_000);
        String orderNumber = jdbc.queryForObject("SELECT order_number FROM orders WHERE order_id = ?", String.class,
                group.getOrder().getId());
        String paymentId = paymentIdOf(group);
        jdbc.update("UPDATE payment SET pg_tx_id = 'tosspay_9c41e0b2' WHERE payment_id = ?", paymentId);

        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=RFD-" + target))).containsExactly(target);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=" + target))).containsExactly(target);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=" + orderNumber))).containsExactly(target);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=" + paymentId))).containsExactly(target);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=tosspay_9c41e0b2"))).containsExactly(target);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=RFD-abc"))).isEmpty();
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=없는번호"))).isEmpty();
    }

    @Test
    @DisplayName("[R-03] 경로 · 정렬 — route 가 발생 경로로 바뀌고 환불액 높은순은 금액 · id 역순")
    void routeAndSort() throws Exception {
        long small = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        long large = operatorRefund(deliveredGroup(creamVariant, 1), 9_000);
        passed(returnClaim(deliveredGroup(creamVariant, 1)));
        long returned = taskIdOfSource("CLAIM_RETURN_PASSED");

        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=RETURN"))).containsExactly(returned);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=OPERATOR"))).isEmpty();
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?route=OPERATOR&sort=AMOUNT_DESC"))).containsExactly(large, small);
        assertThat(ids(adminGet(ADMIN_REFUNDS))).containsExactly(large, small);

        JsonNode row = json(adminGet(ADMIN_REFUNDS + "?tab=DONE")).get("content").get(0);
        assertThat(row.get("route").asText()).isEqualTo("RETURN");
        assertThat(row.get("sourceLabel").asText()).isEqualTo("반품 검수 통과");
        assertThat(row.get("sourceRef").asText()).startsWith("CLM-");
        assertThat(row.get("originLabel").asText()).isEqualTo("PG 자동");
        assertThat(row.get("paymentLabel").asText()).isEqualTo("카드 · 원래 부분");
        assertThat(row.get("refundNo").asText()).isEqualTo("RFD-" + returned);
    }

    @Test
    @DisplayName("[R-04] 경로 라벨 — 운영자 대행 직권 취소와 브랜드 직권 취소를 가른다")
    void directCancelLabels() throws Exception {
        OrderDeliveryGroup byBrand = paidGroup(creamVariant, 1);
        directCancel(List.of(byBrand.getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(1));
        OrderDeliveryGroup byAdmin = paidGroup(creamVariant, 1);
        adminPost(ADMIN_ORDERS + "/groups/" + byAdmin.getId() + "/cancel",
                Map.of("reasonCode", "DEFECT", "consumerMessage", "위해성 회수")).andExpect(status().isOk());

        JsonNode rows = json(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=CANCEL")).get("content");
        assertThat(rowOfGroup(rows, byBrand).get("sourceLabel").asText()).isEqualTo("브랜드 직권 취소");
        assertThat(rowOfGroup(rows, byAdmin).get("sourceLabel").asText()).isEqualTo("운영자 대행 직권 취소");
    }

    // ------------------------------------------------------------------ 요약

    @Test
    @DisplayName("[R-05] 요약 — 탭 건수 = 목록 건수 · 합계 · 소비자 대기 일수 · 배지 = 대기 + 실패")
    void refundSummary() throws Exception {
        operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        operatorRefund(deliveredGroup(creamVariant, 1), 7_000);
        long failed = failedRefund();
        jdbc.update("UPDATE order_refund_task SET created_at = ? WHERE refund_task_id = ?",
                LocalDateTime.now().minusDays(3).minusHours(1), failed);

        JsonNode summary = json(adminGet(ADMIN_REFUNDS + "/summary").andExpect(status().isOk()));
        assertThat(summary.at("/tabs/PENDING/count").asLong()).isEqualTo(2);
        assertThat(summary.at("/tabs/PENDING/amount").asLong()).isEqualTo(12_000);
        assertThat(summary.at("/tabs/FAILED/count").asLong()).isEqualTo(1);
        assertThat(summary.at("/tabs/FAILED/oldestWaitingDays").asInt()).isEqualTo(3);
        assertThat(summary.at("/tabs/DONE/days").asInt()).isEqualTo(30);
        assertThat(summary.get("badge").asLong()).isEqualTo(3);
        assertThat(json(adminGet(ADMIN_REFUNDS)).at("/pageInfo/totalResults").asLong()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ 상세 · 집행

    @Test
    @DisplayName("[R-06] 상세 · 집행 — 취소 대상 결제 · 운영자 이름 · 정산 자리 · 이력 → 집행하면 완료 · 집행자 · 재확인")
    void detailAndExecute() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        long taskId = operatorRefund(group, 5_000);

        JsonNode row = json(adminGet(ADMIN_REFUNDS)).get("content").get(0);
        assertThat(row.get("statusNote").asText()).startsWith("편입 ").endsWith(" · 김운영");
        assertThat(row.get("executable").asBoolean()).isTrue();

        JsonNode detail = json(adminGet(ADMIN_REFUNDS + "/" + taskId).andExpect(status().isOk()));
        assertThat(detail.at("/target/paymentId").asText()).isEqualTo(paymentIdOf(group));
        assertThat(detail.at("/target/methodLabel").asText()).isEqualTo("신한카드");
        assertThat(detail.at("/target/partial").asBoolean()).isTrue();
        assertThat(detail.at("/target/cancellableAmount").asInt()).isPositive();
        assertThat(detail.at("/reason/code").asText()).isEqualTo("RECALL");
        assertThat(detail.at("/reason/requestedByName").asText()).isEqualTo("김운영");
        assertThat(detail.at("/settlement/state").asText()).isEqualTo("BEFORE_SETTLEMENT");
        assertThat(detail.get("additionalPayments")).isEmpty();
        assertThat(detail.get("history")).hasSize(1);
        assertThat(detail.at("/history/0/type").asText()).isEqualTo("REFUND_ENQUEUED_BY_OPERATOR");
        assertThat(detail.at("/history/0/actorName").asText()).isEqualTo("김운영");

        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DONE"))
                .andExpect(jsonPath("$.refund.statusNote").value("집행 김운영 · 재확인"));
        assertThat(jdbc.queryForMap("SELECT executed_by FROM order_refund_task WHERE refund_task_id = ?", taskId))
                .containsEntry("executed_by", operator.getId());
        assertThat(jdbc.queryForObject("SELECT requested_by FROM payment_cancel WHERE refund_task_id = ?", String.class,
                taskId)).isEqualTo("ADMIN");
        JsonNode done = json(adminGet(ADMIN_REFUNDS + "/" + taskId));
        assertThat(done.at("/execution/executedByName").asText()).isEqualTo("김운영");
        assertThat(done.get("history")).extracting(h -> h.get("type").asText())
                .containsExactly("REFUND_ENQUEUED_BY_OPERATOR", "REFUND_EXECUTED");

        // 순차 두 번째 집행은 409 — 돈은 한 번만 나갔다.
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_EXECUTABLE"));
        assertThat(fake.partialCancelCalls()).hasSize(1);
        assertThat(jdbc.queryForMap("SELECT payment_kind, partial_cancel FROM order_refund_task WHERE refund_task_id = ?",
                taskId)).containsEntry("payment_kind", "ORIGINAL").containsEntry("partial_cancel", true);
    }

    @Test
    @DisplayName("[R-07] 상세 — 결제된 재발송비 뒤의 반려 이의 인용은 추가 결제에 「인용 시 결제 취소됨」")
    void disputeAdditionalPayment() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(status().isOk());
        long taskId = json(adminPost("/v1/admin/claims/" + claimId + "/dispute-acceptance",
                Map.of("detail", "배송 시점 오염")).andExpect(status().isOk())).get("refundTaskId").asLong();

        JsonNode detail = json(adminGet(ADMIN_REFUNDS + "/" + taskId));
        assertThat(detail.at("/source/route").asText()).isEqualTo("OPERATOR");
        assertThat(detail.at("/source/label").asText()).isEqualTo("반려 이의 인용");
        assertThat(detail.at("/source/claimId").asLong()).isEqualTo(claimId);
        assertThat(detail.at("/source/ref").asText()).isEqualTo("CLM-" + claimId);
        assertThat(detail.at("/additionalPayments/0/status").asText()).isEqualTo("REFUNDED");
        assertThat(detail.at("/additionalPayments/0/note").asText()).isEqualTo("인용 시 결제 취소됨");
        assertThat(detail.at("/additionalPayments/0/claimPaymentStatus").asText()).isEqualTo("CANCELLED");
        assertThat(detail.get("history")).extracting(h -> h.get("type").asText())
                .contains("REFUND_ENQUEUED_BY_OPERATOR", "DISPUTE_ACCEPTED");
    }

    @Test
    @DisplayName("[R-08] 실패 → 재시도 — 실패 탭 · 상세 실패 기록 · 이번엔 PG 성공이면 완료 · 시도 2회")
    void failedThenRetried() throws Exception {
        long taskId = failedRefund();

        JsonNode row = json(adminGet(ADMIN_REFUNDS + "?tab=FAILED")).get("content").get(0);
        assertThat(row.get("refundTaskId").asLong()).isEqualTo(taskId);
        assertThat(row.get("statusNote").asText()).contains("PG 거절");
        JsonNode detail = json(adminGet(ADMIN_REFUNDS + "/" + taskId));
        assertThat(detail.at("/failure/message").asText()).contains("PG 거절");
        assertThat(detail.at("/failure/attempts")).isNotEmpty();
        // PG 응답 코드는 상세에만(V177 · 39 설계서 0-12) — 목록 행에는 없다.
        assertThat(detail.at("/failure/code").asText()).isEqualTo("PG_PROVIDER");
        assertThat(row.has("lastErrorCode")).isFalse();
        // 직권 취소 환불 = 하위주문 전액(항목 + 배송비) = 결제 전액 — 부분 취소가 아니다.
        assertThat(row.get("partial").asBoolean()).isFalse();
        assertThat(row.get("paymentLabel").asText()).isEqualTo("카드 · 원래");

        String paymentId = jdbc.queryForObject("SELECT payment_id FROM order_refund_task WHERE refund_task_id = ?",
                String.class, taskId);
        fake.willAnswerCancel(paymentId, PortOneCancelResult.Outcome.SUCCEEDED);
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(jsonPath("$.outcome").value("DONE"));
        assertThat(jdbc.queryForObject("SELECT attempt FROM order_refund_task WHERE refund_task_id = ?", Integer.class,
                taskId)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM order_refund_task WHERE refund_task_id = ?",
                String.class, taskId)).isNull();
        adminGet(ADMIN_REFUNDS + "/summary").andExpect(jsonPath("$.tabs.FAILED.count").value(0));
    }

    @Test
    @DisplayName("[R-09] 결제 없는 주문 — 목록 executable=false · 집행 409")
    void noPayment() throws Exception {
        long taskId = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        jdbc.update("UPDATE order_refund_task SET payment_id = NULL WHERE refund_task_id = ?", taskId);

        assertThat(json(adminGet(ADMIN_REFUNDS)).at("/content/0/executable").asBoolean()).isFalse();
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_EXECUTABLE"));
        adminGet(ADMIN_REFUNDS + "/999999").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[R-10] 권한 — 셀러 토큰 403")
    void sellerForbidden() throws Exception {
        mockMvc.perform(get(ADMIN_REFUNDS).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ P3 비큐 경로 기록 행

    @Test
    @DisplayName("[R-11] 결제완료 소비자 취소 — 하위주문마다 DONE 기록 행 · 합 = 결제액 · 부분 아님 · 집행기 미호출 · 「환불 처리 중」 아님")
    void consumerCancelRecorded() throws Exception {
        OrderDeliveryGroup group = paidTwoItemGroup();
        Long orderId = group.getOrder().getId();
        String paymentId = paymentIdOf(group);
        int paid = jdbc.queryForObject("SELECT amount FROM payment WHERE payment_id = ?", Integer.class, paymentId);

        cancel(orderId).andExpect(status().is2xxSuccessful());

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM order_refund_task WHERE order_id = ?", orderId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("source", "USER_CANCEL_BEFORE_PREPARE").containsEntry("status", "DONE")
                .containsEntry("origin", "PG_AUTO").containsEntry("partial_cancel", false)
                .containsEntry("payment_kind", "ORIGINAL").containsEntry("refund_amount", paid);
        assertThat(rows.get(0).get("payment_cancel_id")).isNotNull();
        assertThat(fake.partialCancelCalls()).isEmpty();
        assertThat(refundTaskRepositoryPending(orderId)).isEmpty();

        JsonNode row = json(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=CANCEL")).get("content").get(0);
        assertThat(row.get("sourceLabel").asText()).isEqualTo("결제완료 소비자 취소");
        assertThat(row.get("paymentLabel").asText()).isEqualTo("카드 · 원래");
        assertThat(row.get("executable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("[R-12] 교환 철회 — 선결제 재발송비 취소가 추가 결제 기록 행으로 · route=EXCHANGE · 「교환 철회」")
    void exchangeFeeRefundRecorded() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        JsonNode created = json(userPost(USER_CLAIMS, claimBody(group, "EXCHANGE", "CHANGE_OF_MIND",
                items(group).get(0).getId(), null, addSamePriceVariant(creamVariant, 5).getVariantId()))
                .andExpect(status().isCreated()));
        long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(status().isOk());

        userPost(USER_CLAIMS + "/" + claimId + "/withdraw", Map.of()).andExpect(status().isOk());
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");

        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM order_refund_task WHERE source = 'CLAIM_PAYMENT_CANCELLED'");
        assertThat(task).containsEntry("status", "DONE").containsEntry("payment_kind", "ADDITIONAL")
                .containsEntry("payment_id", paymentId).containsEntry("source_id", collectionIdOf(claimId));
        JsonNode row = json(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=EXCHANGE")).get("content").get(0);
        assertThat(row.get("sourceLabel").asText()).isEqualTo("교환 재발송비 환불 · 교환 철회");
        assertThat(row.get("paymentLabel").asText()).isEqualTo("카드 · 추가");
        assertThat(row.get("sourceRef").asText()).isEqualTo("CLM-" + claimId);
        // 두 번 닫혀도 기록은 한 번이다.
        claimPaymentService.cancelRequested();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'CLAIM_PAYMENT_CANCELLED'",
                Integer.class)).isEqualTo(1);
    }

    private List<Long> refundTaskRepositoryPending(Long orderId) {
        return jdbc.queryForList("SELECT refund_task_id FROM order_refund_task WHERE order_id = ? "
                + "AND status IN ('PENDING', 'EXECUTING', 'FAILED')", Long.class, orderId);
    }

    // ------------------------------------------------------------------ 도우미

    /** 운영자 사유 환불 편입(06a B5) — 발송분이라 배송완료 하위주문에서. */
    private long operatorRefund(OrderDeliveryGroup group, int amount) throws Exception {
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "RECALL", "amount", amount, "detail", "위해성 리콜 · 식약처 회수 명령"))
                .andExpect(status().isOk());
        return jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task WHERE delivery_group_id = ?",
                Long.class, group.getId());
    }

    /** PG 거절로 실패한 직권 취소 환불. */
    private long failedRefund() throws Exception {
        OrderDeliveryGroup group = paidGroup(creamVariant, 1);
        fake.willFailCancel(paymentIdOf(group), FakePaymentGateway.Failure.REJECTED);
        directCancel(List.of(group.getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(1));
        return jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ? "
                + "AND status = 'FAILED'", Long.class, group.getId());
    }

    private long taskIdOfSource(String source) {
        return jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task WHERE source = ?", Long.class,
                source);
    }

    private String paymentIdOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT o.paid_payment_id FROM orders o JOIN order_delivery_group g "
                + "ON g.order_id = o.order_id WHERE g.delivery_group_id = ?", String.class, group.getId());
    }

    private List<Long> ids(ResultActions actions) throws Exception {
        JsonNode rows = json(actions.andExpect(status().isOk())).get("content");
        return StreamSupport.stream(rows.spliterator(), false).map(row -> row.get("refundTaskId").asLong()).toList();
    }

    private static JsonNode rowOfGroup(JsonNode rows, OrderDeliveryGroup group) {
        return StreamSupport.stream(rows.spliterator(), false)
                .filter(row -> row.get("deliveryGroupId").asLong() == group.getId()).findFirst().orElseThrow();
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}

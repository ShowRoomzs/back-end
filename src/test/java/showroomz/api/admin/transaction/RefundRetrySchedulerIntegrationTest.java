package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.transaction.service.AdminRefundService;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.service.OrderOverdueNoticeService;
import showroomz.domain.order.service.RefundExecutor;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOneStatus;
import showroomz.global.scheduler.OrderOverdueNoticeScheduler;
import showroomz.global.scheduler.RefundRetryScheduler;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 환불 자동 재시도 배치(1009 기획 수정본 2-4 · 45 보완 시나리오 2-2 RS-01 ~ RS-06 · 3절 RC-06 · 5절 L-05) — 결과 미확인 정리 → PG 자동
 * 대기 · 실패 재시도. 운영자 사유 환불은 배치가 집지 않는다(돈이 나가는 순간은 운영자의 재확인뿐). PG 자동은 최초 1 + 자동 1 = 2회까지.
 *
 * <p>배치 빈은 테스트 설정에서 꺼져 있어 직접 만든다. 대기 시간(대기 2분 · 실패 10분 · 집행 중 30분)은 시각 소급으로 맞춘다 — SQL 은
 * {@code created_at} · {@code modified_at} 소급과, 같은 결제의 집행 중 행으로 자동 집행을 미루는 데에만 쓴다.
 */
@IntegrationTest
class RefundRetrySchedulerIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_REFUNDS = "/v1/admin/refunds";
    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final int DELIVERY_FEE = 3_000;

    @Autowired private OrderRefundTaskRepository refundTaskRepository;
    @Autowired private RefundExecutor refundExecutor;
    @Autowired private OrderProperties orderProperties;
    @Autowired private AdminRefundService refundService;
    @Autowired private ApplicationContext context;

    private RefundRetryScheduler scheduler;
    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpScheduler() {
        scheduler = new RefundRetryScheduler(refundTaskRepository, refundExecutor, orderProperties);
        operator = fixture.createAdmin("retry-ops@showroomz.test", "김운영");
        admin = adminToken(operator);
    }

    @Test
    @DisplayName("[RS-01 · RS-02] 실패 11분 — PG 자동만 재시도(PG 1회 · 시도 2 · 여전히 실패 · 이력 SYSTEM) · 운영자 사유는 그대로 · 다음 회차는 상한(2)이라 PG 0회 · 운영자 재시도는 3회째로 된다")
    void retriesOnlyPgAutoUpToLimit() throws Exception {
        long pgAuto = failedDirectCancel();
        String pgPayment = paymentOf(pgAuto);
        OrderDeliveryGroup delivered = deliveredGroup(creamVariant, 1);
        long byOperator = operatorRefund(delivered, 5_000);
        String operatorPayment = paymentOf(byOperator);
        fake.willFailCancel(operatorPayment, FakePaymentGateway.Failure.REJECTED);
        execute(byOperator).andExpect(jsonPath("$.outcome").value("FAILED"));
        assertThat(task(pgAuto)).containsEntry("status", "FAILED").containsEntry("attempt", 1);
        assertThat(task(byOperator)).containsEntry("status", "FAILED").containsEntry("attempt", 1);
        backdateModified(11, pgAuto, byOperator);
        int pgCallsBefore = callsOf(pgPayment);
        int operatorCallsBefore = callsOf(operatorPayment);

        scheduler.run(LocalDateTime.now());

        assertThat(callsOf(pgPayment)).isEqualTo(pgCallsBefore + 1);
        assertThat(task(pgAuto)).containsEntry("status", "FAILED").containsEntry("attempt", 2);
        assertThat(failureActors(pgAuto)).containsExactly("SYSTEM", "SYSTEM");
        assertThat(callsOf(operatorPayment)).isEqualTo(operatorCallsBefore);
        assertThat(task(byOperator)).containsEntry("status", "FAILED").containsEntry("attempt", 1);
        assertThat(rowOf(json(adminGet(ADMIN_REFUNDS + "?tab=FAILED")), pgAuto).get("statusNote").asText())
                .contains("PG 거절").endsWith("재시도 1회 실패");

        // 자동 시도 상한(refund-auto-max-attempts=2) — 대기 시간을 지나도 다시 집지 않는다.
        assertThat(orderProperties.getRefundAutoMaxAttempts()).isEqualTo(2);
        backdateModified(11, pgAuto);
        scheduler.run(LocalDateTime.now());
        assertThat(callsOf(pgPayment)).isEqualTo(pgCallsBefore + 1);
        assertThat(task(pgAuto)).containsEntry("attempt", 2);
        adminGet(ADMIN_REFUNDS + "/" + pgAuto).andExpect(jsonPath("$.autoMaxAttempts").value(2))
                .andExpect(jsonPath("$.attempt").value(2));

        // 운영자 M2 재시도(R-08 과 이어짐) — 3회째로 집행된다.
        fake.willAnswerCancel(pgPayment, PortOneCancelResult.Outcome.SUCCEEDED);
        execute(pgAuto).andExpect(jsonPath("$.outcome").value("DONE"));
        assertThat(task(pgAuto)).containsEntry("status", "DONE").containsEntry("attempt", 3)
                .containsEntry("executed_by", operator.getId());
    }

    @Test
    @DisplayName("[RS-03] PG 자동 대기(커밋 뒤 집행을 놓친 건) — 1분이면 아직 · 3분이면 집행해 DONE(PG 1회 · 클레임 종결) · 운영자 사유 대기는 집지 않는다")
    void picksMissedPgAutoPending() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        String paymentId = paymentIdOf(group);
        long blocker = operatorRefund(group, 1_000);
        // 같은 결제의 다른 환불이 PG 를 기다리는 동안 반품 통과 → 자동 집행이 대기로 남는다(커밋 뒤 집행을 놓친 건과 같은 상태).
        jdbc.update("UPDATE order_refund_task SET status = 'EXECUTING' WHERE refund_task_id = ?", blocker);
        Long claimId = passed(returnClaim(group));
        long missed = jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE source = 'CLAIM_RETURN_PASSED' "
                + "AND delivery_group_id = ?", Long.class, group.getId());
        assertThat(task(missed)).containsEntry("status", "PENDING").containsEntry("origin", "PG_AUTO");
        jdbc.update("UPDATE order_refund_task SET status = 'PENDING' WHERE refund_task_id = ?", blocker);
        int calls = callsOf(paymentId);

        backdateCreated(1, missed, blocker);
        scheduler.run(LocalDateTime.now());
        assertThat(task(missed)).containsEntry("status", "PENDING");
        assertThat(callsOf(paymentId)).isEqualTo(calls);

        backdateCreated(3, missed, blocker);
        scheduler.run(LocalDateTime.now());
        assertThat(task(missed)).containsEntry("status", "DONE").containsEntry("executed_by", null);
        assertThat(callsOf(paymentId)).isEqualTo(calls + 1);
        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        assertThat(task(blocker)).containsEntry("status", "PENDING").containsEntry("attempt", 0);
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=PENDING"))).containsExactly(blocker);
    }

    @Test
    @DisplayName("[RS-04] 같은 결제 — 집행 중 31분(결과 미확인) 정리 → PG 자동 실패 재시도 순서 · 정리는 PG 재호출 없이 DONE · 재시도는 잔액 기준 · 이중 환불 0")
    void resolvesStaleBeforeRetry() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        String paymentId = paymentIdOf(group);
        int paid = CREAM_PRICE + DELIVERY_FEE;
        fake.willFailCancel(paymentId, FakePaymentGateway.Failure.REJECTED);
        passed(returnClaim(group));
        long pgAuto = jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE source = 'CLAIM_RETURN_PASSED' "
                + "AND delivery_group_id = ?", Long.class, group.getId());
        assertThat(task(pgAuto)).containsEntry("status", "FAILED");
        long stale = operatorRefund(group, DELIVERY_FEE);
        fake.willFailCancel(paymentId, FakePaymentGateway.Failure.TIMEOUT);
        execute(stale).andExpect(jsonPath("$.outcome").value("UNKNOWN"));
        assertThat(task(stale)).containsEntry("status", "EXECUTING");
        backdateModified(31, stale);
        backdateModified(11, pgAuto);
        // 포트원에는 정리 대상 건의 취소가 나가 있다 — 재시도 건은 이번엔 PG 가 받는다.
        fake.willReturn(paymentId, PortOnePayment.of(paymentId, PortOneStatus.PAID, FakePaymentGateway.STORE_ID, paid)
                .withCancelled(PortOneStatus.PARTIAL_CANCELLED, DELIVERY_FEE));
        fake.willAnswerCancel(paymentId, PortOneCancelResult.Outcome.SUCCEEDED);
        int calls = callsOf(paymentId);

        scheduler.run(LocalDateTime.now());

        assertThat(task(stale)).containsEntry("status", "DONE");
        assertThat(callsOf(paymentId)).isLessThanOrEqualTo(calls + 1);
        assertThat((String) task(pgAuto).get("status")).isIn("DONE", "FAILED");
        long cancelledRecords = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payment_cancel WHERE payment_id = ?",
                Long.class, paymentId);
        int cancelledAmount = jdbc.queryForObject("SELECT cancelled_amount FROM payment WHERE payment_id = ?", Integer.class,
                paymentId);
        assertThat(cancelledRecords).isEqualTo(cancelledAmount).isLessThanOrEqualTo(paid);
        assertThat(cancelledAmount).isEqualTo(task(pgAuto).get("status").equals("DONE") ? paid : DELIVERY_FEE);
    }

    @Test
    @DisplayName("[RS-05] 실패 5분 — 대기 시간(10분) 전이라 집지 않는다")
    void waitsTenMinutesAfterFailure() throws Exception {
        long taskId = failedDirectCancel();
        String paymentId = paymentOf(taskId);
        backdateModified(5, taskId);
        int calls = callsOf(paymentId);

        scheduler.run(LocalDateTime.now());

        assertThat(callsOf(paymentId)).isEqualTo(calls);
        assertThat(task(taskId)).containsEntry("status", "FAILED").containsEntry("attempt", 1);
    }

    @Test
    @DisplayName("[RS-06] 배치 스위치 — enabled=false 면 빈이 없고(테스트 컨텍스트) · true 거나 키가 없으면 있다 · 처리 지연 알림 배치도 같다")
    void schedulerSwitch() {
        assertThat(context.getBeanProvider(RefundRetryScheduler.class).getIfAvailable()).isNull();
        assertThat(context.getBeanProvider(OrderOverdueNoticeScheduler.class).getIfAvailable()).isNull();

        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(OrderRefundTaskRepository.class, () -> mock(OrderRefundTaskRepository.class))
                .withBean(RefundExecutor.class, () -> mock(RefundExecutor.class))
                .withBean(OrderProperties.class, OrderProperties::new)
                .withBean(OrderOverdueNoticeService.class, () -> mock(OrderOverdueNoticeService.class))
                .withUserConfiguration(RefundRetryScheduler.class, OrderOverdueNoticeScheduler.class);
        runner.withPropertyValues("order.refund-retry-scheduler-enabled=false",
                        "order.overdue-notice-scheduler-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RefundRetryScheduler.class)
                        .doesNotHaveBean(OrderOverdueNoticeScheduler.class));
        runner.withPropertyValues("order.refund-retry-scheduler-enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(RefundRetryScheduler.class)
                        .hasSingleBean(OrderOverdueNoticeScheduler.class));
        runner.run(ctx -> assertThat(ctx).hasSingleBean(RefundRetryScheduler.class));
    }

    // ------------------------------------------------------------------ 경합 · 흐름

    @Test
    @DisplayName("[RC-06] PG 자동 실패 11분 — 운영자 집행 × 배치 동시 · PG 1회 · 시도 +1 한 번 · 집행자는 이긴 쪽(운영자 id · 배치 null)")
    void operatorExecuteRacingBatch() throws Exception {
        long taskId = failedDirectCancel();
        String paymentId = paymentOf(taskId);
        fake.willAnswerCancel(paymentId, PortOneCancelResult.Outcome.SUCCEEDED);
        backdateModified(11, taskId);
        int calls = callsOf(paymentId);

        List<String> results = ConcurrentCalls.race(
                () -> refundService.execute(operator.getId(), taskId).outcome(),
                () -> {
                    scheduler.run(LocalDateTime.now());
                    return "BATCH";
                });

        assertThat(callsOf(paymentId)).isEqualTo(calls + 1);
        assertThat(task(taskId)).containsEntry("status", "DONE").containsEntry("attempt", 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel WHERE refund_task_id = ?", Integer.class, taskId))
                .isEqualTo(1);
        if (results.get(0).equals("DONE")) {
            assertThat(task(taskId)).containsEntry("executed_by", operator.getId());
        } else {
            assertThat(results.get(0)).isIn("SKIPPED", "REFUND_TASK_NOT_EXECUTABLE");
            assertThat(task(taskId)).containsEntry("executed_by", null);
        }
    }

    @Test
    @DisplayName("[L-05] 06a 대행 직권 취소 → PG 거절 → 06c 실패 → 배치 자동 재시도 1회 실패 → 운영자 M2 성공 → 완료 탭 「운영자 대행 직권 취소」")
    void adminCancelFailedRetriedThenOperatorCompletes() throws Exception {
        OrderDeliveryGroup group = paidGroup(creamVariant, 1);
        String paymentId = paymentIdOf(group);
        fake.willFailCancel(paymentId, FakePaymentGateway.Failure.REJECTED);
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/cancel",
                Map.of("reasonCode", "DEFECT", "consumerMessage", "위해성 회수로 취소합니다.")).andExpect(status().isOk());
        long taskId = jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ?",
                Long.class, group.getId());
        assertThat(task(taskId)).containsEntry("status", "FAILED").containsEntry("origin", "PG_AUTO");
        assertThat(rowOf(json(adminGet(ADMIN_REFUNDS + "?tab=FAILED")), taskId).get("sourceLabel").asText())
                .isEqualTo("운영자 대행 직권 취소");

        backdateModified(11, taskId);
        scheduler.run(LocalDateTime.now());
        JsonNode failedRow = rowOf(json(adminGet(ADMIN_REFUNDS + "?tab=FAILED")), taskId);
        assertThat(failedRow.get("attempt").asInt()).isEqualTo(2);
        assertThat(failedRow.get("statusNote").asText()).endsWith("재시도 1회 실패");
        assertThat(failureActors(taskId)).containsExactly("SYSTEM", "SYSTEM");

        fake.willAnswerCancel(paymentId, PortOneCancelResult.Outcome.SUCCEEDED);
        execute(taskId).andExpect(jsonPath("$.outcome").value("DONE"));
        JsonNode done = rowOf(json(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=CANCEL")), taskId);
        assertThat(done.get("sourceLabel").asText()).isEqualTo("운영자 대행 직권 취소");
        assertThat(done.get("statusNote").asText()).isEqualTo("집행 김운영 · 재확인");
        assertThat(done.get("amount").asInt()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        assertThat(task(taskId)).containsEntry("attempt", 3).containsEntry("executed_by", operator.getId());
        adminGet(ADMIN_REFUNDS + "/summary").andExpect(jsonPath("$.tabs.FAILED.count").value(0));
    }

    // ------------------------------------------------------------------ 도우미

    /** PG 거절로 실패한 브랜드 직권 취소 환불(PG 자동 · 시도 1). */
    private long failedDirectCancel() throws Exception {
        OrderDeliveryGroup group = paidGroup(creamVariant, 1);
        fake.willFailCancel(paymentIdOf(group), FakePaymentGateway.Failure.REJECTED);
        directCancel(List.of(group.getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(1));
        return jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ? "
                + "AND status = 'FAILED'", Long.class, group.getId());
    }

    private long operatorRefund(OrderDeliveryGroup group, int amount) throws Exception {
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "RECALL", "amount", amount, "detail", "위해성 리콜")).andExpect(status().isOk());
        return jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task WHERE delivery_group_id = ?",
                Long.class, group.getId());
    }

    private void backdateModified(int minutes, Long... taskIds) {
        for (Long taskId : taskIds) {
            jdbc.update("UPDATE order_refund_task SET modified_at = ? WHERE refund_task_id = ?",
                    LocalDateTime.now().minusMinutes(minutes), taskId);
        }
    }

    private void backdateCreated(int minutes, Long... taskIds) {
        for (Long taskId : taskIds) {
            jdbc.update("UPDATE order_refund_task SET created_at = ? WHERE refund_task_id = ?",
                    LocalDateTime.now().minusMinutes(minutes), taskId);
        }
    }

    private Map<String, Object> task(long taskId) {
        return jdbc.queryForMap("SELECT status, attempt, origin, executed_by FROM order_refund_task WHERE refund_task_id = ?",
                taskId);
    }

    private String paymentOf(long taskId) {
        return jdbc.queryForObject("SELECT payment_id FROM order_refund_task WHERE refund_task_id = ?", String.class, taskId);
    }

    private String paymentIdOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT o.paid_payment_id FROM orders o JOIN order_delivery_group g "
                + "ON g.order_id = o.order_id WHERE g.delivery_group_id = ?", String.class, group.getId());
    }

    private int callsOf(String paymentId) {
        return (int) fake.partialCancelCalls().stream().filter(call -> call.startsWith(paymentId + ":")).count();
    }

    /** 06c 상세 실패 기록의 시도별 주체 — 자동은 SYSTEM, 운영자 재시도는 ADMIN. */
    private List<String> failureActors(long taskId) throws Exception {
        JsonNode attempts = json(adminGet(ADMIN_REFUNDS + "/" + taskId).andExpect(status().isOk())).at("/failure/attempts");
        return StreamSupport.stream(attempts.spliterator(), false).map(a -> a.get("actorType").asText()).toList();
    }

    private ResultActions execute(long taskId) throws Exception {
        return adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of());
    }

    private List<Long> ids(ResultActions actions) throws Exception {
        JsonNode rows = json(actions.andExpect(status().isOk())).get("content");
        return StreamSupport.stream(rows.spliterator(), false).map(row -> row.get("refundTaskId").asLong()).toList();
    }

    private static JsonNode rowOf(JsonNode page, long taskId) {
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(row -> row.get("refundTaskId").asLong() == taskId).findFirst()
                .orElseThrow(() -> new AssertionError("행 없음: RFD-" + taskId));
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}

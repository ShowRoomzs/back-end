package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.service.AdminRefundService;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.RefundExecutor;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOneStatus;
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
    private static final int DELIVERY_FEE = 3_000;

    @Autowired private AdminRefundService refundService;
    @Autowired private RefundExecutor refundExecutor;

    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpAdmin() {
        operator = fixture.createAdmin("refunds-ops@showroomz.test", "김운영");
        admin = adminToken(operator);
    }

    // ------------------------------------------------------------------ 탭

    @Test
    @DisplayName("[R-01] 탭 — 집행 대기는 운영자 사유만 · 완료 탭 기간은 집행 시각(요약도 같은 days) · 소멸은 어느 탭에도 없다")
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
        // 요약도 같은 기간을 받는다 — 탭 숫자 = 목록 건수.
        adminGet(ADMIN_REFUNDS + "/summary").andExpect(jsonPath("$.tabs.DONE.count").value(0))
                .andExpect(jsonPath("$.tabs.DONE.days").value(30));
        adminGet(ADMIN_REFUNDS + "/summary?days=60").andExpect(jsonPath("$.tabs.DONE.count").value(1))
                .andExpect(jsonPath("$.tabs.DONE.days").value(60));

        jdbc.update("UPDATE order_refund_task SET status = 'VOID' WHERE refund_task_id = ?", pending);
        for (String tab : List.of("PENDING", "FAILED", "DONE")) {
            assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=" + tab + "&days=60"))).doesNotContain(pending);
        }
        adminGet(ADMIN_REFUNDS + "/" + pending).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID"));
        // [F-C11] 페이지 크기 — 06a · 06d 와 같은 공통 검증(1 ~ 100).
        adminGet(ADMIN_REFUNDS + "?size=101").andExpect(status().isBadRequest());
        adminGet(ADMIN_REFUNDS + "?size=0").andExpect(status().isBadRequest());
        adminGet(ADMIN_REFUNDS + "?size=100").andExpect(status().isOk());
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
        // [S-04] 주문번호는 정확 일치 — SQL 와일드카드가 패턴으로 동작하지 않는다.
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=%"))).isEmpty();
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=_"))).isEmpty();
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?keyword=" + orderNumber.substring(0, orderNumber.length() - 1) + "_")))
                .isEmpty();
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

        // [F-C07] 분실 처리(LOST_IN_TRANSIT) 환불은 경로 「취소」에 들어온다 — 반송 · 반품이 아니다.
        OrderDeliveryGroup lost = shipped(prepared(paidGroup(creamVariant, 1)), "CJ", newInvoice());
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ?, shipped_at = ? "
                + "WHERE delivery_group_id = ?", LocalDateTime.now().minusDays(29), LocalDateTime.now().minusDays(30), lost.getId());
        adminPost(ADMIN_ORDERS + "/groups/" + lost.getId() + "/lost", Map.of("reason", "택배사 분실 확인")).andExpect(status().isOk());
        long lostTask = taskIdOfSource("LOST_IN_TRANSIT");
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=CANCEL"))).contains(lostTask);
        for (String route : List.of("RETURN", "RETURN_SHIPMENT", "OPERATOR", "EXCHANGE")) {
            assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE&route=" + route))).as(route).doesNotContain(lostTask);
        }
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
        // [F-C06] 실패 0건이면 가장 오래 기다린 일수는 0 이 아니라 null.
        JsonNode empty = json(adminGet(ADMIN_REFUNDS + "/summary").andExpect(status().isOk()));
        assertThat(empty.at("/tabs/FAILED/count").asLong()).isZero();
        assertThat(empty.at("/tabs/FAILED/oldestWaitingDays").isNull()).isTrue();

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
        // [F-C01] 자동 시도 상한 — 설정값(order.refund-auto-max-attempts).
        assertThat(detail.get("autoMaxAttempts").asInt()).isEqualTo(2);
        assertThat(detail.get("attempt").asInt()).isZero();
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

    // ------------------------------------------------------------------ 편입 철회 · 수동 완료 기록(41 보고 2번)

    @Test
    @DisplayName("[R-13] 편입 철회 — 집행 전 운영자 사유만 VOID · 이력 · 어느 탭에도 없다 · 반려 이의 인용 · PG 자동 · 완료 건은 409")
    void voidRefund() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        long taskId = operatorRefund(group, 5_000);
        assertThat(json(adminGet(ADMIN_REFUNDS)).at("/content/0/voidable").asBoolean()).isTrue();

        JsonNode voided = json(adminPost(ADMIN_REFUNDS + "/" + taskId + "/void", Map.of("reason", "오편입 · 금액 재산정"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID")).andExpect(jsonPath("$.statusNote").value("편입 철회")));
        assertThat(jdbc.queryForObject("SELECT status FROM order_refund_task WHERE refund_task_id = ?", String.class, taskId))
                .isEqualTo("VOID");
        // [F-C09] 철회 응답 행의 「일시」 = 철회 시각(modified_at) · [F-A01] 06a 상세 환불 목록에는 소멸 행이 남는다.
        assertThat(voided.get("displayAt").asText()).isEqualTo(jsonTime(jdbc.queryForObject(
                "SELECT modified_at FROM order_refund_task WHERE refund_task_id = ?", java.sql.Timestamp.class, taskId)
                .toLocalDateTime()));
        JsonNode refunds = json(adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId())).at("/groups/0/refunds");
        assertThat(refunds).singleElement().satisfies(refund -> {
            assertThat(refund.get("refundTaskId").asLong()).isEqualTo(taskId);
            assertThat(refund.get("status").asText()).isEqualTo("VOID");
        });
        // [F-C02] 소멸 건 상세는 200 — 버튼 셋 다 없음 · 이력에 철회.
        JsonNode voidDetail = json(adminGet(ADMIN_REFUNDS + "/" + taskId).andExpect(status().isOk()));
        assertThat(voidDetail.get("status").asText()).isEqualTo("VOID");
        assertThat(voidDetail.get("statusLabel").asText()).isEqualTo("취소됨");
        assertThat(voidDetail.get("executable").asBoolean()).isFalse();
        assertThat(voidDetail.get("voidable").asBoolean()).isFalse();
        assertThat(voidDetail.get("manuallyCompletable").asBoolean()).isFalse();
        assertThat(voidDetail.get("history")).extracting(h -> h.get("type").asText()).contains("REFUND_VOIDED");
        assertThat(jdbc.queryForList("SELECT detail FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = 'REFUND_VOIDED'", String.class, group.getId())).singleElement().asString()
                .contains("RFD-" + taskId).contains("오편입");
        for (String tab : List.of("PENDING", "FAILED", "DONE")) {
            assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=" + tab))).doesNotContain(taskId);
        }
        adminGet(ADMIN_REFUNDS + "/summary").andExpect(jsonPath("$.badge").value(0));
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/void", Map.of("reason", "다시")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_VOIDABLE"));
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(status().isConflict());
        assertThat(fake.partialCancelCalls()).isEmpty();

        // 반려 이의 인용 건 — 편입 때 재발송비 청구가 정리돼 철회하지 않는다.
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        long dispute = json(adminPost("/v1/admin/claims/" + claimId + "/dispute-acceptance", Map.of("detail", "오염")))
                .get("refundTaskId").asLong();
        assertThat(json(adminGet(ADMIN_REFUNDS + "/" + dispute)).get("voidable").asBoolean()).isFalse();
        adminPost(ADMIN_REFUNDS + "/" + dispute + "/void", Map.of("reason", "x")).andExpect(status().isConflict());
        // PG 자동(실패) 건도 철회 대상이 아니다.
        adminPost(ADMIN_REFUNDS + "/" + failedRefund() + "/void", Map.of("reason", "x")).andExpect(status().isConflict());
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/void", Map.of("reason", " ")).andExpect(status().isBadRequest());
        // [F-C08] 없는 환불 — 철회 · 수동 완료도 404(집행 · 상세와 같다).
        adminPost(ADMIN_REFUNDS + "/999999/void", Map.of("reason", "오편입")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_FOUND"));
        adminPost(ADMIN_REFUNDS + "/999999/manual-complete", Map.of("note", "콘솔 처리")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_FOUND"));
    }

    @Test
    @DisplayName("[R-14] 수동 완료 기록 — 실패 건을 PG 호출 없이 완료 · 누적 취소액 · 취소 기록(ADMIN) · 이력 · 완료 탭 · 두 번째 409")
    void manualComplete() throws Exception {
        long taskId = failedRefund();
        String paymentId = jdbc.queryForObject("SELECT payment_id FROM order_refund_task WHERE refund_task_id = ?",
                String.class, taskId);
        int before = fake.partialCancelCalls().size();
        assertThat(json(adminGet(ADMIN_REFUNDS + "?tab=FAILED")).at("/content/0/manuallyCompletable").asBoolean()).isTrue();

        adminPost(ADMIN_REFUNDS + "/" + taskId + "/manual-complete",
                Map.of("pgCancellationId", "console-7f3a", "note", "포트원 콘솔에서 10.09 14:20 부분 취소"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.statusNote").value("집행 김운영 · 재확인"));

        assertThat(fake.partialCancelCalls()).hasSize(before);
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM order_refund_task WHERE refund_task_id = ?", taskId);
        assertThat(task).containsEntry("status", "DONE").containsEntry("executed_by", operator.getId());
        assertThat(task.get("payment_cancel_id")).isNotNull();
        assertThat(jdbc.queryForMap("SELECT requested_by, pg_cancellation_id, status FROM payment_cancel WHERE refund_task_id = ?",
                taskId)).containsEntry("requested_by", "ADMIN").containsEntry("pg_cancellation_id", "console-7f3a")
                .containsEntry("status", "SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT cancelled_amount FROM payment WHERE payment_id = ?", Integer.class, paymentId))
                .isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE payment_id = ?", String.class, paymentId))
                .isEqualTo("CANCELLED");
        JsonNode detail = json(adminGet(ADMIN_REFUNDS + "/" + taskId));
        assertThat(detail.at("/execution/pgCancellationId").asText()).isEqualTo("console-7f3a");
        assertThat(detail.get("history")).extracting(h -> h.get("type").asText()).contains("REFUND_RECORDED_MANUALLY");
        assertThat(ids(adminGet(ADMIN_REFUNDS + "?tab=DONE"))).contains(taskId);
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/manual-complete", Map.of("note", "다시")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_EXECUTABLE"));
    }

    // ------------------------------------------------------------------ 집행 결과 · 동시성 · 정리 배치

    @Test
    @DisplayName("[R-15] 결과 미확인 — 타임아웃 · PG 접수 대기는 UNKNOWN · 처리 중 유지 · 재집행 · 철회 · 수동 완료 409 · 정리 배치가 포트원 누적 취소액으로 닫는다")
    void unknownOutcomeResolvedByBatch() throws Exception {
        OrderDeliveryGroup settled = deliveredGroup(creamVariant, 1);
        long settledTask = operatorRefund(settled, 5_000);
        String settledPayment = paymentIdOf(settled);
        fake.willFailCancel(settledPayment, FakePaymentGateway.Failure.TIMEOUT);

        execute(settledTask).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.refund.status").value("EXECUTING"))
                .andExpect(jsonPath("$.refund.executable").value(false));
        execute(settledTask).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_EXECUTABLE"));
        adminPost(ADMIN_REFUNDS + "/" + settledTask + "/void", Map.of("reason", "오편입")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_VOIDABLE"));
        adminPost(ADMIN_REFUNDS + "/" + settledTask + "/manual-complete", Map.of("note", "콘솔 처리"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_EXECUTABLE"));
        assertThat(ids(adminGet(ADMIN_REFUNDS))).contains(settledTask);

        // 포트원에는 취소가 나갔다 — 정리 배치가 PG 를 다시 부르지 않고 완료로 닫는다.
        int calls = fake.partialCancelCalls().size();
        fake.willReturn(settledPayment, PortOnePayment.of(settledPayment, PortOneStatus.PAID, FakePaymentGateway.STORE_ID,
                paymentAmountOf(settledPayment)).withCancelled(PortOneStatus.PARTIAL_CANCELLED, 5_000));
        assertThat(refundExecutor.resolveStale(settledTask)).isEqualTo(RefundExecutor.Outcome.DONE);
        assertThat(taskStatus(settledTask)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT cancelled_amount FROM payment WHERE payment_id = ?", Integer.class,
                settledPayment)).isEqualTo(5_000);
        assertThat(fake.partialCancelCalls()).hasSize(calls);

        // PG 접수 대기도 결과 미확인이다 — 포트원에 취소가 없으면 실패(재시도 대기)로 굳히고, 운영자 재시도로 끝낸다.
        OrderDeliveryGroup pending = deliveredGroup(creamVariant, 1);
        long pendingTask = operatorRefund(pending, 4_000);
        String pendingPayment = paymentIdOf(pending);
        fake.willAnswerCancel(pendingPayment, PortOneCancelResult.Outcome.PENDING);
        execute(pendingTask).andExpect(jsonPath("$.outcome").value("UNKNOWN"));
        fake.willReturnPaid(pendingPayment, paymentAmountOf(pendingPayment));
        assertThat(refundExecutor.resolveStale(pendingTask)).isEqualTo(RefundExecutor.Outcome.FAILED);
        assertThat(jdbc.queryForObject("SELECT last_error FROM order_refund_task WHERE refund_task_id = ?", String.class,
                pendingTask)).contains("결과 미확인");
        fake.willAnswerCancel(pendingPayment, PortOneCancelResult.Outcome.SUCCEEDED);
        execute(pendingTask).andExpect(jsonPath("$.outcome").value("DONE"));
    }

    @Test
    @DisplayName("[R-16] 집행 결과 — 이미 전액 취소는 FAILED(코드) · 같은 결제의 다른 환불 처리 중 · 전액 취소 수렴 중은 SKIPPED(대기 유지 · PG 0회) · 잔액 부족은 PG 없이 FAILED")
    void executeOutcomes() throws Exception {
        OrderDeliveryGroup already = deliveredGroup(creamVariant, 1);
        long alreadyTask = operatorRefund(already, 5_000);
        fake.willAnswerCancel(paymentIdOf(already), PortOneCancelResult.Outcome.ALREADY_CANCELLED);
        execute(alreadyTask).andExpect(jsonPath("$.outcome").value("FAILED"))
                .andExpect(jsonPath("$.refund.status").value("FAILED"));
        assertThat(jdbc.queryForMap("SELECT last_error, last_error_code FROM order_refund_task WHERE refund_task_id = ?",
                alreadyTask)).containsEntry("last_error_code", "ALREADY_CANCELLED")
                .hasEntrySatisfying("last_error", error -> assertThat(error.toString()).contains("이미 전액 취소"));

        int calls = fake.partialCancelCalls().size();
        OrderDeliveryGroup busy = deliveredGroup(creamVariant, 1);
        long first = operatorRefund(busy, 3_000);
        long second = operatorRefund(busy, 2_000);
        jdbc.update("UPDATE order_refund_task SET status = 'EXECUTING' WHERE refund_task_id = ?", first);
        execute(second).andExpect(jsonPath("$.outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.refund.status").value("PENDING"));

        OrderDeliveryGroup converging = deliveredGroup(creamVariant, 1);
        long convergingTask = operatorRefund(converging, 3_000);
        jdbc.update("UPDATE payment SET status = 'CANCEL_REQUESTED' WHERE payment_id = ?", paymentIdOf(converging));
        execute(convergingTask).andExpect(jsonPath("$.outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.refund.status").value("PENDING"));
        assertThat(fake.partialCancelCalls()).hasSize(calls);

        // 편입 뒤 다른 환불이 먼저 나가 잔액이 모자란다 — PG 를 부르지 않고 실패(「잠시 후 다시」가 아니다).
        OrderDeliveryGroup lowBalance = deliveredGroup(creamVariant, 1);
        long lowTask = operatorRefund(lowBalance, 5_000);
        jdbc.update("UPDATE payment SET cancelled_amount = amount - 1000 WHERE payment_id = ?", paymentIdOf(lowBalance));
        execute(lowTask).andExpect(jsonPath("$.outcome").value("FAILED"))
                .andExpect(jsonPath("$.refund.status").value("FAILED"));
        assertThat(jdbc.queryForObject("SELECT last_error FROM order_refund_task WHERE refund_task_id = ?", String.class,
                lowTask)).contains("취소 가능 잔액");
        assertThat(fake.partialCancelCalls()).hasSize(calls);
        assertThat(jdbc.queryForList("SELECT event_type FROM order_fulfillment_history WHERE delivery_group_id = ?",
                String.class, lowBalance.getId())).contains("REFUND_FAILED");
    }

    @Test
    @DisplayName("[R-17] 동시 집행 — 같은 건을 두 운영자가 동시에 눌러도 PG 호출 1회 · 취소 기록 1행 · 하나는 DONE, 다른 하나는 SKIPPED 또는 409")
    void concurrentExecute() throws Exception {
        long taskId = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);

        List<String> results = ConcurrentCalls.race(
                () -> refundService.execute(operator.getId(), taskId).outcome(),
                () -> refundService.execute(operator.getId(), taskId).outcome());

        assertThat(results).containsOnlyOnce("DONE");
        assertThat(results).filteredOn(result -> !result.equals("DONE")).singleElement()
                .isIn("SKIPPED", "REFUND_TASK_NOT_EXECUTABLE");
        assertThat(fake.partialCancelCalls()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel WHERE refund_task_id = ?", Integer.class,
                taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_fulfillment_history WHERE event_type = 'REFUND_EXECUTED' "
                + "AND detail LIKE ?", Integer.class, "RFD-" + taskId + " %")).isEqualTo(1);
        assertThat(taskStatus(taskId)).isEqualTo("DONE");
    }

    @Test
    @DisplayName("[R-18] 철회 · 집행 경합 — 둘 중 하나만 이긴다. 철회가 이기면 PG 0회 · 집행이 이기면 철회 409")
    void voidRacingExecute() throws Exception {
        long taskId = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);

        List<String> results = ConcurrentCalls.race(
                () -> refundService.execute(operator.getId(), taskId).outcome(),
                () -> {
                    refundService.voidRefund(operator.getId(), taskId, new AdminTransactionDto.RefundVoidRequest("오편입"));
                    return "VOIDED";
                });

        String executed = results.get(0);
        String voided = results.get(1);
        if (taskStatus(taskId).equals("VOID")) {
            assertThat(voided).isEqualTo("VOIDED");
            assertThat(executed).isIn("SKIPPED", "REFUND_TASK_NOT_EXECUTABLE");
            assertThat(fake.partialCancelCalls()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel WHERE refund_task_id = ?", Integer.class,
                    taskId)).isZero();
        } else {
            assertThat(taskStatus(taskId)).isEqualTo("DONE");
            assertThat(executed).isEqualTo("DONE");
            assertThat(voided).isEqualTo("REFUND_TASK_NOT_VOIDABLE");
            assertThat(fake.partialCancelCalls()).hasSize(1);
        }
    }

    @Test
    @DisplayName("[R-19] 수동 완료 기록 — 집행 대기 · 취소번호 생략 · 결제 없는 주문도 기록 · 같은 결제 처리 중 · 잔액 부족은 409 · 입력 한도 400 · PG 0회")
    void manualCompleteVariants() throws Exception {
        int calls = fake.partialCancelCalls().size();
        long pendingTask = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        adminPost(ADMIN_REFUNDS + "/" + pendingTask + "/manual-complete", Map.of("note", "포트원 콘솔 처리"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        assertThat(jdbc.queryForMap("SELECT status, pg_cancellation_id FROM payment_cancel WHERE refund_task_id = ?",
                pendingTask)).containsEntry("status", "SUCCEEDED").containsEntry("pg_cancellation_id", null);

        long noPaymentTask = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        jdbc.update("UPDATE order_refund_task SET payment_id = NULL WHERE refund_task_id = ?", noPaymentTask);
        adminPost(ADMIN_REFUNDS + "/" + noPaymentTask + "/manual-complete", Map.of("note", "계좌 송금으로 환불"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        assertThat(jdbc.queryForMap("SELECT status, payment_cancel_id FROM order_refund_task WHERE refund_task_id = ?",
                noPaymentTask)).containsEntry("status", "DONE").containsEntry("payment_cancel_id", null);

        OrderDeliveryGroup busy = deliveredGroup(creamVariant, 1);
        long first = operatorRefund(busy, 3_000);
        long second = operatorRefund(busy, 2_000);
        jdbc.update("UPDATE order_refund_task SET status = 'EXECUTING' WHERE refund_task_id = ?", first);
        adminPost(ADMIN_REFUNDS + "/" + second + "/manual-complete", Map.of("note", "콘솔 처리"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_EXECUTABLE"));

        OrderDeliveryGroup lowBalance = deliveredGroup(creamVariant, 1);
        long lowTask = operatorRefund(lowBalance, 5_000);
        jdbc.update("UPDATE payment SET cancelled_amount = amount - 1000 WHERE payment_id = ?", paymentIdOf(lowBalance));
        adminPost(ADMIN_REFUNDS + "/" + lowTask + "/manual-complete", Map.of("note", "콘솔 처리"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_EXECUTABLE"));
        adminPost(ADMIN_REFUNDS + "/" + lowTask + "/manual-complete", Map.of("note", "가".repeat(501)))
                .andExpect(status().isBadRequest());
        adminPost(ADMIN_REFUNDS + "/" + lowTask + "/manual-complete",
                Map.of("pgCancellationId", "c".repeat(101), "note", "콘솔 처리")).andExpect(status().isBadRequest());
        assertThat(taskStatus(second)).isEqualTo("PENDING");
        assertThat(taskStatus(lowTask)).isEqualTo("PENDING");
        assertThat(fake.partialCancelCalls()).hasSize(calls);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("[R-20] 결제완료 소비자 취소 — 하위주문 합이 결제액과 다르면 기록 행을 남기지 않고 경고한다(취소 자체는 정상)")
    void consumerCancelSumMismatch(CapturedOutput output) throws Exception {
        OrderDeliveryGroup group = paidTwoItemGroup();
        Long orderId = group.getOrder().getId();
        jdbc.update("UPDATE order_delivery_group SET delivery_fee = COALESCE(delivery_fee, 0) + 100 WHERE delivery_group_id = ?",
                group.getId());

        cancel(orderId).andExpect(status().is2xxSuccessful());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE order_id = ?", Integer.class, orderId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel WHERE payment_id = ?", Integer.class,
                paymentIdOf(group))).isPositive();
        assertThat(output).contains("결제완료 소비자 취소 기록 생략");
    }

    // ------------------------------------------------------------------ 45 보완 시나리오 — 경합 · 응답 필드

    @Test
    @DisplayName("[RC-05] 실패 건 — 운영자 집행 × 다른 운영자 수동 완료 기록 동시 · 취소 기록 1행 · 누적 취소액 1회 · 하나만 DONE(R-18 의 짝)")
    void executeRacingManualComplete() throws Exception {
        Seller other = fixture.createAdmin("refunds-ops2@showroomz.test", "이운영");
        long taskId = failedRefund();
        String paymentId = jdbc.queryForObject("SELECT payment_id FROM order_refund_task WHERE refund_task_id = ?",
                String.class, taskId);
        fake.willAnswerCancel(paymentId, PortOneCancelResult.Outcome.SUCCEEDED);
        int calls = fake.partialCancelCalls().size();

        List<String> results = ConcurrentCalls.race(
                () -> refundService.execute(operator.getId(), taskId).outcome(),
                () -> {
                    refundService.recordManual(other.getId(), taskId,
                            new AdminTransactionDto.RefundManualCompleteRequest("console-rc05", "포트원 콘솔 부분 취소"));
                    return "MANUAL";
                });

        assertThat(taskStatus(taskId)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel WHERE refund_task_id = ?", Integer.class, taskId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancelled_amount FROM payment WHERE payment_id = ?", Integer.class, paymentId))
                .isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        Long executedBy = jdbc.queryForObject("SELECT executed_by FROM order_refund_task WHERE refund_task_id = ?", Long.class,
                taskId);
        if (results.get(1).equals("MANUAL")) {
            assertThat(results.get(0)).isIn("SKIPPED", "REFUND_TASK_NOT_EXECUTABLE");
            assertThat(executedBy).isEqualTo(other.getId());
            assertThat(fake.partialCancelCalls()).hasSize(calls);
        } else {
            assertThat(results.get(0)).isEqualTo("DONE");
            assertThat(results.get(1)).isEqualTo("REFUND_TASK_NOT_EXECUTABLE");
            assertThat(executedBy).isEqualTo(operator.getId());
            assertThat(fake.partialCancelCalls()).hasSize(calls + 1);
        }
    }

    @Test
    @DisplayName("[F-C03] 추가 결제 문장 — 결제 대기 청구 소멸(AC-07) 「인용 시 취소됨」 · 차감 환원(AC-07c) 「환불액에 포함」 · 결제 id 없음")
    void additionalPaymentVoidNotes() throws Exception {
        Long pendingFee = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        long pendingTask = acceptDispute(pendingFee);
        JsonNode voided = json(adminGet(ADMIN_REFUNDS + "/" + pendingTask)).at("/additionalPayments/0");
        assertThat(voided.get("status").asText()).isEqualTo("VOID");
        assertThat(voided.get("note").asText()).isEqualTo("재발송비 결제 요청은 인용 시 취소됨");
        assertThat(voided.get("claimPaymentId").isNull()).isTrue();

        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 2)));
        Map<String, Object> body = new java.util.HashMap<>(rejectBody());
        body.put("rejectedQuantity", 1);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", body).andExpect(status().isOk());
        Long split = jdbc.queryForObject("SELECT claim_id FROM order_claim WHERE split_from_claim_id = ?", Long.class, claimId);
        assertThat(chargeStatusOf(split)).isEqualTo("DEDUCTED");
        long restoredTask = acceptDispute(split);
        JsonNode restored = json(adminGet(ADMIN_REFUNDS + "/" + restoredTask)).at("/additionalPayments/0");
        assertThat(restored.get("status").asText()).isEqualTo("VOID");
        assertThat(restored.get("note").asText()).isEqualTo("차감분 환원 — 환불액에 포함");
        assertThat(restored.get("claimPaymentId").isNull()).isTrue();
    }

    @Test
    @DisplayName("[F-C04] 같은 박스에 재발송할 다른 반려가 남아 청구가 결제됨으로 유지 — 「같은 박스의 다른 반려 재발송에 쓰인다」 · 결제 그대로")
    void additionalPaymentKeptForOtherReject() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        List<Long> claimIds = requestClaim(group, showroomz.domain.order.type.ClaimType.RETURN,
                showroomz.domain.order.type.ClaimReason.CHANGE_OF_MIND, allItems(group), newInvoice()).claimIds();
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", claimIds)).andExpect(status().isOk());
        for (Long id : claimIds) {
            sellerPost(SELLER_CLAIMS + "/" + id + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        }
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimIds.get(0) + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertThat(claimIds).allSatisfy(id -> assertThat(claimStatus(id)).isEqualTo("RESHIP_READY"));

        long taskId = acceptDispute(claimIds.get(0));

        JsonNode kept = json(adminGet(ADMIN_REFUNDS + "/" + taskId)).at("/additionalPayments/0");
        assertThat(kept.get("status").asText()).isEqualTo("PAID");
        assertThat(kept.get("note").asText()).isEqualTo("결제된 재발송비 — 같은 박스의 다른 반려 재발송에 쓰인다");
        assertThat(kept.get("claimPaymentId").asText()).isEqualTo(paymentId);
        assertThat(kept.get("claimPaymentStatus").asText()).isEqualTo("PAID");
        assertThat(fake.cancelCalls()).doesNotContain(paymentId);
        assertThat(claimStatus(claimIds.get(1))).isEqualTo("RESHIP_READY");
    }

    @Test
    @DisplayName("[F-C10] 편입자 ≠ 집행자 — 상세 reason.requestedByName 과 execution.executedByName 이 서로 다른 두 이름 · 이력 주체도 각자")
    void requesterAndExecutorDiffer() throws Exception {
        Seller executor = fixture.createAdmin("refunds-exec@showroomz.test", "이집행");
        long taskId = operatorRefund(deliveredGroup(creamVariant, 1), 5_000);

        mockMvc.perform(post(ADMIN_REFUNDS + "/" + taskId + "/execute").header(HttpHeaders.AUTHORIZATION, adminToken(executor))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refund.statusNote").value("집행 이집행 · 재확인"));

        JsonNode detail = json(adminGet(ADMIN_REFUNDS + "/" + taskId));
        assertThat(detail.at("/reason/requestedByName").asText()).isEqualTo("김운영");
        assertThat(detail.at("/execution/executedByName").asText()).isEqualTo("이집행");
        assertThat(detail.get("history")).extracting(h -> h.get("type").asText() + ":" + h.get("actorName").asText())
                .containsExactly("REFUND_ENQUEUED_BY_OPERATOR:김운영", "REFUND_EXECUTED:이집행");
    }

    private long acceptDispute(Long claimId) throws Exception {
        return json(adminPost("/v1/admin/claims/" + claimId + "/dispute-acceptance", Map.of("detail", "배송 시점 오염"))
                .andExpect(status().isOk())).get("refundTaskId").asLong();
    }

    private ResultActions execute(long taskId) throws Exception {
        return adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of());
    }

    private String taskStatus(long taskId) {
        return jdbc.queryForObject("SELECT status FROM order_refund_task WHERE refund_task_id = ?", String.class, taskId);
    }

    private int paymentAmountOf(String paymentId) {
        return jdbc.queryForObject("SELECT amount FROM payment WHERE payment_id = ?", Integer.class, paymentId);
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

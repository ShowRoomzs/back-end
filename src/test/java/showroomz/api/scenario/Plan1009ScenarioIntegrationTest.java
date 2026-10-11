package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.groupbuy.service.port.GroupBuyClosureHook;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.OrderCancelRequestService;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimRejectLegalBasis;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1009 기획 수정본 — 발송 기한(1) · PG 자동 환불(2) · 취소 요청 자동 승인(3) · 구매확정 정지 · 재개(4) · 검수 반려 6항목(5-b) ·
 * 어드민 거래 관리(8)를 주문 생성부터 실제 결제(FakePaymentGateway)로 따라간다.
 */
@IntegrationTest
@DisplayName("[1009 기획 수정본] 발송 기한 · PG 자동 환불 · 취소 요청 · 구매확정 정지 · 반려 6항목 · 어드민 거래 관리")
class Plan1009ScenarioIntegrationTest extends OrderFlowTestSupport {

    private static final String USER_CLAIMS = "/v1/user/claims";
    private static final String SELLER_CLAIMS = "/v1/seller/claims";
    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final String ADMIN_REFUNDS = "/v1/admin/refunds";

    @Autowired private GroupBuyClosureHook closureHook;
    @Autowired private OrderCancelRequestService cancelRequestService;
    @Autowired private OrderClaimService claimService;
    @Autowired private OrderClaimRepository claimRepository;

    // ================================================================== 1 발송 기한

    @Test
    @DisplayName("[1] 공구 진행 중 결제 — 발송 기한 없음 · 주문 시점 N 스냅샷 · 앱 「마감 후 N영업일」 → 공구 종결 순간 마감 + N영업일로 확정")
    void shipDueAssignedOnClosure() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = reloadGroup(purchase.group());
        assertThat(group.getShipDueAt()).isNull();
        assertThat(group.getShipDueBusinessDays()).isEqualTo(SHIPPING_LEAD_DAYS);
        appOrder(purchase.orderId())
                .andExpect(jsonPath("$.items[0].statusSub").value("공구 마감 후 2영업일 이내 발송 (주말·공휴일 제외)"));

        // 브랜드가 설정을 바꿔도 접수된 주문은 주문 시점 N 그대로다.
        jdbc.update("UPDATE market SET shipping_lead_days = 5 WHERE market_id = ?", brand.marketId());
        LocalDateTime fridayEnd = LocalDateTime.of(2026, 8, 21, 23, 59);
        transactionTemplate.executeWithoutResult(tx -> closureHook.onGroupBuyClosed(groupBuy.getId(), fridayEnd));

        // 금요일 마감 + 2영업일 = 화요일의 끝.
        assertThat(reloadGroup(group).getShipDueAt()).isEqualTo(LocalDateTime.of(2026, 8, 25, 23, 59, 59));
        assertThat(reloadGroup(group).getShipDueBusinessDays()).isEqualTo(SHIPPING_LEAD_DAYS);
        sellerOrder(group).andExpect(jsonPath("$.timeline.shipDueBusinessDays").value(SHIPPING_LEAD_DAYS));
    }

    // ================================================================== 2 PG 자동 환불

    @Test
    @DisplayName("[2] 직권 취소 — 커밋 직후 PG 부분 취소로 자동 환불 · 큐 DONE · 누적 취소액 · 전액에 닿으면 결제 CANCELLED · 주문 연쇄 취소 없음")
    void directCancelRefundsAutomatically() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        int refund = CREAM_PRICE + DELIVERY_FEE;

        directCancel(List.of(purchase.group().getId()), "SOLD_OUT", "품절로 발송이 어렵습니다. 전액 환불됩니다.")
                .andExpect(jsonPath("$.succeeded").value(1));

        assertThat(refundTasks(purchase.group()))
                .containsExactly(new RefundTask("SELLER_DIRECT_CANCEL", refund, "DONE"));
        assertThat(fake.partialCancelCalls()).containsExactly(purchase.paymentId() + ":" + refund);
        Payment payment = paymentRepository.findById(purchase.paymentId()).orElseThrow();
        assertThat(payment.getCancelledAmount()).isEqualTo(refund);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(order(purchase.orderId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel WHERE payment_id = ? AND status = 'SUCCEEDED' "
                + "AND refund_task_id IS NOT NULL", Integer.class, purchase.paymentId())).isEqualTo(1);
        // 앱 — 환불 완료(대기 · 실패가 남지 않았다).
        userGet(ORDERS).andExpect(jsonPath("$.content[0].items[0].statusSub").value("완료"));
    }

    @Test
    @DisplayName("[2] PG 거절 — 큐 FAILED(사유 기록) · 어드민 환불 관리 실패 탭 → [재시도]로 DONE")
    void rejectedRefundRetriedByAdmin() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        fake.willFailCancel(purchase.paymentId(), FakePaymentGateway.Failure.REJECTED);

        directCancel(List.of(purchase.group().getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(1));
        assertThat(refundTasks(purchase.group())).extracting(RefundTask::status).containsExactly("FAILED");

        String admin = adminToken(fixture.createAdmin("ops@showroomz.test", "운영자"));
        JsonNode failed = json(adminGet(ADMIN_REFUNDS + "?tab=FAILED", admin).andExpect(status().isOk()));
        assertThat(failed.get("content")).hasSize(1);
        long taskId = failed.get("content").get(0).get("refundTaskId").asLong();
        assertThat(failed.get("content").get(0).get("lastError").asText()).contains("PG 거절");

        fake.willAnswerCancel(purchase.paymentId(), PortOneCancelResult.Outcome.SUCCEEDED);
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of(), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DONE"))
                .andExpect(jsonPath("$.refund.status").value("DONE"));
        assertThat(paymentRepository.findById(purchase.paymentId()).orElseThrow().getCancelledAmount())
                .isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        adminGet(ADMIN_REFUNDS + "/summary", admin).andExpect(jsonPath("$.badge").value(0));
    }

    // ================================================================== 3 취소 요청

    @Test
    @DisplayName("[3] 소비자 취소 요청 — 하위주문 전체 발송 보류 · 응답 기한 1영업일 · 기한 경과 자동 승인 → PG 자동 환불 · 취소 상세")
    void cancelRequestAutoApproved() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = preparing(purchase.group());
        OrderProduct item = itemsOf(group).get(0);

        JsonNode created = json(userPost(ORDERS + "/" + purchase.orderId() + "/cancel-requests", Map.of(
                "deliveryGroupId", group.getId(), "orderProductIds", List.of(item.getId()),
                "reasonCode", "CHANGE_OF_MIND")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("REVIEWING"))
                .andExpect(jsonPath("$.notices.length()").value(2)));
        Long requestId = created.get("cancelRequestId").asLong();
        assertThat(created.get("respondDueAt").isNull()).isFalse();

        // 하위주문 전체가 발송 보류 — 송장 등록이 막히고 같은 하위주문에 두 번째 요청은 409.
        registerShipment(group, "CJ", "400050009001").andExpect(jsonPath("$.succeeded").value(0));
        userPost(ORDERS + "/" + purchase.orderId() + "/cancel-requests", Map.of(
                "deliveryGroupId", group.getId(), "orderProductIds", List.of(item.getId()),
                "reasonCode", "CHANGE_OF_MIND")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_PENDING_EXISTS"));
        sellerOrder(group).andExpect(jsonPath("$.cancelRequest.respondDueAt").exists());

        OrderCancelRequest request = cancelRequestRepository.findById(requestId).orElseThrow();
        assertThat(cancelRequestService.autoApproveIfOverdue(requestId, LocalDateTime.now())).isFalse();
        assertThat(cancelRequestService.autoApproveIfOverdue(requestId, request.getRespondDueAt().plusMinutes(1)))
                .isTrue();

        OrderCancelRequest approved = cancelRequestRepository.findById(requestId).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(CancelRequestStatus.APPROVED);
        assertThat(approved.isAutoApproved()).isTrue();
        assertThat(approved.getDecidedBy()).isNull();
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CANCEL_REQUEST_APPROVED", CREAM_PRICE + DELIVERY_FEE, "DONE"));

        userGet(ORDERS + "/" + purchase.orderId() + "/items/" + item.getId() + "/cancel").andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("REQUEST"))
                .andExpect(jsonPath("$.phase").value("CANCELLED"))
                .andExpect(jsonPath("$.autoApproved").value(true))
                .andExpect(jsonPath("$.refund.status").value("DONE"));
    }

    @Test
    @DisplayName("[3] 취소 요청 거부 — 사유 드롭다운(라벨 + 상세)이 소비자 취소 상세에 그대로 · 환불 없음 · 발송 재개")
    void cancelRequestRejectedWithReasonCode() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = preparing(purchase.group());
        OrderProduct item = itemsOf(group).get(0);
        Long requestId = json(userPost(ORDERS + "/" + purchase.orderId() + "/cancel-requests", Map.of(
                "deliveryGroupId", group.getId(), "orderProductIds", List.of(item.getId()),
                "reasonCode", "ETC", "reasonDetail", "주소를 잘못 적었어요"))
                .andExpect(status().isCreated())).get("cancelRequestId").asLong();

        sellerPost(SELLER_ORDERS + "/cancel-requests/" + requestId + "/reject", Map.of("reasonCode", "ETC"))
                .andExpect(status().isBadRequest());
        sellerPost(SELLER_ORDERS + "/cancel-requests/" + requestId + "/reject",
                Map.of("reasonCode", "ALREADY_PACKED", "detail", "오늘 오전 출고 작업이 끝났습니다."))
                .andExpect(status().isOk());

        userGet(ORDERS + "/" + purchase.orderId() + "/items/" + item.getId() + "/cancel")
                .andExpect(jsonPath("$.phase").value("REJECTED"))
                .andExpect(jsonPath("$.rejection.reasonLabel").value("이미 포장·출고가 완료됨"))
                .andExpect(jsonPath("$.rejection.detail").value("오늘 오전 출고 작업이 끝났습니다."));
        assertThat(refundTasks(group)).isEmpty();
        registerShipment(group, "CJ", "400050009002").andExpect(jsonPath("$.succeeded").value(1));
    }

    // ================================================================== 4 구매확정 정지 · 재개

    @Test
    @DisplayName("[4] 반품 접수 = 구매확정 정지 · 정지 중에는 확정하지 않음 · 종결되면 정지한 시간만큼 밀어 남은 일수부터 재개")
    void purchaseConfirmPausedAndResumed() throws Exception {
        LocalDateTime deliveredAt = LocalDateTime.now().minusDays(5).withNano(0);
        OrderDeliveryGroup group = deliveredGroup("400050009101", deliveredAt);
        OrderProduct item = itemsOf(group).get(0);
        Long claimId = json(userPost(USER_CLAIMS, returnBody(group, item.getId(), "400050009102"))
                .andExpect(status().isCreated())).get("claimIds").get(0).asLong();

        OrderDeliveryGroup paused = reloadGroup(group);
        assertThat(paused.getConfirmPausedAt()).isNotNull();
        assertThat(paused.confirmRemainingDays(7, LocalDateTime.now())).isEqualTo(2L);
        sellerGet(SELLER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.purchaseConfirm.paused").value(true))
                .andExpect(jsonPath("$.purchaseConfirm.remainingDays").value(2));

        // 정지된 지 3일이 지났다고 치고 — 정지 중에는 7일이 지나도 확정하지 않는다.
        jdbc.update("UPDATE order_delivery_group SET confirm_paused_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(3), group.getId());
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now().plusDays(1))).isFalse();

        claimService.closeByAdmin(claimId, 1L, "미발송 방치", LocalDateTime.now());

        OrderDeliveryGroup resumed = reloadGroup(group);
        assertThat(resumed.getConfirmPausedAt()).isNull();
        // 기산점이 정지한 3일만큼 밀렸다 — 남은 일수는 멈춘 시점(배송완료 2일째)에 남아 있던 5일 그대로다.
        assertThat(resumed.confirmBaseAt()).isAfter(deliveredAt.plusDays(3).minusMinutes(1));
        assertThat(resumed.confirmRemainingDays(7, LocalDateTime.now())).isEqualTo(5L);
        assertThat(resumed.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    // ================================================================== 5-b 검수 반려 6항목

    @Test
    @DisplayName("[5-b] 일부 반려 — 반려 수량은 새 행으로 갈라져 반려 · 나머지는 통과 · 통과분 환불 − 차감 − 재발송비가 PG 자동 환불")
    void partialReject() throws Exception {
        Purchase purchase = purchase(creamVariant, 2); // 54,400원 — 무료배송(최초 배송비 3,000 차감 대상)
        OrderDeliveryGroup group = deliver(purchase.group(), "400050009201");
        OrderProduct item = itemsOf(group).get(0);
        Long claimId = json(userPost(USER_CLAIMS, returnBody(group, item.getId(), "400050009202"))
                .andExpect(status().isCreated())).get("claimIds").get(0).asLong();
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));

        // 6항목 중 법적 근거가 빠지면 같은 오류다.
        Map<String, Object> missingLegal = rejectBody(1, false);
        missingLegal.remove("legalBasis");
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", missingLegal)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLAIM_REJECT_INCOMPLETE"));

        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", rejectBody(1, false))
                .andExpect(status().isOk());

        List<OrderClaim> box = claimRepository.findAll().stream()
                .filter(claim -> claim.getDeliveryGroup().getId().equals(group.getId()))
                .sorted(Comparator.comparing(OrderClaim::getId)).toList();
        assertThat(box).hasSize(2);
        OrderClaim passed = box.get(0);
        OrderClaim split = box.get(1);
        assertThat(passed.getQuantity()).isEqualTo(1);
        assertThat(passed.getStatus()).isEqualTo(ClaimStatus.COMPLETED); // 통과 → PG 자동 환불 → 종결
        assertThat(split.getQuantity()).isEqualTo(1);
        assertThat(split.getSplitFromClaimId()).isEqualTo(passed.getId());
        assertThat(split.getRejectLegalBasis()).isEqualTo(ClaimRejectLegalBasis.ART17_2_2);
        assertThat(split.getRejectConsumerMessage()).isEqualTo("개봉 후 사용 흔적이 있어 1개는 반품이 어렵습니다.");
        // 통과분 환불이 재발송비를 덮는다 — 차감하고 바로 재발송 대기.
        assertThat(split.getStatus()).isEqualTo(ClaimStatus.RESHIP_READY);
        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CLAIM_RETURN_PASSED", CREAM_PRICE - DELIVERY_FEE - DELIVERY_FEE, "DONE"));

        userGet(USER_CLAIMS + "/" + split.getId())
                .andExpect(jsonPath("$.items[?(@.claimId == " + split.getId() + ")].rejection.legalNote")
                        .value(ClaimRejectLegalBasis.ART17_2_2.getLabel()))
                .andExpect(jsonPath("$.items[?(@.claimId == " + split.getId() + ")].rejection.rejectedQuantity")
                        .value(1));
    }

    @Test
    @DisplayName("[5-b] 브랜드 귀책 인정 — 차감 환원 · 반려 재발송비 0원(브랜드 부담) · 결제 없이 바로 재발송 대기")
    void rejectWithSellerFault() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = deliver(purchase.group(), "400050009301");
        Long claimId = json(userPost(USER_CLAIMS, returnBody(group, itemsOf(group).get(0).getId(), "400050009302"))
                .andExpect(status().isCreated())).get("claimIds").get(0).asLong();
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)));

        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", rejectBody(null, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("RESHIP_READY"))
                .andExpect(jsonPath("$.rejection.faultChangedToSeller").value(true));

        assertThat(jdbc.queryForObject("SELECT c.status FROM order_claim_charge c JOIN order_claim k "
                + "ON k.collection_id = c.collection_id WHERE k.claim_id = ? AND c.type = 'REJECT_RESHIP'",
                String.class, claimId)).isEqualTo(ClaimChargeStatus.WAIVED.name());
        assertThat(claimRepository.findById(claimId).orElseThrow().getFeeBearer().name()).isEqualTo("SELLER");
    }

    // ================================================================== 8 어드민 거래 관리

    @Test
    @DisplayName("[8] 어드민 주문 조회 · 상세 · 운영자 사유 환불 편입(돈은 안 나감) → 환불 관리 집행으로만 PG 부분 취소 · 잔액 초과는 409")
    void adminOrderAndOperatorRefund() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = deliver(purchase.group(), "400050009401");
        String admin = adminToken(fixture.createAdmin("ops2@showroomz.test", "운영자"));

        adminGet(ADMIN_ORDERS, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(purchase.orderId()));
        adminGet(ADMIN_ORDERS + "/" + purchase.orderId(), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].shipping.shipDueBusinessDays").value(SHIPPING_LEAD_DAYS))
                .andExpect(jsonPath("$.groups[0].actions.canEnqueueRefund").value(true))
                .andExpect(jsonPath("$.groups[0].actions.canRegisterShipment").value(false));
        adminGet(ADMIN_ORDERS + "/summary", admin).andExpect(jsonPath("$.tabCounts.ALL").value(1));

        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "RECALL", "amount", 999_999, "detail", "위해성 리콜"), admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFUND_AMOUNT_EXCEEDED"));
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "RECALL", "amount", 10_000, "detail", "위해성 리콜 · 식약처 회수 명령"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].refunds[0].origin").value("OPERATOR"))
                .andExpect(jsonPath("$.groups[0].refunds[0].status").value("PENDING"));
        // 편입만으로 돈은 나가지 않는다.
        assertThat(fake.partialCancelCalls()).isEmpty();

        JsonNode pending = json(adminGet(ADMIN_REFUNDS + "?tab=PENDING", admin).andExpect(status().isOk()));
        long taskId = pending.get("content").get(0).get("refundTaskId").asLong();
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of(), admin)
                .andExpect(jsonPath("$.outcome").value("DONE"));
        assertThat(fake.partialCancelCalls()).containsExactly(purchase.paymentId() + ":10000");
        assertThat(paymentRepository.findById(purchase.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        adminGet("/v1/admin/order-exceptions/summary", admin).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 도우미

    private OrderDeliveryGroup deliver(OrderDeliveryGroup group, String trackingNumber) throws Exception {
        OrderDeliveryGroup prepared = preparing(group);
        registerShipment(prepared, "CJ", trackingNumber).andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime deliveredAt = LocalDateTime.now().minusHours(1).withNano(0);
        return track(prepared, new TrackSnapshot(deliveredAt, deliveredAt, false, false), LocalDateTime.now());
    }

    private Map<String, Object> returnBody(OrderDeliveryGroup group, Long orderProductId, String trackingNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", "RETURN");
        body.put("deliveryGroupId", group.getId());
        body.put("items", List.of(Map.of("orderProductId", orderProductId)));
        body.put("reasonCode", "CHANGE_OF_MIND");
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", trackingNumber));
        return body;
    }

    private Map<String, Object> rejectBody(Integer rejectedQuantity, boolean sellerFault) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reasonCode", "USED");
        body.put("detail", "용기 입구에 사용 흔적이 있습니다.");
        body.put("legalBasis", "ART17_2_2");
        if (rejectedQuantity != null) {
            body.put("rejectedQuantity", rejectedQuantity);
        }
        body.put("faultChangedToSeller", sellerFault);
        body.put("consumerMessage", "개봉 후 사용 흔적이 있어 1개는 반품이 어렵습니다.");
        body.put("evidenceImageUrls", List.of("https://img.test/evidence-1.jpg"));
        return body;
    }

    private ResultActions userGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions userPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions adminGet(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions adminPost(String url, Object body, String token) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}

package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.api.admin.transaction.service.AdminOrderCommandService;
import showroomz.api.scenario.OrderFlowTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.SellerCancelReason;
import showroomz.support.IntegrationTest;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 주문 조회(06a) 운영자 조치 7종의 실패 매트릭스와 성공 부수 효과 — 43 테스트 상세 2-3(OA-A01 ~ A16)과 6절 X-11 ② · ③.
 * 실패한 요청은 상태 · 이력 · 환불 큐 · 재고 · PG 호출 어디에도 흔적을 남기지 않아야 한다.
 */
@IntegrationTest
class AdminOrderActionGuardIntegrationTest extends OrderFlowTestSupport {

    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final int DELIVERY_FEE = 3_000;
    private static final String EVIDENCE = "https://img.test/defect.jpg";

    @Autowired private AdminOrderCommandService commandService;

    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpAdmin() {
        operator = fixture.createAdmin("orders-guard@showroomz.test", "운영자");
        admin = adminToken(operator);
    }

    // ------------------------------------------------------------------ 배송완료일 정정

    @Test
    @DisplayName("[OA-A02] 정정 입력 — 사유 공백 · 301자 · 시각 누락 400 · 앞뒤로 여러 번 정정 가능 · 회마다 이력(구 → 신 · 사유)")
    void correctDeliveredAtInputsAndRepeats() throws Exception {
        LocalDateTime deliveredAt = LocalDateTime.now().minusDays(3).withNano(0);
        OrderDeliveryGroup group = deliveredGroup("400080001001", deliveredAt);
        jdbc.update("UPDATE order_delivery_group SET shipped_at = ? WHERE delivery_group_id = ?",
                deliveredAt.minusDays(2), group.getId());

        correct(group, Map.of("deliveredAt", deliveredAt.minusHours(5).toString(), "reason", " "))
                .andExpect(status().isBadRequest());
        correct(group, Map.of("deliveredAt", deliveredAt.minusHours(5).toString(), "reason", "가".repeat(301)))
                .andExpect(status().isBadRequest());
        correct(group, Map.of("reason", "택배사 확인")).andExpect(status().isBadRequest());
        assertThat(events(group, "DELIVERED_AT_CORRECTED")).isEmpty();

        LocalDateTime earlier = deliveredAt.minusDays(1);
        LocalDateTime later = deliveredAt.plusDays(1);
        correct(group, Map.of("deliveredAt", earlier.toString(), "reason", "소비자 이의 · 택배사 확인")).andExpect(status().isOk());
        JsonNode corrected = json(correct(group, Map.of("deliveredAt", later.toString(), "reason", "재확인 결과 정정"))
                .andExpect(status().isOk()));
        assertThat(at(corrected.at("/groups/0/purchaseConfirm/dueAt").asText())).isEqualTo(later.plusDays(7));

        assertThat(((Timestamp) groupRow(group).get("delivered_at")).toLocalDateTime()).isEqualTo(later);
        assertThat(groupRow(group).get("delivered_by")).isEqualTo(operator.getId());
        List<String> details = events(group, "DELIVERED_AT_CORRECTED");
        assertThat(details).hasSize(2);
        assertThat(details.get(0)).contains("→").contains("소비자 이의 · 택배사 확인");
        assertThat(details.get(1)).contains("재확인 결과 정정");
        assertThat(actorsOf(group, "DELIVERED_AT_CORRECTED")).containsOnly("ADMIN");
    }

    // ------------------------------------------------------------------ 대행 송장

    @Test
    @DisplayName("[OA-A05] 대행 송장 실패 — 신규(알림 3회여도) · 결제 취소 수렴 중은 409 · 사유 301자 · 택배사 누락 · 숫자 없는 송장 400 · 상태 불변")
    void shipmentFailures() throws Exception {
        OrderDeliveryGroup fresh = paidGroup();
        overdue(fresh, 3);
        shipment(fresh, "CJ", "400080002001", "무응답").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));

        OrderDeliveryGroup group = preparingGroup();
        overdue(group, 3);
        shipment(group, "CJ", "400080002002", "가".repeat(301)).andExpect(status().isBadRequest());
        Map<String, Object> noCarrier = new HashMap<>(Map.of("trackingNumber", "400080002002", "note", "무응답"));
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/shipment", noCarrier).andExpect(status().isBadRequest());
        shipment(group, "CJ", "abc-def", "무응답").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVOICE_FORMAT_INVALID"));

        jdbc.update("UPDATE payment SET status = 'CANCEL_REQUESTED' WHERE payment_id = ?", paymentIdOf(group));
        shipment(group, "CJ", "400080002002", "무응답").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));

        assertThat(groupRow(group)).containsEntry("fulfillment_status", "PREPARING").containsEntry("tracking_number", null);
        assertThat(events(group, "INVOICE_REGISTERED")).isEmpty();
    }

    @Test
    @DisplayName("[OA-A04] 대행 송장 성공 — 하이픈 송장은 숫자만 저장 · 택배사 라벨 · 알림 횟수 유지 · 처리 지연에서 빠짐 · 이력 detail(대행 · 택배사 · 송장 · 사유)")
    void shipmentSuccessEffects() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        overdue(group, 3);
        shipment(group, "CJ", "4000-8000-3001", "자동 알림 3회 무응답").andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].status").value("SHIPPING"))
                .andExpect(jsonPath("$.groups[0].shipping.trackingNumber").value("400080003001"))
                .andExpect(jsonPath("$.groups[0].shipping.carrierLabel").value("CJ대한통운"))
                .andExpect(jsonPath("$.groups[0].shipping.overdueNoticeCount").value(3))
                .andExpect(jsonPath("$.groups[0].actions.canRegisterShipment").value(false));
        assertThat(groupRow(group)).containsEntry("tracking_number", "400080003001").containsEntry("carrier", "CJ");
        assertThat(groupRow(group).get("shipped_at")).isNotNull();
        assertThat(events(group, "INVOICE_REGISTERED")).singleElement().asString()
                .contains("운영자 대행").contains("400080003001").contains("자동 알림 3회 무응답");
        JsonNode delays = json(adminGet("/v1/admin/order-exceptions?tab=DELAY")).at("/page/content");
        assertThat(delays.findValuesAsText("deliveryGroupId")).doesNotContain(String.valueOf(group.getId()));
    }

    // ------------------------------------------------------------------ 대행 직권 취소

    @Test
    @DisplayName("[OA-A06] 직권 취소 성공 — 재고 원복 · 항목 취소 · 취소 유형 · 취소 전 상태 · 취소 블록 · PG 1회(항목 + 배송비) · 이력 actor ADMIN · 소비자 메시지")
    void cancelSuccessEffects() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        overdue(group, 3);
        Long variantId = itemsOf(group).get(0).getVariantId();
        int stockBefore = stock(variantId);
        int pgCalls = fake.partialCancelCalls().size();

        cancel(group, "SOLD_OUT", "재고가 소진되어 취소합니다.").andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.groups[0].cancel.cancelTypeLabel").isNotEmpty())
                .andExpect(jsonPath("$.groups[0].cancel.reasonDetail").value("재고가 소진되어 취소합니다."))
                .andExpect(jsonPath("$.groups[0].refunds[0].origin").value("PG_AUTO"))
                .andExpect(jsonPath("$.groups[0].actions.canCancel").value(false));
        // 응답은 커밋 전에 조립된다 — PG 자동 환불(커밋 직후)의 결과는 DB 로 본다.
        assertThat(refundTasks(group)).singleElement().satisfies(task -> {
            assertThat(task.source()).isEqualTo("SELLER_DIRECT_CANCEL");
            assertThat(task.amount()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
            assertThat(task.status()).isEqualTo("DONE");
        });

        assertThat(stock(variantId)).isEqualTo(stockBefore + 1);
        assertThat(groupRow(group)).containsEntry("cancel_type", "SELLER_DIRECT").containsEntry("status_at_cancel", "PREPARING")
                .containsEntry("cancel_reason_code", "SOLD_OUT");
        assertThat(jdbc.queryForList("SELECT status FROM order_product WHERE delivery_group_id = ?", String.class, group.getId()))
                .containsOnly("CANCELLED");
        assertThat(fake.partialCancelCalls()).hasSize(pgCalls + 1).last().asString()
                .endsWith(":" + (CREAM_PRICE + DELIVERY_FEE));
        assertThat(events(group, "CANCELLED_BY_SELLER")).singleElement().asString()
                .contains("운영자 대행").contains("재고가 소진되어 취소합니다.");
        assertThat(actorsOf(group, "CANCELLED_BY_SELLER")).containsOnly("ADMIN");
    }

    @Test
    @DisplayName("[OA-A07] 직권 취소 실패 — 배송중(하자 사유여도) · 검토 중 취소 요청 409 · 메시지 공백 · 301자 · 사유 누락 · 없는 사유 400 · 재고 · 큐 불변")
    void cancelFailures() throws Exception {
        OrderDeliveryGroup shipping = shippingGroup("400080004001");
        cancel(shipping, "DEFECT", "위해성 회수").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));

        OrderDeliveryGroup requested = preparingGroup();
        seedCancelRequest(requested, itemsOf(requested));
        cancel(requested, "DEFECT", "위해성 회수").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_PENDING_EXISTS"));

        OrderDeliveryGroup group = preparingGroup();
        Long variantId = itemsOf(group).get(0).getVariantId();
        int stockBefore = stock(variantId);
        cancel(group, "DEFECT", " ").andExpect(status().isBadRequest());
        cancel(group, "DEFECT", "가".repeat(301)).andExpect(status().isBadRequest());
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/cancel", Map.of("consumerMessage", "회수"))
                .andExpect(status().isBadRequest());
        cancel(group, "NOT_A_REASON", "회수").andExpect(status().isBadRequest());

        for (OrderDeliveryGroup untouched : List.of(shipping, requested, group)) {
            assertThat(refundTasks(untouched)).isEmpty();
            assertThat(groupRow(untouched).get("cancel_type")).isNull();
        }
        assertThat(stock(variantId)).isEqualTo(stockBefore);
    }

    // ------------------------------------------------------------------ 운영자 사유 환불 편입

    @Test
    @DisplayName("[OA-A08 · A09] 편입 상한 — 잔액 = 취소 가능액 − 나가지 않은 환불 · 경계값 정확히 통과 · 1원 초과 409(잔액 문구) · 철회하면 다시 열림 · PG 0회")
    void enqueueCapBoundary() throws Exception {
        OrderDeliveryGroup group = deliveredGroup("400080005001", LocalDateTime.now().minusDays(1));
        int paid = CREAM_PRICE + DELIVERY_FEE;
        int pgCalls = fake.partialCancelCalls().size();

        enqueue(group, "RECALL", paid - 1_000).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].refunds[0].origin").value("OPERATOR"))
                .andExpect(jsonPath("$.groups[0].refunds[0].status").value("PENDING"));
        enqueue(group, "RECALL", 1_001).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_AMOUNT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1,000원")));
        enqueue(group, "POST_CONFIRM_DEFECT", 1_000).andExpect(status().isOk());
        enqueue(group, "RECALL", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("0원")));

        Long first = jdbc.queryForObject("SELECT MIN(refund_task_id) FROM order_refund_task WHERE delivery_group_id = ?",
                Long.class, group.getId());
        adminPost("/v1/admin/refunds/" + first + "/void", Map.of("reason", "금액 재산정")).andExpect(status().isOk());
        enqueue(group, "RECALL", paid - 1_000).andExpect(status().isOk());

        assertThat(fake.partialCancelCalls()).hasSize(pgCalls);
        List<Map<String, Object>> tasks = jdbc.queryForList("SELECT reason_code, refund_amount, requested_by, source "
                + "FROM order_refund_task WHERE delivery_group_id = ? AND status = 'PENDING' ORDER BY refund_task_id", group.getId());
        assertThat(tasks).extracting(row -> row.get("refund_amount")).containsExactly(1_000, paid - 1_000);
        assertThat(tasks).allSatisfy(row -> {
            assertThat(row.get("source")).isEqualTo("OPERATOR_REASON");
            assertThat(row.get("requested_by")).isEqualTo(operator.getId());
        });
        assertThat(events(group, "REFUND_ENQUEUED_BY_OPERATOR")).hasSize(3)
                .first().asString().contains("위해성 리콜").contains(String.format("%,d원", paid - 1_000));
    }

    @Test
    @DisplayName("[OA-A10] 편입 입력 — 금액 0 · 누락 · 근거 공백 · 501자 · 사유 누락 400 · 결제 행 없는 주문은 상한 검사 없이 편입")
    void enqueueInputs() throws Exception {
        OrderDeliveryGroup group = deliveredGroup("400080006001", LocalDateTime.now().minusDays(1));
        enqueue(group, "RECALL", 0).andExpect(status().isBadRequest());
        adminPost(refundTasksUrl(group), Map.of("reason", "RECALL", "detail", "근거")).andExpect(status().isBadRequest());
        adminPost(refundTasksUrl(group), Map.of("reason", "RECALL", "amount", 1_000, "detail", " ")).andExpect(status().isBadRequest());
        adminPost(refundTasksUrl(group), Map.of("reason", "RECALL", "amount", 1_000, "detail", "가".repeat(501)))
                .andExpect(status().isBadRequest());
        adminPost(refundTasksUrl(group), Map.of("amount", 1_000, "detail", "근거")).andExpect(status().isBadRequest());
        assertThat(refundTasks(group)).isEmpty();

        jdbc.update("UPDATE orders SET paid_payment_id = NULL WHERE order_id = ?", orderOf(group));
        enqueue(group, "RECALL", 9_999_999).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT payment_id FROM order_refund_task WHERE delivery_group_id = ?",
                String.class, group.getId())).isNull();
    }

    // ------------------------------------------------------------------ 구매확정 후 하자 반품

    @Test
    @DisplayName("[OA-A11 · A12] 하자 반품 — 오배송도 개설 · 기타 · 항목 없음 · 중복 · 수량 0 · 내용 공백 400 · 배송중 409 · 다른 하위주문 항목 404 · 열린 클레임이 점유한 수량 409")
    void defectClaimMatrix() throws Exception {
        OrderDeliveryGroup group = deliveredGroup("400080007001", LocalDateTime.now().minusDays(1));
        Long itemId = itemsOf(group).get(0).getId();

        defect(group, List.of(Map.of("orderProductId", itemId, "quantity", 1)), "ETC", "내용").andExpect(status().isBadRequest());
        defect(group, List.of(), "DAMAGED_OR_DEFECTIVE", "내용").andExpect(status().isBadRequest());
        defect(group, List.of(Map.of("orderProductId", itemId, "quantity", 1), Map.of("orderProductId", itemId, "quantity", 1)),
                "DAMAGED_OR_DEFECTIVE", "내용").andExpect(status().isBadRequest());
        defect(group, List.of(Map.of("orderProductId", itemId, "quantity", 0)), "DAMAGED_OR_DEFECTIVE", "내용")
                .andExpect(status().isBadRequest());
        defect(group, List.of(Map.of("orderProductId", itemId, "quantity", 1)), "DAMAGED_OR_DEFECTIVE", " ")
                .andExpect(status().isBadRequest());
        defect(group, List.of(Map.of("orderProductId", itemId, "quantity", 2)), "DAMAGED_OR_DEFECTIVE", "내용")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_QUANTITY_EXCEEDED"));

        OrderDeliveryGroup other = deliveredGroup("400080007002", LocalDateTime.now().minusDays(1));
        defect(group, List.of(Map.of("orderProductId", itemsOf(other).get(0).getId(), "quantity", 1)),
                "DAMAGED_OR_DEFECTIVE", "내용").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_PRODUCT_NOT_FOUND"));
        OrderDeliveryGroup shipping = shippingGroup("400080007003");
        defect(shipping, List.of(Map.of("orderProductId", itemsOf(shipping).get(0).getId(), "quantity", 1)),
                "DAMAGED_OR_DEFECTIVE", "내용").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ELIGIBLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim", Integer.class)).isZero();

        JsonNode opened = json(defect(group, List.of(Map.of("orderProductId", itemId, "quantity", 1)),
                "WRONG_OR_LATE_DELIVERY", "다른 색상이 왔습니다").andExpect(status().isOk()));
        assertThat(opened.get("requestId").asLong()).isPositive();
        long claimId = opened.get("claimIds").get(0).asLong();
        assertThat(jdbc.queryForMap("SELECT opened_by, opened_by_admin_id, reason_code, fee_bearer, status FROM order_claim "
                + "WHERE claim_id = ?", claimId)).containsEntry("opened_by", "OPERATOR")
                .containsEntry("opened_by_admin_id", operator.getId()).containsEntry("reason_code", "WRONG_OR_LATE_DELIVERY")
                .containsEntry("fee_bearer", "SELLER");
        defect(group, List.of(Map.of("orderProductId", itemId, "quantity", 1)), "DAMAGED_OR_DEFECTIVE", "또 열기")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_QUANTITY_EXCEEDED"));
    }

    // ------------------------------------------------------------------ 추적 정지 종결

    @Test
    @DisplayName("[OA-A14 · A15] 추적 정지 종결 — 집화 확인 필요 · 배송완료는 409 · 사유 공백 · 수령 시각이 발송 전 · 미래면 400 · 배송완료 처리는 출처 · 타이머 · 이력")
    void stalledResolveMatrix() throws Exception {
        OrderDeliveryGroup pickup = shippingGroup("400080008001");
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'PICKUP_UNCONFIRMED', shipped_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(40), pickup.getId());
        lost(pickup, "분실").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
        OrderDeliveryGroup delivered = deliveredGroup("400080008002", LocalDateTime.now().minusDays(30));
        stalled(delivered, 30);
        lost(delivered, "분실").andExpect(status().isConflict());

        OrderDeliveryGroup group = shippingGroup("400080008003");
        stalled(group, 29);
        LocalDateTime shippedAt = ((Timestamp) groupRow(group).get("shipped_at")).toLocalDateTime();
        lost(group, " ").andExpect(status().isBadRequest());
        markDelivered(group, LocalDateTime.now().minusDays(1), " ").andExpect(status().isBadRequest());
        markDelivered(group, shippedAt.minusHours(1), "소비자 수령 확인").andExpect(status().isBadRequest());
        markDelivered(group, LocalDateTime.now().plusHours(1), "소비자 수령 확인").andExpect(status().isBadRequest());
        assertThat(groupRow(group)).containsEntry("fulfillment_status", "SHIPPING");

        LocalDateTime receivedAt = LocalDateTime.now().minusDays(2).withNano(0);
        JsonNode resolved = json(markDelivered(group, receivedAt, "소비자 수령 확인").andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.groups[0].shipping.deliveredSourceLabel").value("운영자 처리 · 추적 정지"))
                .andExpect(jsonPath("$.groups[0].actions.canMarkLost").value(false)));
        assertThat(at(resolved.at("/groups/0/purchaseConfirm/dueAt").asText())).isEqualTo(receivedAt.plusDays(7));
        assertThat(groupRow(group)).containsEntry("delivered_source", "ADMIN_STALLED").containsEntry("delivered_by", operator.getId());
        assertThat(events(group, "DELIVERED")).last().asString().contains("추적 정지 28일 경과").contains("소비자 수령 확인");
        assertThat(refundTasks(group)).isEmpty();
        lost(group, "분실").andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 없는 하위주문

    @Test
    @DisplayName("[OA-A16] 조치 7종 — 없는 하위주문은 404 ORDER_GROUP_NOT_FOUND")
    void missingGroup() throws Exception {
        String base = ADMIN_ORDERS + "/groups/999999";
        String now = LocalDateTime.now().minusHours(1).withNano(0).toString();
        mockMvc.perform(patch(base + "/delivered-at").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("deliveredAt", now, "reason", "정정"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        List<Map.Entry<String, Map<String, Object>>> posts = List.of(
                Map.entry("/shipment", Map.of("carrier", "CJ", "trackingNumber", "400080009001", "note", "대행")),
                Map.entry("/cancel", Map.of("reasonCode", "DEFECT", "consumerMessage", "회수")),
                Map.entry("/refund-tasks", Map.of("reason", "RECALL", "amount", 1_000, "detail", "근거")),
                Map.entry("/defect-claims", Map.of("items", List.of(Map.of("orderProductId", 1, "quantity", 1)),
                        "reasonCode", "DAMAGED_OR_DEFECTIVE", "detail", "내용", "evidenceImageUrls", List.of(EVIDENCE))),
                Map.entry("/lost", Map.of("reason", "분실")),
                Map.entry("/delivered", Map.of("deliveredAt", now, "reason", "수령")));
        for (Map.Entry<String, Map<String, Object>> action : posts) {
            adminPost(base + action.getKey(), action.getValue()).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------ 브랜드 · 소비자와 겹칠 때(X-11)

    @Test
    @DisplayName("[X-11 ②] 운영자 대행 송장 · 브랜드 송장 동시 — 하나만 등록 · 송장번호는 이긴 쪽 · 등록 이력 1건")
    void adminAndBrandShipmentRace() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        overdue(group, 3);

        List<String> results = ConcurrentCalls.race(
                () -> {
                    commandService.registerShipment(operator.getId(), group.getId(),
                            new AdminOrderDto.ShipmentRequest(showroomz.domain.order.type.DeliveryCarrier.CJ, "400080010001", "대행"));
                    return "ADMIN";
                },
                () -> json(registerShipment(group, "CJ", "400080010002")).get("succeeded").asInt() == 1 ? "BRAND" : "BRAND_SKIPPED");

        assertThat(results).filteredOn(result -> result.equals("ADMIN") || result.equals("BRAND")).hasSize(1);
        String expectedTracking = results.contains("ADMIN") ? "400080010001" : "400080010002";
        assertThat(groupRow(group)).containsEntry("fulfillment_status", "SHIPPING").containsEntry("tracking_number", expectedTracking);
        assertThat(events(group, "INVOICE_REGISTERED")).hasSize(1);
    }

    @Test
    @DisplayName("[X-11 ③] 운영자 직권 취소 · 소비자 취소 요청 동시 — 취소된 하위주문에 검토 중 요청이 남지 않는다 · 환불 1건 이하")
    void adminCancelAndConsumerRequestRace() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        OrderProduct item = itemsOf(group).get(0);

        List<String> results = ConcurrentCalls.race(
                () -> {
                    commandService.cancel(operator.getId(), group.getId(),
                            new AdminOrderDto.CancelCommand(SellerCancelReason.DEFECT, "위해성 회수"));
                    return "ADMIN";
                },
                () -> String.valueOf(consumerPost("/v1/user/orders/" + orderOf(group) + "/cancel-requests", Map.of(
                        "deliveryGroupId", group.getId(), "orderProductIds", List.of(item.getId()),
                        "reasonCode", "CHANGE_OF_MIND")).andReturn().getResponse().getStatus()));

        String status = (String) groupRow(group).get("fulfillment_status");
        boolean pendingRequest = cancelRequestRepository.existsPendingByGroup(group.getId());
        assertThat(status.equals("CANCELLED") && pendingRequest).as("취소됐는데 검토 중 요청이 남음 %s", results).isFalse();
        if (status.equals("CANCELLED")) {
            assertThat(results.get(0)).isEqualTo("ADMIN");
            assertThat(results.get(1)).startsWith("4");
            assertThat(refundTasks(group)).hasSize(1);
        } else {
            assertThat(results.get(0)).isEqualTo("CANCEL_REQUEST_PENDING_EXISTS");
            assertThat(results.get(1)).isEqualTo("201");
            assertThat(pendingRequest).isTrue();
            assertThat(refundTasks(group)).isEmpty();
        }
    }

    // ------------------------------------------------------------------ 도우미

    private void overdue(OrderDeliveryGroup group, int notices) {
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ?, overdue_notice_count = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(1), notices, group.getId());
    }

    private void stalled(OrderDeliveryGroup group, int daysAgo) {
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ?, shipped_at = ? "
                + "WHERE delivery_group_id = ?", LocalDateTime.now().minusDays(daysAgo),
                LocalDateTime.now().minusDays(daysAgo + 1), group.getId());
    }

    private List<String> events(OrderDeliveryGroup group, String eventType) {
        return jdbc.queryForList("SELECT detail FROM order_fulfillment_history WHERE delivery_group_id = ? AND event_type = ? "
                + "ORDER BY fulfillment_history_id", String.class, group.getId(), eventType);
    }

    private List<String> actorsOf(OrderDeliveryGroup group, String eventType) {
        return jdbc.queryForList("SELECT actor_type FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = ?", String.class, group.getId(), eventType);
    }

    private int stock(Long variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variant WHERE variant_id = ?", Integer.class, variantId);
    }

    private String paymentIdOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT paid_payment_id FROM orders WHERE order_id = ?", String.class, orderOf(group));
    }

    private Map<String, Object> groupRow(OrderDeliveryGroup group) {
        return jdbc.queryForMap("SELECT * FROM order_delivery_group WHERE delivery_group_id = ?", group.getId());
    }

    private Long orderOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?", Long.class,
                group.getId());
    }

    private String refundTasksUrl(OrderDeliveryGroup group) {
        return ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks";
    }

    private ResultActions correct(OrderDeliveryGroup group, Map<String, Object> body) throws Exception {
        return mockMvc.perform(patch(ADMIN_ORDERS + "/groups/" + group.getId() + "/delivered-at")
                .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions shipment(OrderDeliveryGroup group, String carrier, String trackingNumber, String note) throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/shipment",
                Map.of("carrier", carrier, "trackingNumber", trackingNumber, "note", note));
    }

    private ResultActions cancel(OrderDeliveryGroup group, String reasonCode, String message) throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/cancel",
                Map.of("reasonCode", reasonCode, "consumerMessage", message));
    }

    private ResultActions enqueue(OrderDeliveryGroup group, String reason, int amount) throws Exception {
        return adminPost(refundTasksUrl(group), Map.of("reason", reason, "amount", amount, "detail", "식약처 회수 명령"));
    }

    private ResultActions defect(OrderDeliveryGroup group, List<Map<String, Object>> items, String reasonCode, String detail)
            throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/defect-claims", Map.of("items", items,
                "reasonCode", reasonCode, "detail", detail, "evidenceImageUrls", List.of(EVIDENCE)));
    }

    private ResultActions lost(OrderDeliveryGroup group, String reason) throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/lost", Map.of("reason", reason));
    }

    private ResultActions markDelivered(OrderDeliveryGroup group, LocalDateTime deliveredAt, String reason) throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/delivered",
                Map.of("deliveredAt", deliveredAt.toString(), "reason", reason));
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions consumerPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /** 응답 시각 — 직렬화가 {@code ...Z} 를 붙여 내리므로 떼고 읽는다(값은 서버 로컬 시각 그대로다). */
    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text.endsWith("Z") ? text.substring(0, text.length() - 1) : text);
    }
}

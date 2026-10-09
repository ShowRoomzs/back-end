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
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.api.admin.transaction.service.AdminOrderCommandService;
import showroomz.api.scenario.OrderFlowTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.support.IntegrationTest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 주문 조회(06a · 37 설계서 7절 T1 ~ T6) + 06d 와의 대행 판정 정합(T7). 운영자 조치 5종의 가드 · 효과와 탭 · 검색을 본다.
 * 하위주문은 실제 결제 경로로만 만들고, SQL 은 시각 소급(발송 기한 · 발송 · 배송완료)과 자동 알림 횟수에만 쓴다.
 */
@IntegrationTest
class AdminOrderIntegrationTest extends OrderFlowTestSupport {

    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final String ADMIN_EXCEPTIONS = "/v1/admin/order-exceptions";
    private static final int DELIVERY_FEE = 3_000;

    @Autowired private AdminOrderCommandService commandService;

    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpAdmin() {
        operator = fixture.createAdmin("orders-ops@showroomz.test", "운영자");
        admin = adminToken(operator);
    }

    // ------------------------------------------------------------------ T1 배송완료일 정정

    @Test
    @DisplayName("[T1] 정정 — 배송완료 아님 409 · 발송 전 시각 400 · 정상이면 출처 「운영자 정정」 · 교환 재시작 기산점도 같은 만큼 민다")
    void correctDeliveredAt() throws Exception {
        OrderDeliveryGroup shipping = shippingGroup("400060001001");
        correct(shipping, LocalDateTime.now().minusHours(1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));

        OrderDeliveryGroup delivered = deliveredGroup("400060001002", LocalDateTime.now().minusHours(1).withNano(0));
        LocalDateTime shippedAt = LocalDateTime.now().minusDays(5).withNano(0);
        LocalDateTime deliveredAt = LocalDateTime.now().minusDays(3).withNano(0);
        LocalDateTime restartAt = LocalDateTime.now().minusDays(1).withNano(0);
        jdbc.update("UPDATE order_delivery_group SET shipped_at = ?, delivered_at = ?, confirm_restart_at = ? "
                + "WHERE delivery_group_id = ?", shippedAt, deliveredAt, restartAt, delivered.getId());

        correct(delivered, shippedAt.minusHours(1)).andExpect(status().isBadRequest());
        correct(delivered, LocalDateTime.now().plusHours(1)).andExpect(status().isBadRequest());

        LocalDateTime corrected = deliveredAt.plusDays(1);
        correct(delivered, corrected).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].shipping.deliveredSourceLabel").value("운영자 정정"));

        Map<String, Object> row = groupRow(delivered);
        assertThat(((Timestamp) row.get("delivered_at")).toLocalDateTime()).isEqualTo(corrected);
        assertThat(row.get("delivered_source")).isEqualTo("ADMIN");
        assertThat(((Timestamp) row.get("confirm_restart_at")).toLocalDateTime())
                .isEqualTo(restartAt.plus(Duration.between(deliveredAt, corrected)));
        assertThat(fulfillmentEvents(delivered)).contains("DELIVERED_AT_CORRECTED");
    }

    // ------------------------------------------------------------------ T2 대행 송장

    @Test
    @DisplayName("[T2] 대행 송장 — 알림 2회 409 · 사유 없음 400 · 쿠팡 400 · 중복 409 · 검토 중 요청 409 · 3회면 등록")
    void registerShipmentOnBehalf() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        overdue(group, 2);
        shipment(group, "CJ", "400060002001", "무응답").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ACT_ON_BEHALF_NOT_ALLOWED"));

        overdue(group, 3);
        shipment(group, "CJ", "400060002001", " ").andExpect(status().isBadRequest());
        shipment(group, "COUPANG", "400060002001", "무응답").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVOICE_FORMAT_INVALID"));
        shippingGroup("400060002999");
        shipment(group, "CJ", "400060002999", "무응답").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVOICE_DUPLICATE"));

        OrderDeliveryGroup requested = preparingGroup();
        overdue(requested, 3);
        seedCancelRequest(requested, itemsOf(requested));
        shipment(requested, "CJ", "400060002002", "무응답").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_PENDING_EXISTS"));

        shipment(group, "CJ", "400060002001", "자동 알림 3회 무응답 · 소비자 문의").andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].status").value("SHIPPING"));
        assertThat(jdbc.queryForObject("SELECT detail FROM order_fulfillment_history WHERE delivery_group_id = ? "
                        + "AND event_type = 'INVOICE_REGISTERED' AND actor_type = 'ADMIN'", String.class, group.getId()))
                .contains("운영자 대행").contains("자동 알림 3회 무응답");
    }

    // ------------------------------------------------------------------ T3 대행 직권 취소

    @Test
    @DisplayName("[T3] 직권 취소 — 상품 하자는 대행 조건 없이 · 환불 = 항목 + 배송비(PG 자동) · 그 밖의 사유는 조건 미충족 409")
    void cancelOnBehalf() throws Exception {
        OrderDeliveryGroup notOverdue = paidGroup();
        cancel(notOverdue, "ETC").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ACT_ON_BEHALF_NOT_ALLOWED"));

        cancel(notOverdue, "DEFECT").andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.groups[0].refunds[0].origin").value("PG_AUTO"));
        assertThat(refundTasks(notOverdue)).extracting(RefundTask::source, RefundTask::amount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SELLER_DIRECT_CANCEL", CREAM_PRICE + DELIVERY_FEE));
        assertThat(jdbc.queryForObject("SELECT actor_type FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = 'CANCELLED_BY_SELLER'", String.class, notOverdue.getId())).isEqualTo("ADMIN");

        OrderDeliveryGroup overdue = preparingGroup();
        overdue(overdue, 3);
        cancel(overdue, "ETC").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ T4 하자 반품 대신 열기

    @Test
    @DisplayName("[T4] 하자 반품 — 단순 변심 400 · 증빙 없음 400 · 배송완료 3개월 경과 409 · 정상이면 운영자 개설 · 구매확정 타이머 정지")
    void openDefectClaim() throws Exception {
        OrderDeliveryGroup group = deliveredGroup("400060004001", LocalDateTime.now().minusHours(1).withNano(0));
        Long productId = itemsOf(group).get(0).getId();

        defect(group, productId, "CHANGE_OF_MIND", List.of("https://img.test/d.jpg")).andExpect(status().isBadRequest());
        defect(group, productId, "DAMAGED_OR_DEFECTIVE", List.of()).andExpect(status().isBadRequest());

        OrderDeliveryGroup old = deliveredGroup("400060004002", LocalDateTime.now().minusHours(1).withNano(0));
        jdbc.update("UPDATE order_delivery_group SET delivered_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusMonths(3).minusDays(1), old.getId());
        defect(old, itemsOf(old).get(0).getId(), "DAMAGED_OR_DEFECTIVE", List.of("https://img.test/d.jpg"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_NOT_ELIGIBLE"));

        JsonNode opened = json(defect(group, productId, "DAMAGED_OR_DEFECTIVE", List.of("https://img.test/d.jpg"))
                .andExpect(status().isOk()));
        long claimId = opened.get("claimIds").get(0).asLong();
        assertThat(jdbc.queryForMap("SELECT opened_by, open_reason, fee_bearer FROM order_claim WHERE claim_id = ?", claimId))
                .containsEntry("opened_by", "OPERATOR").containsEntry("open_reason", "구매확정 후 하자")
                .containsEntry("fee_bearer", "SELLER");
        assertThat(groupRow(group).get("confirm_paused_at")).isNotNull();
        adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId())
                .andExpect(jsonPath("$.groups[0].purchaseConfirm.paused").value(true));
    }

    // ------------------------------------------------------------------ T5 탭

    @Test
    @DisplayName("[T5] 탭 — 배송 이상에 반송 중 · 배지가 들어오고 반송 완료는 빠진다 · 취소 탭에 검토 중 요청 · 상태 셀렉트")
    void tabs() throws Exception {
        OrderDeliveryGroup returning = returningGroup("400060005001");
        OrderDeliveryGroup alerted = shippingGroup("400060005002");
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED' WHERE delivery_group_id = ?",
                alerted.getId());
        OrderDeliveryGroup requested = preparingGroup();
        seedCancelRequest(requested, itemsOf(requested));
        OrderDeliveryGroup plain = shippingGroup("400060005003");

        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?tab=DELIVERY_ISSUE")))
                .containsExactlyInAnyOrder(orderOf(returning), orderOf(alerted));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?tab=CANCEL"))).containsExactly(orderOf(requested));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?status=SHIPPING")))
                .containsExactlyInAnyOrder(orderOf(alerted), orderOf(plain));

        jdbc.update("UPDATE order_delivery_group SET return_completed_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now(), returning.getId());
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?tab=DELIVERY_ISSUE"))).containsExactly(orderOf(alerted));
        adminGet(ADMIN_ORDERS + "/summary").andExpect(jsonPath("$.tabCounts.DELIVERY_ISSUE").value(1))
                .andExpect(jsonPath("$.tabCounts.CANCEL").value(1))
                .andExpect(jsonPath("$.tabCounts.ALL").value(4));
    }

    // ------------------------------------------------------------------ T10 사유 환불 편입 가드

    @Test
    @DisplayName("[T10] 사유 환불 편입 — 반송 중은 버튼 없음 · 409(반송 완료 시 자동 환불) · 06b 전용 사유(반려 이의 인용 · 검수 무응답)는 400")
    void operatorRefundGuards() throws Exception {
        OrderDeliveryGroup returning = returningGroup("400060010001");
        adminGet(ADMIN_ORDERS + "/" + orderOf(returning))
                .andExpect(jsonPath("$.groups[0].actions.canEnqueueRefund").value(false));
        operatorRefund(returning, "RECALL").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
        jdbc.update("UPDATE order_delivery_group SET return_completed_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now(), returning.getId());
        adminGet(ADMIN_ORDERS + "/" + orderOf(returning))
                .andExpect(jsonPath("$.groups[0].actions.canEnqueueRefund").value(false));

        OrderDeliveryGroup delivered = deliveredGroup("400060010002", LocalDateTime.now().minusDays(1));
        adminGet(ADMIN_ORDERS + "/" + orderOf(delivered))
                .andExpect(jsonPath("$.groups[0].actions.canEnqueueRefund").value(true));
        for (String reason : List.of("DISPUTE_ACCEPTED", "INSPECTION_UNANSWERED")) {
            operatorRefund(delivered, reason).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(refundTasks(returning)).isEmpty();
        assertThat(refundTasks(delivered)).isEmpty();
        operatorRefund(delivered, "POST_CONFIRM_DEFECT").andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].refunds[0].origin").value("OPERATOR"));
    }

    // ------------------------------------------------------------------ T11 상세 404 · 열람 기록

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("[T11] 상세 — 열 때마다 열람 로그(운영자 · 주문) · 결제 전 주문과 없는 주문은 404 ORDER_NOT_FOUND")
    void detailAccessLogAndNotFound(CapturedOutput output) throws Exception {
        OrderDeliveryGroup group = paidGroup();
        Long orderId = orderOf(group);
        adminGet(ADMIN_ORDERS + "/" + orderId).andExpect(status().isOk());
        assertThat(output).contains("어드민 주문 상세 열람 - operatorId: " + operator.getId() + ", orderId: " + orderId);

        jdbc.update("UPDATE orders SET paid_at = NULL WHERE order_id = ?", orderId);
        adminGet(ADMIN_ORDERS + "/" + orderId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        assertThat(orderIds(adminGet(ADMIN_ORDERS))).doesNotContain(orderId);
        adminGet(ADMIN_ORDERS + "/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ T12 동시 조치

    @Test
    @DisplayName("[T12] 동시 조치 — 분실 · 배송완료 처리가 겹치면 하나만 · 대행 송장 두 건이 겹치면 하나만(나머지 409 · 환불 · 이력 중복 없음)")
    void concurrentOperatorActions() throws Exception {
        OrderDeliveryGroup stalledGroup = shippingGroup("400060012001");
        stalled(stalledGroup, 29);
        List<String> resolved = ConcurrentCalls.race(
                () -> {
                    commandService.markLost(operator.getId(), stalledGroup.getId(),
                            new AdminOrderDto.MarkLostRequest("택배사 분실 확인"));
                    return "LOST";
                },
                () -> {
                    commandService.markDelivered(operator.getId(), stalledGroup.getId(),
                            new AdminOrderDto.MarkDeliveredRequest(LocalDateTime.now().minusHours(1).withNano(0), "소비자 수령 확인"));
                    return "DELIVERED";
                });
        assertThat(resolved).filteredOn(result -> result.equals("ORDER_STATE_CHANGED")).hasSize(1);
        String status = (String) groupRow(stalledGroup).get("fulfillment_status");
        if (resolved.contains("LOST")) {
            assertThat(status).isEqualTo("CANCELLED");
            assertThat(refundTasks(stalledGroup)).hasSize(1);
        } else {
            assertThat(status).isEqualTo("DELIVERED");
            assertThat(refundTasks(stalledGroup)).isEmpty();
        }

        OrderDeliveryGroup overdueGroup = preparingGroup();
        overdue(overdueGroup, 3);
        List<String> shipped = ConcurrentCalls.race(
                () -> {
                    commandService.registerShipment(operator.getId(), overdueGroup.getId(),
                            new AdminOrderDto.ShipmentRequest(DeliveryCarrier.CJ, "400060012002", "무응답 대행 A"));
                    return "SHIPPED";
                },
                () -> {
                    commandService.registerShipment(operator.getId(), overdueGroup.getId(),
                            new AdminOrderDto.ShipmentRequest(DeliveryCarrier.CJ, "400060012003", "무응답 대행 B"));
                    return "SHIPPED";
                });
        assertThat(shipped).containsExactlyInAnyOrder("SHIPPED", "ORDER_STATE_CHANGED");
        assertThat(groupRow(overdueGroup)).containsEntry("fulfillment_status", "SHIPPING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = 'INVOICE_REGISTERED'", Integer.class, overdueGroup.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ T6 검색

    @Test
    @DisplayName("[T6] 키워드 — 하위주문번호 · 브랜드명 · 송장(숫자) · 페이지 크기 상한")
    void keyword() throws Exception {
        OrderDeliveryGroup target = shippingGroup("400060006001");
        shippingGroup("400060006002");
        String subOrderNumber = (String) groupRow(target).get("sub_order_number");

        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?keyword=" + subOrderNumber))).containsExactly(orderOf(target));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?keyword=400060006001"))).containsExactly(orderOf(target));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?keyword=" + target.getMarketName()))).hasSize(2);
        // 공통 페이징 검증(PagingRequest)이 먼저 막는다 — 코드는 INVALID_INPUT, 상한은 같다.
        adminGet(ADMIN_ORDERS + "?size=101").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ T7 06a · 06d 정합

    @Test
    @DisplayName("[T7] 대행 판정 — 06a 상세 canRegisterShipment 와 06d actOnBehalfAvailable 이 같은 값(알림 2회 거짓 · 3회 참)")
    void actOnBehalfConsistentWithExceptions() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        overdue(group, 2);
        assertConsistent(group, false);
        overdue(group, 3);
        assertConsistent(group, true);
    }

    private void assertConsistent(OrderDeliveryGroup group, boolean expected) throws Exception {
        adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId())
                .andExpect(jsonPath("$.groups[0].actions.canRegisterShipment").value(expected));
        JsonNode rows = json(adminGet(ADMIN_EXCEPTIONS + "?tab=DELAY"));
        JsonNode items = rows.has("page") ? rows.get("page").get("content") : rows;
        JsonNode row = StreamSupport.stream(items.spliterator(), false)
                .filter(item -> item.get("deliveryGroupId").asLong() == group.getId()).findFirst().orElseThrow();
        assertThat(row.get("actOnBehalfAvailable").asBoolean()).isEqualTo(expected);
    }

    // ------------------------------------------------------------------ T8 조회 확장(37 설계서 8절 #1 · #2)

    @Test
    @DisplayName("[T8] 상세 조건 · 검색 대상 · 정렬 — 공구 · 인플루언서 · 결제수단 · 이상 유형 · PG 거래번호 · 금액순 · 이상 지속 오래된순")
    void extendedSearchAndSort() throws Exception {
        OrderDeliveryGroup cheap = shippingGroup("400060008001");
        OrderDeliveryGroup pricey = preparing(paidGroupWithTwoItems());
        Long groupBuyId = jdbc.queryForObject("SELECT group_buy_id FROM order_delivery_group WHERE delivery_group_id = ?",
                Long.class, cheap.getId());

        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?groupBuyId=" + groupBuyId))).hasSize(2);
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?groupBuyId=" + (groupBuyId + 999)))).isEmpty();
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?creatorId=" + creator.getId()))).hasSize(2);
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?creatorId=" + (creator.getId() + 999)))).isEmpty();
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?paymentMethod=CARD"))).hasSize(2);
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?paymentMethod=EASY_PAY"))).isEmpty();
        adminGet(ADMIN_ORDERS + "/summary?paymentMethod=EASY_PAY").andExpect(jsonPath("$.tabCounts.ALL").value(0));

        String paymentId = jdbc.queryForObject("SELECT o.paid_payment_id FROM orders o WHERE o.order_id = ?", String.class,
                orderOf(cheap));
        jdbc.update("UPDATE payment SET pg_tx_id = 'tosspay_t8' WHERE payment_id = ?", paymentId);
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?searchType=PG_TX_ID&keyword=tosspay_t8"))).containsExactly(orderOf(cheap));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?searchType=PG_TX_ID&keyword=" + paymentId))).containsExactly(orderOf(cheap));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?searchType=TRACKING_NUMBER&keyword=400060008001"))).containsExactly(orderOf(cheap));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?searchType=RECIPIENT&keyword=400060008001"))).isEmpty();
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?searchType=BRAND&keyword=" + cheap.getMarketName()))).hasSize(2);

        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?sort=AMOUNT_DESC"))).containsExactly(orderOf(pricey), orderOf(cheap));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?sort=PAID_ASC"))).containsExactly(orderOf(cheap), orderOf(pricey));

        OrderDeliveryGroup stalledOld = shippingGroup("400060008002");
        OrderDeliveryGroup stalledNew = shippingGroup("400060008003");
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(10), stalledOld.getId());
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'PICKUP_UNCONFIRMED', last_tracking_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(2), stalledNew.getId());
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?tab=DELIVERY_ISSUE&trackingAlert=STALLED")))
                .containsExactly(orderOf(stalledOld));
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?tab=DELIVERY_ISSUE&sort=ISSUE_OLDEST")))
                .containsExactly(orderOf(stalledOld), orderOf(stalledNew));
    }

    // ------------------------------------------------------------------ T9 추적 정지 종결(41 보고 3번 · 권고 3)

    @Test
    @DisplayName("[T9] 추적 정지 종결 — 27일은 조건 미충족 · 28일이면 분실(취소 · 재고 그대로 · PG 자동 환불) 또는 배송완료(출처 · 타이머) · 06d 표시")
    void resolveStalled() throws Exception {
        OrderDeliveryGroup lost = shippingGroup("400060009001");
        Long variantId = itemsOf(lost).get(0).getVariantId();
        int stockBefore = stock(variantId);
        stalled(lost, 27);
        adminGet(ADMIN_ORDERS + "/" + orderOf(lost)).andExpect(jsonPath("$.groups[0].actions.canMarkLost").value(false))
                .andExpect(jsonPath("$.groups[0].actions.canMarkDelivered").value(false));
        adminPost(ADMIN_ORDERS + "/groups/" + lost.getId() + "/lost", Map.of("reason", "택배사 분실 확인"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));

        stalled(lost, 29);
        adminGet(ADMIN_ORDERS + "/" + orderOf(lost)).andExpect(jsonPath("$.groups[0].actions.canMarkLost").value(true))
                .andExpect(jsonPath("$.groups[0].actions.canMarkDelivered").value(true));
        adminPost(ADMIN_ORDERS + "/groups/" + lost.getId() + "/lost", Map.of("reason", "택배사 분실 확인"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.groups[0].refunds[0].source").value("LOST_IN_TRANSIT"))
                .andExpect(jsonPath("$.groups[0].refunds[0].origin").value("PG_AUTO"));
        assertThat(refundTasks(lost)).singleElement().satisfies(task -> {
            assertThat(task.source()).isEqualTo("LOST_IN_TRANSIT");
            assertThat(task.amount()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
            assertThat(task.status()).isEqualTo("DONE");
        });
        assertThat(stock(variantId)).isEqualTo(stockBefore);
        assertThat(groupRow(lost)).containsEntry("cancel_type", "LOST").containsEntry("status_at_cancel", "SHIPPING");
        assertThat(jdbc.queryForMap("SELECT status, cancel_type FROM order_product WHERE delivery_group_id = ?", lost.getId()))
                .containsEntry("status", "CANCELLED").containsEntry("cancel_type", "LOST");
        assertThat(fulfillmentEvents(lost)).contains("LOST_RESOLVED");
        adminPost(ADMIN_ORDERS + "/groups/" + lost.getId() + "/lost", Map.of("reason", "다시")).andExpect(status().isConflict());

        OrderDeliveryGroup arrived = shippingGroup("400060009002");
        stalled(arrived, 29);
        adminPost(ADMIN_ORDERS + "/groups/" + arrived.getId() + "/delivered",
                Map.of("deliveredAt", LocalDateTime.now().plusHours(1).toString(), "reason", "x")).andExpect(status().isBadRequest());
        adminPost(ADMIN_ORDERS + "/groups/" + arrived.getId() + "/delivered",
                Map.of("deliveredAt", LocalDateTime.now().minusHours(2).withNano(0).toString(), "reason", "소비자 수령 확인"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.groups[0].shipping.deliveredSourceLabel").value("운영자 처리 · 추적 정지"))
                .andExpect(jsonPath("$.groups[0].purchaseConfirm.dueAt").exists())
                .andExpect(jsonPath("$.groups[0].actions.canCorrectDeliveredAt").value(true));
        assertThat(groupRow(arrived)).containsEntry("delivered_source", "ADMIN_STALLED").containsEntry("tracking_alert", null);
        assertThat(jdbc.queryForObject("SELECT actor_type FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = 'DELIVERED'", String.class, arrived.getId())).isEqualTo("ADMIN");

        OrderDeliveryGroup watching = shippingGroup("400060009003");
        stalled(watching, 30);
        JsonNode rows = json(adminGet(ADMIN_EXCEPTIONS + "?tab=DELIVERY&kind=TRACKING_STALLED")).at("/page/content");
        JsonNode row = StreamSupport.stream(rows.spliterator(), false)
                .filter(item -> item.get("deliveryGroupId").asLong() == watching.getId()).findFirst().orElseThrow();
        assertThat(row.get("actOnBehalfAvailable").asBoolean()).isTrue();
        assertThat(row.get("handlerLabel").asText()).contains("운영자 판정");
    }

    private void stalled(OrderDeliveryGroup group, int daysAgo) {
        // 발송은 마지막 추적보다 앞서야 한다 — 수령 시각 검사(발송 이후)의 기준.
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ?, shipped_at = ? "
                + "WHERE delivery_group_id = ?", LocalDateTime.now().minusDays(daysAgo),
                LocalDateTime.now().minusDays(daysAgo + 1), group.getId());
    }

    private int stock(Long variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variant WHERE variant_id = ?", Integer.class, variantId);
    }

    // ------------------------------------------------------------------ 도우미

    /** 발송 기한을 어제로 소급하고 자동 알림 횟수를 맞춘다(시각 · 횟수만 — 상태는 건드리지 않는다). */
    private void overdue(OrderDeliveryGroup group, int notices) {
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ?, overdue_notice_count = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(1), notices, group.getId());
    }

    private Map<String, Object> groupRow(OrderDeliveryGroup group) {
        return jdbc.queryForMap("SELECT * FROM order_delivery_group WHERE delivery_group_id = ?", group.getId());
    }

    private Long orderOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?", Long.class,
                group.getId());
    }

    private List<Long> orderIds(ResultActions actions) throws Exception {
        JsonNode rows = json(actions.andExpect(status().isOk())).get("content");
        return StreamSupport.stream(rows.spliterator(), false).map(row -> row.get("orderId").asLong()).toList();
    }

    private ResultActions correct(OrderDeliveryGroup group, LocalDateTime deliveredAt) throws Exception {
        return mockMvc.perform(patch(ADMIN_ORDERS + "/groups/" + group.getId() + "/delivered-at")
                .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("deliveredAt", deliveredAt.toString(), "reason", "소비자 수령일 이의 · 택배사 확인"))));
    }

    private ResultActions shipment(OrderDeliveryGroup group, String carrier, String trackingNumber, String note)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("carrier", carrier);
        body.put("trackingNumber", trackingNumber);
        body.put("note", note);
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/shipment", body);
    }

    private ResultActions cancel(OrderDeliveryGroup group, String reasonCode) throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/cancel",
                Map.of("reasonCode", reasonCode, "consumerMessage", "판매자 사정으로 주문이 취소되었습니다."));
    }

    private ResultActions operatorRefund(OrderDeliveryGroup group, String reason) throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", reason, "amount", 1_000, "detail", "운영자 사유 환불 근거"));
    }

    private ResultActions defect(OrderDeliveryGroup group, Long orderProductId, String reasonCode, List<String> evidence)
            throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/defect-claims", Map.of(
                "items", List.of(Map.of("orderProductId", orderProductId, "quantity", 1)),
                "reasonCode", reasonCode, "detail", "1:1 문의 — 구매확정 후 용기 파손 발견", "evidenceImageUrls", evidence));
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}

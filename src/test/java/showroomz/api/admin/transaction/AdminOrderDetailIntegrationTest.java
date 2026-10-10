package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.scenario.OrderFlowTestSupport;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderCancelRequestService;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 주문 조회(06a) 조회 상세 — 43 테스트 상세 2-1 · 2-2 의 목록 행 필드 · 정렬 · 페이지 · 결제일 경계 · 빈 결과 · 요약 정합과 상세
 * B1 · B2 · B3 · B4 · B7 블록의 필드 값을 하나씩 본다. 조치(쓰기)의 가드는 {@code AdminOrderActionGuardIntegrationTest}.
 */
@IntegrationTest
class AdminOrderDetailIntegrationTest extends OrderFlowTestSupport {

    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final int DELIVERY_FEE = 3_000;
    private static final int CONFIRM_DAYS = 7;

    @Autowired private OrderCancelRequestService cancelRequestService;

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("orders-detail@showroomz.test", "운영자"));
    }

    // ------------------------------------------------------------------ 목록 · 요약

    @Test
    @DisplayName("[OA-L01] 목록 행 — 주문 단위 값(번호 · 결제 시각 · 수취인 · 결제액) · 하위주문 요약 필드 · 주의 건수는 하위주문 단위로 한 번")
    void listRowFields() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        Map<String, Object> order = orderRow(group);

        JsonNode row = rowOf(group);
        assertThat(row.get("orderNumber").asText()).isEqualTo(order.get("order_number"));
        assertThat(row.get("recipientName").asText()).isEqualTo(order.get("recipient_name"));
        assertThat(row.get("totalAmount").asInt()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        assertThat(row.get("paidAt").isNull()).isFalse();
        assertThat(row.get("attentionCount").asInt()).isZero();
        JsonNode summary = row.get("groups").get(0);
        assertThat(summary.get("deliveryGroupId").asLong()).isEqualTo(group.getId());
        assertThat(summary.get("subOrderNumber").asText()).isEqualTo(groupRow(group).get("sub_order_number"));
        assertThat(summary.get("brandName").asText()).isEqualTo(group.getMarketName());
        assertThat(summary.get("status").asText()).isEqualTo("NEW");
        assertThat(summary.get("statusLabel").asText()).isNotBlank();
        assertThat(summary.get("statusTone").isNull()).isFalse();
        assertThat(summary.get("trackingAlert").isNull()).isTrue();
        assertThat(summary.get("shipOverdue").asBoolean()).isFalse();
        assertThat(summary.get("cancelRequested").asBoolean()).isFalse();

        // 발송 기한 경과 + 검토 중 취소 요청 — 같은 하위주문이므로 주의 건수는 1이다.
        OrderDeliveryGroup preparing = preparing(group);
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(1), preparing.getId());
        assertThat(rowOf(group).get("groups").get(0).get("shipOverdue").asBoolean()).isTrue();
        assertThat(rowOf(group).get("attentionCount").asInt()).isEqualTo(1);
        seedCancelRequest(preparing, itemsOf(preparing));
        JsonNode both = rowOf(group);
        assertThat(both.get("groups").get(0).get("cancelRequested").asBoolean()).isTrue();
        assertThat(both.get("attentionCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("[OA-L01 · OA-L10] 정렬 · 페이지 — 결제 최신순 · 같은 시각이면 주문 id 역순 · 쪽 나눔 · 크기 0 · 101 은 400")
    void defaultSortAndPaging() throws Exception {
        Long first = orderOf(paidGroup());
        Long second = orderOf(paidGroup());
        Long third = orderOf(paidGroup());
        LocalDateTime same = LocalDateTime.now().minusDays(1).withNano(0);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id IN (?, ?, ?)", same, first, second, third);

        assertThat(orderIds(adminGet(ADMIN_ORDERS))).containsExactly(third, second, first);
        JsonNode page1 = json(adminGet(ADMIN_ORDERS + "?page=1&size=2").andExpect(status().isOk()));
        assertThat(ids(page1)).containsExactly(third, second);
        assertThat(page1.at("/pageInfo/totalResults").asLong()).isEqualTo(3);
        assertThat(page1.at("/pageInfo/hasNext").asBoolean()).isTrue();
        JsonNode page2 = json(adminGet(ADMIN_ORDERS + "?page=2&size=2").andExpect(status().isOk()));
        assertThat(ids(page2)).containsExactly(first);
        assertThat(page2.at("/pageInfo/hasNext").asBoolean()).isFalse();

        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", same.plusMinutes(1), first);
        assertThat(orderIds(adminGet(ADMIN_ORDERS))).containsExactly(first, third, second);
        adminGet(ADMIN_ORDERS + "?size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminGet(ADMIN_ORDERS + "?size=101").andExpect(status().isBadRequest());
        adminGet(ADMIN_ORDERS + "?size=100").andExpect(status().isOk());
    }

    @Test
    @DisplayName("[OA-L06] 결제일 경계 — to 는 그날 23:59:59 까지 포함 · 다음 날 00:00 은 제외 · 요약도 같은 기간")
    void paidDateBoundary() throws Exception {
        Long lastSecond = orderOf(paidGroup());
        Long nextMidnight = orderOf(paidGroup());
        LocalDate day = LocalDate.now().minusDays(10);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", day.atTime(23, 59, 59), lastSecond);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", day.plusDays(1).atStartOfDay(), nextMidnight);

        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?from=" + day + "&to=" + day))).containsExactly(lastSecond);
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?from=" + day.plusDays(1) + "&to=" + day.plusDays(1))))
                .containsExactly(nextMidnight);
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?from=" + day + "&to=" + day.plusDays(1))))
                .containsExactly(nextMidnight, lastSecond);
        adminGet(ADMIN_ORDERS + "/summary?from=" + day + "&to=" + day).andExpect(jsonPath("$.tabCounts.ALL").value(1));
    }

    @Test
    @DisplayName("[OA-L09] 빈 결과 — 없는 브랜드 · 없는 검색어는 빈 페이지(오류 아님) · 요약 0")
    void emptyResults() throws Exception {
        paidGroup();
        JsonNode byMarket = json(adminGet(ADMIN_ORDERS + "?marketId=999999").andExpect(status().isOk()));
        assertThat(byMarket.get("content")).isEmpty();
        assertThat(byMarket.at("/pageInfo/totalResults").asLong()).isZero();
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?keyword=없는주문번호"))).isEmpty();
        adminGet(ADMIN_ORDERS + "/summary?marketId=999999").andExpect(jsonPath("$.tabCounts.ALL").value(0))
                .andExpect(jsonPath("$.tabCounts.DELIVERY_ISSUE").value(0))
                .andExpect(jsonPath("$.tabCounts.CANCEL").value(0));
    }

    @Test
    @DisplayName("[OA-L11] 요약 = 탭별 목록 건수 — 같은 검색 조건에서 세 탭 모두 일치")
    void summaryMatchesListTotals() throws Exception {
        returningGroup("400070011001");
        OrderDeliveryGroup requested = preparingGroup();
        seedCancelRequest(requested, itemsOf(requested));
        shippingGroup("400070011002");
        paidGroup();

        JsonNode tabCounts = json(adminGet(ADMIN_ORDERS + "/summary").andExpect(status().isOk())).get("tabCounts");
        for (String tab : List.of("ALL", "DELIVERY_ISSUE", "CANCEL")) {
            long total = json(adminGet(ADMIN_ORDERS + "?tab=" + tab)).at("/pageInfo/totalResults").asLong();
            assertThat(tabCounts.get(tab).asLong()).as(tab).isEqualTo(total);
        }
        assertThat(tabCounts.get("ALL").asLong()).isEqualTo(4);
        String brand = requested.getMarketName();
        JsonNode filtered = json(adminGet(ADMIN_ORDERS + "/summary?keyword=" + brand)).get("tabCounts");
        assertThat(filtered.get("CANCEL").asLong())
                .isEqualTo(json(adminGet(ADMIN_ORDERS + "?tab=CANCEL&keyword=" + brand)).at("/pageInfo/totalResults").asLong());
    }

    // ------------------------------------------------------------------ 상세 블록

    @Test
    @DisplayName("[OA-D01] B1 배송중 — 결제 블록 · 소비자 · 수취인(마스킹 없음) · 배송 블록 · 항목 · 이력 오래된순 · 레일은 사유 환불만")
    void shippingDetailBlocks() throws Exception {
        OrderDeliveryGroup group = shippingGroup("400070001001");
        Map<String, Object> order = orderRow(group);

        JsonNode detail = detailOf(group);
        assertThat(detail.get("orderNumber").asText()).isEqualTo(order.get("order_number"));
        assertThat(detail.at("/payment/paymentId").asText()).isEqualTo(order.get("paid_payment_id"));
        assertThat(detail.at("/payment/methodLabel").asText()).isEqualTo("신한카드");
        assertThat(detail.at("/payment/amount").asInt()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        assertThat(detail.at("/payment/cancelledAmount").asInt()).isZero();
        assertThat(detail.at("/consumer/userId").asLong()).isEqualTo(consumer.getId());
        assertThat(detail.at("/recipient/name").asText()).isEqualTo(order.get("recipient_name")).doesNotContain("*");
        assertThat(detail.at("/recipient/phone").asText()).doesNotContain("*");
        assertThat(detail.at("/recipient/address").asText()).isNotBlank();

        JsonNode g = detail.get("groups").get(0);
        assertThat(g.get("status").asText()).isEqualTo("SHIPPING");
        assertThat(g.at("/shipping/carrier").asText()).isEqualTo("CJ");
        assertThat(g.at("/shipping/carrierLabel").asText()).isEqualTo("CJ대한통운");
        assertThat(g.at("/shipping/trackingNumber").asText()).isEqualTo("400070001001");
        assertThat(g.at("/shipping/prepareStartedAt").isNull()).isFalse();
        assertThat(g.at("/shipping/shippedAt").isNull()).isFalse();
        assertThat(g.at("/shipping/shipOverdue").asBoolean()).isFalse();
        assertThat(g.get("purchaseConfirm").isNull()).isTrue();
        assertThat(g.get("cancel").isNull()).isTrue();
        assertThat(g.get("cancelRequest").isNull()).isTrue();
        assertThat(g.get("refunds")).isEmpty();
        JsonNode item = g.get("items").get(0);
        assertThat(item.get("quantity").asInt()).isEqualTo(1);
        assertThat(item.get("price").asInt()).isEqualTo(CREAM_PRICE);
        assertThat(item.get("returnedQuantity").asInt()).isZero();
        assertThat(item.get("productName").asText()).isNotBlank();

        List<String> events = new ArrayList<>();
        List<LocalDateTime> times = new ArrayList<>();
        for (JsonNode h : g.get("history")) {
            events.add(h.get("eventType").asText());
            times.add(at(h.get("occurredAt").asText()));
            assertThat(h.get("label").asText()).isNotBlank();
        }
        assertThat(events).contains("PREPARE_STARTED", "INVOICE_REGISTERED");
        assertThat(events.indexOf("PREPARE_STARTED")).isLessThan(events.indexOf("INVOICE_REGISTERED"));
        assertThat(times).isSorted();
        assertActions(g, Map.of("canEnqueueRefund", true));
    }

    @Test
    @DisplayName("[OA-D02] B2 반송 — 감지 중에도 · 완료 뒤에도 레일 조치 없음 · 완료 감지가 PG 자동 환불(반송 완료 · 환불번호) · 결제 누적 취소액")
    void returnCompletedDetail() throws Exception {
        OrderDeliveryGroup group = returningGroup("400070002001");
        JsonNode detecting = detailOf(group).get("groups").get(0);
        assertThat(detecting.get("status").asText()).isEqualTo("RETURNING");
        assertThat(detecting.at("/shipping/returnDetectedAt").isNull()).isFalse();
        assertThat(detecting.at("/shipping/returnCompletedAt").isNull()).isTrue();
        assertActions(detecting, Map.of());
        assertThat(rowOf(group).get("attentionCount").asInt()).isEqualTo(1);

        LocalDateTime now = LocalDateTime.now();
        track(group, new TrackSnapshot(now, null, true, true), now);

        JsonNode detail = detailOf(group);
        JsonNode done = detail.get("groups").get(0);
        assertThat(done.get("status").asText()).isEqualTo("RETURNING");
        assertThat(done.at("/shipping/returnCompletedAt").isNull()).isFalse();
        assertActions(done, Map.of());
        JsonNode refund = done.get("refunds").get(0);
        assertThat(refund.get("source").asText()).isEqualTo("RETURN_COMPLETED");
        assertThat(refund.get("origin").asText()).isEqualTo("PG_AUTO");
        assertThat(refund.get("status").asText()).isEqualTo("DONE");
        assertThat(refund.get("refundNo").asText()).isEqualTo("RFD-" + refund.get("refundTaskId").asLong());
        assertThat(refund.get("executedAt").isNull()).isFalse();
        assertThat(detail.at("/payment/cancelledAmount").asInt()).isEqualTo(refund.get("amount").asInt());
        assertThat(eventTypes(done)).contains("RETURN_COMPLETED", "REFUND_EXECUTED");
        assertThat(rowOf(group).get("attentionCount").asInt()).isZero();
    }

    @Test
    @DisplayName("[OA-D03] B3 배송완료 — 출처 「자동 확인」 · 구매확정 예정 = 배송완료 + 7일 · 남은 일수(올림) · 레일은 정정 · 하자 반품 · 사유 환불")
    void deliveredDetail() throws Exception {
        LocalDateTime deliveredAt = LocalDateTime.now().minusDays(2).minusHours(1).withNano(0);
        OrderDeliveryGroup group = deliveredGroup("400070003001", deliveredAt);

        JsonNode g = detailOf(group).get("groups").get(0);
        assertThat(g.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(at(g.at("/shipping/deliveredAt").asText())).isEqualTo(deliveredAt);
        assertThat(g.at("/shipping/deliveredSourceLabel").asText()).isEqualTo("자동 확인");
        assertThat(g.at("/purchaseConfirm/paused").asBoolean()).isFalse();
        assertThat(at(g.at("/purchaseConfirm/dueAt").asText())).isEqualTo(deliveredAt.plusDays(CONFIRM_DAYS));
        assertThat(g.at("/purchaseConfirm/remainingDays").asLong()).isEqualTo(5);
        assertThat(g.at("/purchaseConfirm/confirmedAt").isNull()).isTrue();
        assertActions(g, Map.of("canCorrectDeliveredAt", true, "canOpenDefectClaim", true, "canEnqueueRefund", true));

        // 배송완료 3개월 + 1일 — 하자 반품 버튼이 사라진다.
        jdbc.update("UPDATE order_delivery_group SET delivered_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusMonths(3).minusDays(1), group.getId());
        assertThat(detailOf(group).at("/groups/0/actions/canOpenDefectClaim").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("[OA-D04] B4 발송 기한 경과 — 기한 · N영업일 · 알림 횟수 · 대행은 상품준비중 ∧ 3회만 · 신규는 직권 취소만")
    void overdueDetail() throws Exception {
        OrderDeliveryGroup preparing = preparingGroup();
        overdue(preparing, 3);
        JsonNode g = detailOf(preparing).get("groups").get(0);
        assertThat(g.at("/shipping/shipDueAt").isNull()).isFalse();
        assertThat(g.at("/shipping/shipDueBusinessDays").asInt()).isPositive();
        assertThat(g.at("/shipping/shipOverdue").asBoolean()).isTrue();
        assertThat(g.at("/shipping/overdueNoticeCount").asInt()).isEqualTo(3);
        assertActions(g, Map.of("canRegisterShipment", true, "canCancel", true));

        overdue(preparing, 2);
        assertActions(detailOf(preparing).get("groups").get(0), Map.of("canCancel", true));

        OrderDeliveryGroup fresh = paidGroup();
        overdue(fresh, 3);
        assertActions(detailOf(fresh).get("groups").get(0), Map.of("canCancel", true));
    }

    @Test
    @DisplayName("[OA-D07] B7 취소 요청 — 요청 블록(사유 · 마감 · 항목) · 송장 · 취소 잠금 · 취소 탭 → 자동 승인 뒤 취소 블록 · PG 환불 · 이력 「자동 승인」")
    void cancelRequestThenAutoApproved() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        overdue(group, 3);
        OrderProduct item = itemsOf(group).get(0);
        Long requestId = json(consumerPost("/v1/user/orders/" + orderOf(group) + "/cancel-requests", Map.of(
                "deliveryGroupId", group.getId(), "orderProductIds", List.of(item.getId()),
                "reasonCode", "ETC", "reasonDetail", "주소를 잘못 적었어요")).andExpect(status().isCreated()))
                .get("cancelRequestId").asLong();

        JsonNode g = detailOf(group).get("groups").get(0);
        assertThat(g.at("/cancelRequest/cancelRequestId").asLong()).isEqualTo(requestId);
        assertThat(g.at("/cancelRequest/reasonLabel").asText()).isNotBlank();
        assertThat(g.at("/cancelRequest/reasonDetail").asText()).isEqualTo("주소를 잘못 적었어요");
        assertThat(g.at("/cancelRequest/orderProductIds/0").asLong()).isEqualTo(item.getId());
        LocalDateTime requestedAt = at(g.at("/cancelRequest/requestedAt").asText());
        assertThat(at(g.at("/cancelRequest/respondDueAt").asText())).isAfter(requestedAt);
        assertActions(g, Map.of());
        assertThat(rowOf(group).get("groups").get(0).get("cancelRequested").asBoolean()).isTrue();
        assertThat(orderIds(adminGet(ADMIN_ORDERS + "?tab=CANCEL"))).contains(orderOf(group));

        OrderCancelRequest request = cancelRequestRepository.findById(requestId).orElseThrow();
        assertThat(cancelRequestService.autoApproveIfOverdue(requestId, request.getRespondDueAt().plusMinutes(1))).isTrue();

        JsonNode approved = detailOf(group).get("groups").get(0);
        assertThat(approved.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(approved.get("cancelRequest").isNull()).isTrue();
        assertThat(approved.at("/cancel/cancelTypeLabel").asText()).isNotBlank();
        assertThat(approved.at("/refunds/0/source").asText()).isEqualTo("CANCEL_REQUEST_APPROVED");
        assertThat(approved.at("/refunds/0/status").asText()).isEqualTo("DONE");
        JsonNode approvedEvent = StreamSupport.stream(approved.get("history").spliterator(), false)
                .filter(h -> h.get("eventType").asText().equals("CANCEL_REQUEST_APPROVED")).findFirst().orElseThrow();
        assertThat(approvedEvent.get("detail").asText()).contains("자동 승인");
        assertActions(approved, Map.of());
    }

    @Test
    @DisplayName("[OA-D11] 추적 정지 종결 버튼 — 배송중 ∧ 추적 정지 ∧ 마지막 추적(없으면 발송) + 28일 · 집화 확인 필요 · 배송완료는 없음")
    void stalledResolveButtons() throws Exception {
        OrderDeliveryGroup pickup = shippingGroup("400070011101");
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'PICKUP_UNCONFIRMED', shipped_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(40), pickup.getId());
        assertStalledButtons(pickup, false);

        OrderDeliveryGroup neverTracked = shippingGroup("400070011102");
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = NULL, shipped_at = ? "
                + "WHERE delivery_group_id = ?", LocalDateTime.now().minusDays(28).minusMinutes(1), neverTracked.getId());
        assertStalledButtons(neverTracked, true);
        jdbc.update("UPDATE order_delivery_group SET shipped_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(27), neverTracked.getId());
        assertStalledButtons(neverTracked, false);

        OrderDeliveryGroup delivered = deliveredGroup("400070011103", LocalDateTime.now().minusDays(30));
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(30), delivered.getId());
        assertStalledButtons(delivered, false);
    }

    // ------------------------------------------------------------------ 45 보완 시나리오 4-1

    @Test
    @DisplayName("[F-A03] 결제 행 없는 주문(paid_payment_id NULL) — 상세 200 · payment null · 배송완료면 사유 환불 편입 가능")
    void detailWithoutPaymentRow() throws Exception {
        OrderDeliveryGroup group = deliveredGroup("400070031001", LocalDateTime.now().minusDays(1));
        jdbc.update("UPDATE orders SET paid_payment_id = NULL WHERE order_id = ?", orderOf(group));

        JsonNode detail = detailOf(group);
        assertThat(detail.get("payment").isNull()).isTrue();
        assertThat(detail.at("/groups/0/status").asText()).isEqualTo("DELIVERED");
        assertThat(detail.at("/groups/0/actions/canEnqueueRefund").asBoolean()).isTrue();
        assertThat(rowOf(group).get("totalAmount").asInt()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
    }

    @Test
    @DisplayName("[F-A06] 두 브랜드가 섞인 주문 — 목록 행 하나에 하위주문 둘 · 브랜드명이 다르다 · 주의 건수는 하위주문 단위(둘 다 기한 경과면 2)")
    void mixedBrandListRow() throws Exception {
        MixedOrder mixed = placeMixedBrandOrder();

        JsonNode row = rowOf(mixed.mine());
        assertThat(row.get("groups")).hasSize(2);
        assertThat(row.get("groups")).extracting(g -> g.get("deliveryGroupId").asLong())
                .containsExactlyInAnyOrder(mixed.mine().getId(), mixed.other().getId());
        assertThat(row.at("/groups/0/brandName").asText()).isNotEqualTo(row.at("/groups/1/brandName").asText());
        assertThat(row.get("attentionCount").asInt()).isZero();
        assertThat(row.get("totalAmount").asInt()).isEqualTo(jdbc.queryForObject(
                "SELECT amount FROM payment WHERE payment_id = ?", Integer.class, mixed.paymentId()));

        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(1), mixed.mine().getId());
        assertThat(rowOf(mixed.mine()).get("attentionCount").asInt()).isEqualTo(1);
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(1), mixed.other().getId());
        assertThat(rowOf(mixed.mine()).get("attentionCount").asInt()).isEqualTo(2);
        assertThat(orderIds(adminGet(ADMIN_ORDERS))).containsExactly(mixed.orderId());
        assertThat(detailOf(mixed.mine()).get("groups")).hasSize(2);
    }

    @Test
    @DisplayName("[F-A09] page=0 · page=-1 은 1쪽으로 보정(400 아님) — 06a · 06b · 06c · 06d 네 목록 공통")
    void nonPositivePageIsFirstPage() throws Exception {
        Long orderId = orderOf(paidGroup());
        for (String page : List.of("0", "-1")) {
            assertThat(orderIds(adminGet(ADMIN_ORDERS + "?page=" + page))).as("06a page=%s", page).containsExactly(orderId);
            for (String list : List.of("/v1/admin/claims", "/v1/admin/refunds", "/v1/admin/order-exceptions")) {
                adminGet(list + "?page=" + page).andExpect(status().isOk());
            }
        }
        adminGet("/v1/admin/order-exceptions?page=0").andExpect(jsonPath("$.page.pageInfo.currentPage").value(1));
        adminGet("/v1/admin/refunds?page=-1").andExpect(jsonPath("$.pageInfo.currentPage").value(1));
        adminGet("/v1/admin/claims?page=0").andExpect(jsonPath("$.pageInfo.currentPage").value(1));
        adminGet(ADMIN_ORDERS + "?page=0").andExpect(jsonPath("$.pageInfo.currentPage").value(1));
    }

    @Test
    @DisplayName("[F-A10] enum 오타 · 날짜 형식 오류는 400 INVALID_INPUT(글로벌 핸들러) · status=PENDING 은 유효값이지만 결제 전이라 0건")
    void invalidQueryParameters() throws Exception {
        paidGroup();
        for (String query : List.of("tab=ALLL", "sort=X", "status=SHIPPED_X", "from=2026-13-01", "searchType=NOPE&keyword=1")) {
            adminGet(ADMIN_ORDERS + "?" + query).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        JsonNode pending = json(adminGet(ADMIN_ORDERS + "?status=PENDING").andExpect(status().isOk()));
        assertThat(pending.get("content")).isEmpty();
        assertThat(pending.at("/pageInfo/totalResults").asLong()).isZero();
    }

    // ------------------------------------------------------------------ 도우미

    private static final List<String> ACTION_KEYS = List.of("canCorrectDeliveredAt", "canRegisterShipment", "canCancel",
            "canEnqueueRefund", "canOpenDefectClaim", "canMarkLost", "canMarkDelivered");

    /** 레일 버튼 7개 — 주어진 것만 참이고 나머지는 전부 거짓이다(숨김). */
    private static void assertActions(JsonNode group, Map<String, Boolean> expectedTrue) {
        JsonNode actions = group.get("actions");
        for (String key : ACTION_KEYS) {
            assertThat(actions.get(key).asBoolean()).as(key).isEqualTo(expectedTrue.getOrDefault(key, false));
        }
    }

    private void assertStalledButtons(OrderDeliveryGroup group, boolean expected) throws Exception {
        JsonNode actions = detailOf(group).at("/groups/0/actions");
        assertThat(actions.get("canMarkLost").asBoolean()).isEqualTo(expected);
        assertThat(actions.get("canMarkDelivered").asBoolean()).isEqualTo(expected);
    }

    private void overdue(OrderDeliveryGroup group, int notices) {
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ?, overdue_notice_count = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(1), notices, group.getId());
    }

    private JsonNode detailOf(OrderDeliveryGroup group) throws Exception {
        return json(adminGet(ADMIN_ORDERS + "/" + orderOf(group)).andExpect(status().isOk()));
    }

    private JsonNode rowOf(OrderDeliveryGroup group) throws Exception {
        Long orderId = orderOf(group);
        JsonNode rows = json(adminGet(ADMIN_ORDERS + "?size=100").andExpect(status().isOk())).get("content");
        return StreamSupport.stream(rows.spliterator(), false).filter(row -> row.get("orderId").asLong() == orderId)
                .findFirst().orElseThrow();
    }

    private static List<String> eventTypes(JsonNode group) {
        return StreamSupport.stream(group.get("history").spliterator(), false).map(h -> h.get("eventType").asText()).toList();
    }

    private Map<String, Object> orderRow(OrderDeliveryGroup group) {
        return jdbc.queryForMap("SELECT * FROM orders WHERE order_id = ?", orderOf(group));
    }

    private Map<String, Object> groupRow(OrderDeliveryGroup group) {
        return jdbc.queryForMap("SELECT * FROM order_delivery_group WHERE delivery_group_id = ?", group.getId());
    }

    private Long orderOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?", Long.class,
                group.getId());
    }

    private List<Long> orderIds(ResultActions actions) throws Exception {
        return ids(json(actions.andExpect(status().isOk())));
    }

    private static List<Long> ids(JsonNode page) {
        return StreamSupport.stream(page.get("content").spliterator(), false).map(row -> row.get("orderId").asLong()).toList();
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
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

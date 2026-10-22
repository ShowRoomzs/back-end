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
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 46 수정계획 — 어드민 거래 관리 FE 연동 요청 중 06a 주문 조회와 공구 셀렉트. 운영자 사유 환불 버튼의 잔액 조건(1절) · 공구 목록
 * {@code marketId} · 인플루언서 선택 목록(2절) · 행 확장 · 배송 이상 경과 · 셀렉트 건수 · 상세 필드(4절)를 본다.
 */
@IntegrationTest
class AdminOrderFeRequestIntegrationTest extends OrderFlowTestSupport {

    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final String ADMIN_GROUP_BUYS = "/v1/admin/group-buys";
    private static final String GROUP_BUY_TITLE = "글로우 크림 앵콜 공구";

    @Autowired private OneToOneInquiryRepository inquiryRepository;

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("fe-request@showroomz.test", "운영자"));
    }

    // ------------------------------------------------------------------ 1절 운영자 사유 환불 버튼

    @Test
    @DisplayName("[46-1] 사유 환불 버튼 = 상태 ∧ 남은 잔액 — 대기 환불이 잔액을 덮거나 전액 환불됐으면 숨김 · 상한은 operatorRefundableAmount · 넘기면 409")
    void refundButtonFollowsRemainingBalance() throws Exception {
        int total = CREAM_PRICE + DELIVERY_FEE;
        OrderDeliveryGroup group = deliveredGroup("400460001001", LocalDateTime.now().minusDays(1));
        JsonNode before = detailOf(group);
        assertThat(before.at("/payment/operatorRefundableAmount").asInt()).isEqualTo(total);
        assertThat(before.at("/groups/0/actions/canEnqueueRefund").asBoolean()).isTrue();

        // 일부 편입 — 잔액이 남아 버튼은 그대로 · 상한이 줄어든다.
        JsonNode partial = json(enqueue(group, 10_000).andExpect(status().isOk()));
        assertThat(partial.at("/payment/operatorRefundableAmount").asInt()).isEqualTo(total - 10_000);
        assertThat(partial.at("/groups/0/actions/canEnqueueRefund").asBoolean()).isTrue();
        enqueue(group, total - 10_000 + 1).andExpect(status().isConflict());

        // 남은 잔액 전부 편입(아직 집행 전) — 상한 0 · 버튼 숨김. 서버 409 와 같은 식이다.
        JsonNode covered = json(enqueue(group, total - 10_000).andExpect(status().isOk()));
        assertThat(covered.at("/payment/operatorRefundableAmount").asInt()).isZero();
        assertThat(covered.at("/groups/0/actions/canEnqueueRefund").asBoolean()).isFalse();
        enqueue(group, 1).andExpect(status().isConflict());

        // 이미 전액 환불된 결제(cancelled_amount = amount · 20261017-000001 사례).
        OrderDeliveryGroup refunded = deliveredGroup("400460001002", LocalDateTime.now().minusDays(1));
        jdbc.update("UPDATE payment SET cancelled_amount = amount WHERE payment_id = "
                + "(SELECT paid_payment_id FROM orders WHERE order_id = ?)", orderOf(refunded));
        JsonNode done = detailOf(refunded);
        assertThat(done.at("/payment/operatorRefundableAmount").asInt()).isZero();
        assertThat(done.at("/groups/0/actions/canEnqueueRefund").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------ 2절 공구 · 인플루언서 셀렉트

    @Test
    @DisplayName("[46-2] 공구 목록 marketId — 그 브랜드 공구만 · 없는 브랜드는 빈 목록 / 인플루언서 목록 — 공구를 연 인플루언서 · 쇼룸명 · 브랜드 필터")
    void groupBuyAndCreatorSelects() throws Exception {
        JsonNode mine = json(adminGet(ADMIN_GROUP_BUYS + "?marketId=" + brand.marketId() + "&size=100")
                .andExpect(status().isOk()));
        assertThat(mine.get("content")).extracting(row -> row.get("groupBuyId").asLong()).contains(groupBuy.getId());
        assertThat(mine.get("content")).allSatisfy(row ->
                assertThat(row.get("brandName").asText()).isEqualTo(brand.market().getMarketName()));
        assertThat(json(adminGet(ADMIN_GROUP_BUYS + "?marketId=999999")).get("content")).isEmpty();

        JsonNode creators = json(adminGet(ADMIN_GROUP_BUYS + "/creators").andExpect(status().isOk()));
        JsonNode option = StreamSupport.stream(creators.spliterator(), false)
                .filter(row -> row.get("creatorId").asLong() == creator.getId()).findFirst().orElseThrow();
        assertThat(option.get("showroomName").asText()).isEqualTo("글로우_지민");
        assertThat(json(adminGet(ADMIN_GROUP_BUYS + "/creators?marketId=" + brand.marketId())))
                .extracting(row -> row.get("creatorId").asLong()).contains(creator.getId());
        assertThat(json(adminGet(ADMIN_GROUP_BUYS + "/creators?marketId=999999"))).isEmpty();

        // 고른 인플루언서로 주문을 거르면 그 공구의 주문이 나온다.
        OrderDeliveryGroup group = paidGroup();
        assertThat(ids(json(adminGet(ADMIN_ORDERS + "?creatorId=" + creator.getId())))).contains(orderOf(group));
    }

    // ------------------------------------------------------------------ 4절 목록 · 요약

    @Test
    @DisplayName("[46-4-1] 행 확장 — 항목 요약(1종은 옵션까지 · 여러 종은 「외 N종」) · 수량 합 · 금액 합 = 결제액 · 송장 · 공구명")
    void listRowExpansion() throws Exception {
        OrderDeliveryGroup two = paidGroupWithTwoItems();
        JsonNode row = rowOf(two);
        JsonNode summary = row.get("groups").get(0);
        OrderProduct first = itemsOf(two).stream().min(java.util.Comparator.comparing(OrderProduct::getId)).orElseThrow();
        assertThat(summary.get("itemSummary").asText()).isEqualTo(first.getProductName() + " 외 1종");
        assertThat(summary.get("itemQuantity").asInt()).isEqualTo(2);
        assertThat(summary.get("amount").asInt()).isEqualTo(row.get("totalAmount").asInt());
        assertThat(summary.get("groupBuyTitle").asText()).isEqualTo(GROUP_BUY_TITLE);
        assertThat(summary.get("trackingNumber").isNull()).isTrue();

        OrderDeliveryGroup shipping = shippingGroup("400460002001");
        JsonNode single = rowOf(shipping).get("groups").get(0);
        OrderProduct item = itemsOf(shipping).get(0);
        String expected = item.getOptionName() == null || item.getOptionName().isBlank() ? item.getProductName()
                : item.getProductName() + " " + item.getOptionName();
        assertThat(single.get("itemSummary").asText()).isEqualTo(expected);
        assertThat(single.get("itemQuantity").asInt()).isEqualTo(1);
        assertThat(single.get("amount").asInt()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
        assertThat(single.get("carrier").asText()).isEqualTo("CJ");
        assertThat(single.get("carrierLabel").asText()).isEqualTo("CJ대한통운");
        assertThat(single.get("trackingNumber").asText()).isEqualTo("400460002001");
        assertThat(single.get("issueElapsedLabel").isNull()).isTrue();
    }

    @Test
    @DisplayName("[46-4-2] 배송 이상 경과 — 집화 확인 필요 「등록 후 N시간」 · 추적 정지 「N일 무갱신」 · 반송 중 「감지 MM.dd」 · 기준 시각")
    void deliveryIssueElapsed() throws Exception {
        OrderDeliveryGroup pickup = shippingGroup("400460003001");
        LocalDateTime registered = LocalDateTime.now().minusHours(26).minusMinutes(5).withNano(0);
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'PICKUP_UNCONFIRMED', shipped_at = ? "
                + "WHERE delivery_group_id = ?", registered, pickup.getId());
        JsonNode p = rowOf(pickup).get("groups").get(0);
        assertThat(p.get("issueElapsedLabel").asText()).isEqualTo("등록 후 26시간");
        assertThat(at(p.get("issueSince").asText())).isEqualTo(registered);

        OrderDeliveryGroup stalled = shippingGroup("400460003002");
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(8).minusHours(1), stalled.getId());
        assertThat(rowOf(stalled).at("/groups/0/issueElapsedLabel").asText()).isEqualTo("8일 무갱신");

        OrderDeliveryGroup returning = returningGroup("400460003003");
        LocalDateTime detected = reloadGroup(returning).getReturnDetectedAt();
        assertThat(rowOf(returning).at("/groups/0/issueElapsedLabel").asText())
                .isEqualTo("감지 " + detected.format(DateTimeFormatter.ofPattern("MM.dd")));
    }

    @Test
    @DisplayName("[46-4-3] 셀렉트 건수 — 상태별 · 이상 유형별 숫자가 그 값을 고른 목록 건수와 같다 · 키는 항상 있다")
    void selectCountsMatchListTotals() throws Exception {
        paidGroup();
        preparingGroup();
        OrderDeliveryGroup stalled = shippingGroup("400460004001");
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(8), stalled.getId());
        returningGroup("400460004002");
        deliveredGroup("400460004003", LocalDateTime.now().minusDays(1));

        JsonNode summary = json(adminGet(ADMIN_ORDERS + "/summary").andExpect(status().isOk()));
        JsonNode statusCounts = summary.get("statusCounts");
        assertThat(statusCounts.has("PENDING")).isFalse();
        for (String key : List.of("NEW", "PREPARING", "SHIPPING", "RETURNING", "DELIVERED", "CONFIRMED", "CANCELLED")) {
            long listed = json(adminGet(ADMIN_ORDERS + "?status=" + key)).at("/pageInfo/totalResults").asLong();
            assertThat(statusCounts.get(key).asLong()).as(key).isEqualTo(listed);
        }
        assertThat(statusCounts.get("SHIPPING").asLong()).isEqualTo(1);

        JsonNode issues = summary.get("deliveryIssueCounts");
        for (String alert : List.of("PICKUP_UNCONFIRMED", "STALLED")) {
            long listed = json(adminGet(ADMIN_ORDERS + "?tab=DELIVERY_ISSUE&trackingAlert=" + alert))
                    .at("/pageInfo/totalResults").asLong();
            assertThat(issues.get(alert).asLong()).as(alert).isEqualTo(listed);
        }
        assertThat(issues.get("STALLED").asLong()).isEqualTo(1);
        assertThat(issues.get("RETURNING").asLong()).isEqualTo(1).isEqualTo(
                json(adminGet(ADMIN_ORDERS + "?tab=DELIVERY_ISSUE&status=RETURNING")).at("/pageInfo/totalResults").asLong());

        // 같은 조건(브랜드 · 검색)을 따른다.
        JsonNode none = json(adminGet(ADMIN_ORDERS + "/summary?marketId=999999"));
        assertThat(none.at("/statusCounts/SHIPPING").asLong()).isZero();
        assertThat(none.at("/deliveryIssueCounts/RETURNING").asLong()).isZero();
    }

    // ------------------------------------------------------------------ 4절 상세

    @Test
    @DisplayName("[46-4-4 · 4-8] 상세 — 공구명 · 인플루언서 · 송장 출처 「브랜드 등록」 · 마지막 스캔 문구(추적 이벤트) · 스캔 전이면 null")
    void detailGroupBuyInvoiceAndTracking() throws Exception {
        OrderDeliveryGroup group = shippingGroup("400460005001");
        JsonNode g = detailOf(group).get("groups").get(0);
        assertThat(g.get("groupBuyId").asLong()).isEqualTo(groupBuy.getId());
        assertThat(g.get("groupBuyTitle").asText()).isEqualTo(GROUP_BUY_TITLE);
        assertThat(g.get("creatorId").asLong()).isEqualTo(creator.getId());
        assertThat(g.get("creatorName").asText()).isEqualTo("글로우_지민");
        assertThat(g.at("/shipping/invoiceSource").asText()).isEqualTo("SELLER");
        assertThat(g.at("/shipping/invoiceSourceLabel").asText()).isEqualTo("브랜드 등록");
        assertThat(g.at("/shipping/lastTrackingLabel").isNull()).isTrue();

        LocalDateTime now = LocalDateTime.now().withNano(0);
        track(group, new TrackSnapshot(now, null, false, false, List.of(
                new TrackEvent(now.minusHours(3), "옥천HUB", "간선하차", 3),
                new TrackEvent(now.minusHours(1), "옥천HUB", "간선상차", 3)), 3), now);
        assertThat(detailOf(group).at("/groups/0/shipping/lastTrackingLabel").asText()).isEqualTo("간선상차");

        OrderDeliveryGroup unshipped = paidGroup();
        JsonNode u = detailOf(unshipped).at("/groups/0/shipping");
        assertThat(u.get("invoiceSource").isNull()).isTrue();
        assertThat(u.get("invoiceSourceLabel").isNull()).isTrue();
    }

    @Test
    @DisplayName("[46-4-4 · 4-6] 운영자 대행 송장 — 송장 출처 「운영자 대행」 · B4 마지막 자동 알림 시각")
    void adminShipmentSourceAndLastNotice() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        LocalDateTime lastNotice = LocalDateTime.now().minusHours(2).withNano(0);
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ?, overdue_notice_count = 3, last_overdue_notice_at = ? "
                + "WHERE delivery_group_id = ?", LocalDateTime.now().minusDays(1), lastNotice, group.getId());
        assertThat(at(detailOf(group).at("/groups/0/shipping/lastOverdueNoticeAt").asText())).isEqualTo(lastNotice);

        JsonNode shipped = json(adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/shipment",
                Map.of("carrier", "CJ", "trackingNumber", "400460006001", "note", "자동 알림 3회 무응답"))
                .andExpect(status().isOk()));
        assertThat(shipped.at("/groups/0/shipping/invoiceSource").asText()).isEqualTo("ADMIN");
        assertThat(shipped.at("/groups/0/shipping/invoiceSourceLabel").asText()).isEqualTo("운영자 대행");
    }

    @Test
    @DisplayName("[46-4-5] 연결 문의 — 이 주문을 가리키는 1:1 문의 최신순 · inquiryCount 와 같은 수 · 다른 주문 문의는 없다 · 추가 결제 없으면 빈 목록")
    void detailInquiries() throws Exception {
        OrderDeliveryGroup group = deliveredGroup("400460007001", LocalDateTime.now().minusDays(1));
        Long orderId = orderOf(group);
        OneToOneInquiry older = inquiryRepository.save(OneToOneInquiry.builder().user(consumer).type(CsCategory.DELIVERY)
                .content("실제로는 다음 날 받았어요").orderId(orderId).build());
        OneToOneInquiry newer = inquiryRepository.save(OneToOneInquiry.builder().user(consumer)
                .type(CsCategory.CANCEL_EXCHANGE_RETURN).content("반품 문의").orderId(orderId).build());
        inquiryRepository.save(OneToOneInquiry.builder().user(consumer).type(CsCategory.DELIVERY)
                .content("다른 주문").orderId(orderOf(paidGroup())).build());

        JsonNode detail = detailOf(group);
        assertThat(detail.get("inquiryCount").asLong()).isEqualTo(2);
        assertThat(detail.get("inquiries")).extracting(i -> i.get("inquiryId").asLong())
                .containsExactly(newer.getId(), older.getId());
        assertThat(detail.at("/inquiries/1/typeLabel").asText()).isEqualTo("배송");
        assertThat(detail.at("/inquiries/1/answered").asBoolean()).isFalse();
        assertThat(detail.get("additionalPayments")).isEmpty();
    }

    // ------------------------------------------------------------------ 도우미

    private ResultActions enqueue(OrderDeliveryGroup group, int amount) throws Exception {
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "POST_CONFIRM_DEFECT", "amount", amount, "detail", "배송 후 하자 확인"));
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

    private Long orderOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?", Long.class,
                group.getId());
    }

    private static List<Long> ids(JsonNode page) {
        return StreamSupport.stream(page.get("content").spliterator(), false).map(row -> row.get("orderId").asLong()).toList();
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

    /** 응답 시각 — 직렬화가 {@code ...Z} 를 붙여 내리므로 떼고 읽는다. */
    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text.endsWith("Z") ? text.substring(0, text.length() - 1) : text);
    }
}

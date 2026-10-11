package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.event.ClaimHistoryRecordedEvent;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.ClaimEventType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오 4-1절 — 반품 · 교환(E2E-F · E2E-G). 배송완료된 주문에서 앱의 [반품 요청] [교환 요청]으로 들어가 종결까지 간다.
 *
 * <p>사람이 없는 구간은 다른 시나리오와 같은 방식이다 — 택배 추적은 5-2대로 반영 서비스에 포트 결과를 넣고, 받는 API 가
 * 아직 없는 환불 집행(어드민)은 도메인 진입점을 직접 부른다. 상태 컬럼은 SQL 로 바꾸지 않는다.
 * 반려 → 재발송비 결제 → 반송, 거절 보류의 고지 · 폐기는 {@code SellerClaimReshipIntegrationTest}가 본다.
 */
@IntegrationTest
@RecordApplicationEvents
@DisplayName("[시나리오 E2E-F·G] 반품 · 교환")
class OrderClaimScenarioIntegrationTest extends OrderFlowTestSupport {

    private static final String USER_CLAIMS = "/v1/user/claims";
    private static final String SELLER_CLAIMS = "/v1/seller/claims";

    @Autowired private OrderClaimService claimService;
    @Autowired private ApplicationEvents applicationEvents;

    @Test
    @DisplayName("[F-01~F-06] 반품 한 바퀴 — 앱 요청 → 회수 도착 → 입고 확인 → 검수 통과 → 환불 큐 → 환불 집행. 진행 중에는 구매확정이 서고, 요약 칸이 클레임 건수를 따라간다")
    void returnFlow() throws Exception {
        // 구매확정 기한(7일)이 이미 지난 배송완료 — 반품이 진행 중인 동안은 확정되지 않아야 한다.
        OrderDeliveryGroup group = deliveredGroup("100020003101", LocalDateTime.now().minusDays(8).withNano(0));
        Long orderId = group.getOrder().getId();
        Long orderProductId = itemsOf(group).get(0).getId();

        // F-01 배송완료 — 목록은 [반품 · 교환] 하나, 상세는 [반품 요청] [교환 요청] 둘.
        userGet(ORDERS).andExpect(jsonPath("$.content[0].items[0].actions[*].type",
                contains("TRACK_DELIVERY", "RETURN_EXCHANGE")));
        appOrder(orderId).andExpect(jsonPath("$.items[0].actions[*].type",
                contains("TRACK_DELIVERY", "RETURN_REQUEST", "EXCHANGE_REQUEST")));
        userGet(USER_CLAIMS + "/form?orderProductId=" + orderProductId + "&type=RETURN").andExpect(status().isOk())
                // 배송비를 내고 받은 주문 — 환불에서 더 빼는 것은 없다(낸 배송비는 돌려받지 않는다).
                .andExpect(jsonPath("$.fees.consumerFault").value(0));

        // F-02 요청 — 송장을 같이 내 회수 중으로 시작한다.
        String invoice = "200030004101";
        Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", orderProductId, null);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", invoice));
        Long claimId = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COLLECTING"))).get("claimIds").get(0).asLong();

        appOrder(orderId).andExpect(jsonPath("$.items[0].claim.claimId").value(claimId))
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("CLAIM_DETAIL")))
                .andExpect(jsonPath("$.items[0].actions[0].label").value("반품 상세"));
        sellerGet(SELLER_CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.COLLECTING").value(1));
        sellerSummary().andExpect(jsonPath("$.actionBar.incomingCheck").value(0));
        sellerOrder(group).andExpect(jsonPath("$.overlays.openClaimCount").value(1));
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now())).isFalse();

        // F-03 회수 도착(추적) — 「입고 확인」 칸에 잡힌다.
        LocalDateTime arrivedAt = LocalDateTime.now().minusHours(1).withNano(0);
        claimService.applyCollectionTracking(collectionIdOf(claimId), DeliveryCarrier.CJ, invoice,
                new TrackSnapshot(arrivedAt, arrivedAt, false, false,
                        List.of(new TrackEvent(arrivedAt, "브랜드", "배송완료", 6)), 6), LocalDateTime.now());
        assertThat(claimStatus(claimId)).isEqualTo("ARRIVED");
        sellerSummary().andExpect(jsonPath("$.actionBar.incomingCheck").value(1))
                .andExpect(jsonPath("$.actionBar.reshipExchange").value(0));

        // F-04 입고 확인 → 검수 통과 — 환불 큐에 요청 1행 → 커밋 직후 PG 즉시 자동 환불(1009 기획 수정본 2절).
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerSummary().andExpect(jsonPath("$.actionBar.incomingCheck").value(1));
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("COMPLETED"));
        sellerSummary().andExpect(jsonPath("$.actionBar.incomingCheck").value(0));
        // F-05 · F-06 환불 완료 — 운영자 집행 단계가 없다.
        assertThat(refundTasks(group)).containsExactly(new RefundTask("CLAIM_RETURN_PASSED", CREAM_PRICE, "DONE"));
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.refund.amount").value(CREAM_PRICE));

        assertThat(claimStatus(claimId)).isEqualTo("COMPLETED");
        assertThat(itemsOf(group).get(0).getStatus()).isEqualTo(OrderProductStatus.RETURNED);
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.items[0].phase").value("DONE"))
                .andExpect(jsonPath("$.refund.confirmed").value(true));
        appOrder(orderId).andExpect(jsonPath("$.items[0].actions[*].type", contains("CLAIM_DETAIL")));
        sellerOrder(group).andExpect(jsonPath("$.overlays.openClaimCount").value(0));

        // 알림 모듈이 받을 사건이 발행됐다.
        assertThat(applicationEvents.stream(ClaimHistoryRecordedEvent.class)
                .filter(event -> event.claimId().equals(claimId)).map(ClaimHistoryRecordedEvent::eventType))
                .contains(ClaimEventType.REQUESTED, ClaimEventType.ARRIVED, ClaimEventType.RECEIVED,
                        ClaimEventType.INSPECTION_PASSED, ClaimEventType.REFUND_EXECUTED);
    }

    @Test
    @DisplayName("[G-01~G-06] 교환 한 바퀴 — 재발송 배송비 선결제 → 접수 → 검수 통과 → 재발송 송장 → 도착. 구매확정 7일은 새 상품이 도착한 날부터 다시 센다")
    void exchangeFlow() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant);
        OrderDeliveryGroup group = deliveredGroup("100020003102", LocalDateTime.now().minusDays(8).withNano(0));
        Long orderId = group.getOrder().getId();
        Long orderProductId = itemsOf(group).get(0).getId();
        int stock = stockOf(refill);

        // G-01 요청 — 고객 귀책 교환은 재발송 배송비 결제가 끝나야 접수된다.
        Map<String, Object> body = claimBody(group, "EXCHANGE", "CHANGE_OF_MIND", orderProductId,
                refill.getVariantId());
        body.put("expectedFee", DELIVERY_FEE);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", "200030004102"));
        body.put("payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN"));
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.payment.totalAmount").value(DELIVERY_FEE)));
        Long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();
        // 재고는 요청하는 순간 잡히고, 결제 전에는 브랜드 화면에 없다.
        assertThat(stockOf(refill)).isEqualTo(stock - 1);
        sellerGet(SELLER_CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.ALL").value(0));

        // G-02 결제 확정 → 접수.
        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        userPost(USER_CLAIMS + "/payments/" + paymentId + "/complete", Map.of())
                .andExpect(jsonPath("$.claimStatus").value("COLLECTING"));
        appOrder(orderId).andExpect(jsonPath("$.items[0].actions[*].type", contains("CLAIM_DETAIL")))
                .andExpect(jsonPath("$.items[0].actions[0].label").value("교환 상세"));

        // G-03 입고 확인(추적이 꺼진 동안은 회수 중에서도 받는다) → 검수 통과 — 「재발송·교환」 칸에 잡힌다.
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("RESHIP_READY"));
        sellerSummary().andExpect(jsonPath("$.actionBar.reshipExchange").value(1));
        // 교환은 환불이 없다.
        assertThat(refundTasks(group)).isEmpty();

        // G-04 재발송 송장 — 등록해도 완료가 아니다.
        String reshipInvoice = "200030004202";
        sellerPost(SELLER_CLAIMS + "/reshipments", Map.of("items", List.of(
                Map.of("claimId", claimId, "carrier", "CJ", "trackingNumber", reshipInvoice))))
                .andExpect(jsonPath("$.succeeded").value(1));
        assertThat(claimStatus(claimId)).isEqualTo("RESHIPPING");
        sellerSummary().andExpect(jsonPath("$.actionBar.reshipExchange").value(0));
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now())).isFalse();

        // G-05 새 상품 도착(추적) — 교환 완료.
        LocalDateTime deliveredAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        claimService.applyReshipTracking(claimId, DeliveryCarrier.CJ, reshipInvoice,
                new TrackSnapshot(deliveredAt, deliveredAt, false, false,
                        List.of(new TrackEvent(deliveredAt, "강남", "배송완료", 6)), 6), LocalDateTime.now());
        assertThat(claimStatus(claimId)).isEqualTo("COMPLETED");
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.items[0].statusLabel").value("교환 완료"));
        // 새 옵션의 재고는 돌아오지 않는다 — 실제로 나갔다.
        assertThat(stockOf(refill)).isEqualTo(stock - 1);

        // G-06 구매확정 — 원래 배송완료일(8일 전)이 아니라 새 상품 도착일부터 7일.
        assertThat(reloadGroup(group).getConfirmRestartAt()).isEqualTo(deliveredAt);
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now())).isFalse();
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now().plusDays(8))).isTrue();
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        // 구매확정되면 반품·교환 창이 닫힌다.
        appOrder(orderId).andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_DELIVERY")));
    }

    // ------------------------------------------------------------------ 픽스처

    private Map<String, Object> claimBody(OrderDeliveryGroup group, String type, String reason, Long orderProductId,
                                          Long exchangeVariantId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderProductId", orderProductId);
        if (exchangeVariantId != null) {
            item.put("exchangeVariantId", exchangeVariantId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", type);
        body.put("deliveryGroupId", group.getId());
        body.put("items", List.of(item));
        body.put("reasonCode", reason);
        return body;
    }

    /** 같은 상품 · 같은 가격의 옵션 「리필」(재고 5) — 교환할 수 있는 옵션은 판매가가 같아야 한다. */
    private ProductVariant addSamePriceVariant(ProductVariant base) {
        Long productId = jdbc.queryForObject("SELECT product_id FROM product_variant WHERE variant_id = ?",
                Long.class, base.getVariantId());
        Product product = productVariantRepository.findByProductIdsOrderByVariantId(List.of(productId)).get(0)
                .getProduct();
        ProductVariant added = productVariantRepository.save(new ProductVariant(product, "리필",
                base.getRegularPrice(), base.getSalePrice(), 5, false));
        jdbc.update("INSERT INTO contract_item_option (contract_item_id, variant_id, variant_name, regular_price, "
                + "min_quantity, sort_order) SELECT contract_item_id, ?, '리필', regular_price, 0, 1 "
                + "FROM contract_item_option WHERE variant_id = ?", added.getVariantId(), base.getVariantId());
        return added;
    }

    private Long collectionIdOf(Long claimId) {
        return jdbc.queryForObject("SELECT collection_id FROM order_claim WHERE claim_id = ?", Long.class, claimId);
    }

    private String claimStatus(Long claimId) {
        return jdbc.queryForObject("SELECT status FROM order_claim WHERE claim_id = ?", String.class, claimId);
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

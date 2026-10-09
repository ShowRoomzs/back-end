package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultMatcher;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 주문 관리의 구간 이음새(시나리오 문서 E2E-0 · X-01 · X-02) — 결제 → 하위주문 → 준비 → 발송 → 배송완료 → 구매확정 →
 * 정산 게이트가 한 번에 흐르는지, 그리고 하위주문이 브랜드 경계를 넘지 않는지.
 */
@IntegrationTest
class SellerOrderScenarioIntegrationTest extends SellerOrderTestSupport {

    @Autowired private GroupBuySalesReader salesReader;

    @Test
    @DisplayName("한 거래의 일생 — 결제 · 발주서+준비 시작 · 송장 · 배송완료(자동) · 구매확정(D+7) · 정산 게이트 미종결 0(E2E-0)")
    void lifeOfOneOrder() throws Exception {
        // 0-9 결제 확정 → 하위주문 활성화
        OrderDeliveryGroup group = paidGroup();
        assertThat(group.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(group.getShipDueAt()).isNull(); // 공구 진행 중 — 마감 뒤에 확정된다
        assertThat(group.getShipDueBusinessDays()).isEqualTo(SHIPPING_LEAD_DAYS);
        assertThat(salesReader.readClosure(groupBuy.getId()).orElseThrow().unclosedCount()).isEqualTo(1);

        // 0-10 신규 탭
        sellerGet(SELLER_ORDERS + "?tab=NEW").andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()));
        sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.actionBar.prepareStart").value(1));

        // 0-11 발주서(기본 8컬럼) + 준비 시작 — 소비자 앱 취소는 닫힌다
        List<String> columns = objectMapper.convertValue(objectMapper.readTree(
                sellerGet(SELLER_ORDERS + "/purchase-order/template").andReturn().getResponse().getContentAsString())
                .get("columns"), List.class);
        byte[] file = sellerPost(SELLER_ORDERS + "/purchase-order",
                Map.of("deliveryGroupIds", List.of(group.getId()), "columns", columns))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(readSheet(file).get(1)).contains(orderNumberOf(group), "김수민", "010-1234-5678");
        assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        cancel(group.getOrder().getId()).andExpect(status().isConflict());

        // 0-12 송장 등록
        shipped(group, "CJ", "1234-5678-9012");
        sellerGet(SELLER_ORDERS + "?tab=SHIPPING")
                .andExpect(jsonPath("$.content[0].trackingNumber").value("123456789012"))
                .andExpect(jsonPath("$.content[0].carrierLabel").value("CJ대한통운"));

        // 0-13 배송완료(추적 자동)
        delivered(group, LocalDateTime.now().withNano(0));
        sellerGet(SELLER_ORDERS + "?tab=DELIVERED")
                .andExpect(jsonPath("$.content[0].confirmRemainingDays").value(7));

        // 0-14 구매확정 — 기한 소급 후 배치
        jdbc.update("UPDATE order_delivery_group SET delivered_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(8), group.getId());
        LocalDateTime now = batchNow();
        List<Long> due = fulfillmentService.findIdsToConfirm(now.minusDays(7), 500);
        assertThat(due).containsExactly(group.getId());
        due.forEach(id -> fulfillmentService.confirmPurchase(id, now, now.minusDays(7)));

        sellerGet(SELLER_ORDERS + "?tab=CONFIRMED")
                .andExpect(jsonPath("$.content[0].paidAmount").value(CREAM_PRICE + DELIVERY_FEE))
                .andExpect(jsonPath("$.content[0].settlementLabel").value(nullValue()));
        orderDetail(group.getId())
                .andExpect(jsonPath("$.history[*].eventType", contains(
                        "PURCHASE_CONFIRMED", "DELIVERED", "INVOICE_REGISTERED", "PREPARE_STARTED", "PAID")));
        // 0-17 정산 게이트 — 구매확정이 미종결을 닫았다
        assertThat(salesReader.readClosure(groupBuy.getId()).orElseThrow().unclosedCount()).isZero();
        sellerGet(SELLER_ORDERS + "/summary")
                .andExpect(jsonPath("$.tabCounts.CONFIRMED").value(1))
                .andExpect(jsonPath("$.tabCounts.ALL").value(1));
    }

    @Nested
    @DisplayName("브랜드 격리")
    class Isolation {

        @Test
        @DisplayName("다른 브랜드는 내 하위주문을 보지도 만지지도 못한다 — 목록·요약 0 · 상세/수정/승인 404 · 다건 액션은 제외(X-01)")
        void otherBrandCannotSeeOrTouch() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup preparing = preparingGroup();
            OrderDeliveryGroup shipping = shippingGroup("600070008000");
            OrderDeliveryGroup requestedGroup = preparingGroup();
            OrderCancelRequest request = seedCancelRequest(requestedGroup);
            String other = sellerToken(otherBrand().seller());

            sellerGet(other, SELLER_ORDERS + "?tab=ALL").andExpect(jsonPath("$.content", empty()));
            sellerGet(other, SELLER_ORDERS + "/summary")
                    .andExpect(jsonPath("$.tabCounts.ALL").value(0))
                    .andExpect(jsonPath("$.tabCounts.CANCEL_REQUESTED").value(0))
                    .andExpect(jsonPath("$.actionBar.prepareStart").value(0));
            sellerGet(other, SELLER_ORDERS + "/" + fresh.getId())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
            sellerPatch(other, SELLER_ORDERS + "/" + shipping.getId() + "/shipment",
                    Map.of("carrier", "HANJIN", "trackingNumber", "600070008999"))
                    .andExpect(status().isNotFound());
            sellerPost(other, SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/approve", Map.of())
                    .andExpect(status().isNotFound());
            sellerPost(other, SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/reject", Map.of("reason", "거부"))
                    .andExpect(status().isNotFound());

            sellerPost(other, SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(fresh.getId())))
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(fresh.getId(), "ORDER_STATE_CHANGED"));
            sellerPost(other, SELLER_ORDERS + "/shipments", Map.of("rows", List.of(
                    shipmentRow(preparing.getId(), "CJ", "600070008001"))))
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(preparing.getId(), "ORDER_STATE_CHANGED"));
            sellerPost(other, SELLER_ORDERS + "/cancel", Map.of("deliveryGroupIds", List.of(fresh.getId()),
                    "reasonCode", "SOLD_OUT", "consumerMessage", "품절"))
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(fresh.getId(), "ORDER_GROUP_NOT_FOUND"));
            sellerPost(other, SELLER_ORDERS + "/purchase-order", Map.of("deliveryGroupIds", List.of(fresh.getId()),
                    "columns", List.of("RECIPIENT", "PHONE", "ADDRESS")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
            uploadShipments(other, shipmentXlsx(List.<String[]>of(
                    new String[]{orderNumberOf(preparing), "CJ대한통운", "600070008002"})))
                    .andExpect(jsonPath("$.rows[0].errorCode").value("ORDER_NOT_FOUND"));

            // 내 쪽은 아무것도 바뀌지 않았다.
            assertThat(reload(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(reload(preparing).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reload(shipping).getTrackingNumber()).isEqualTo("600070008000");
            assertThat(cancelRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo(CancelRequestStatus.PENDING);
            assertThat(refundTasks(fresh)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class)).isZero();
        }

        @Test
        @DisplayName("다른 브랜드의 다건 액션 응답이 내 하위주문의 취소 요청 여부를 드러내지 않는다 — 존재 비노출(4-4)")
        void skipReasonDoesNotLeakOtherBrandState() throws Exception {
            OrderDeliveryGroup requested = paidGroup();
            seedCancelRequest(requested);
            String other = sellerToken(otherBrand().seller());

            sellerPost(other, SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(requested.getId())))
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(requested.getId(), "ORDER_STATE_CHANGED"));
        }

        @Test
        @DisplayName("두 브랜드 공구를 한 번에 결제하면 하위주문 2개 — 각자 자기 몫만 · 한쪽 준비 시작이 소비자 전액 취소를 닫는다(X-02)")
        void compositeOrderSplitsByBrand() throws Exception {
            BrandFixture.Brand other = otherBrand();
            String otherToken = sellerToken(other.seller());
            ProductVariant otherVariant = openOtherBrandGroupBuy(other);
            GroupBuy otherGroupBuy = groupBuyRepository.findAll().stream()
                    .filter(gb -> !gb.getId().equals(groupBuy.getId())).findFirst().orElseThrow();
            Cart mine = cartItem(creamVariant, 1);
            Cart theirs = cartRepository.save(new Cart(consumer, otherVariant, otherGroupBuy, 1));

            Created created = created(createOrder(Map.of(
                    "idempotencyKey", newKey(),
                    "cartItemIds", List.of(mine.getId(), theirs.getId()),
                    "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            complete(created.paymentId()).andExpect(status().isOk());

            List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(created.orderId());
            assertThat(groups).hasSize(2);
            OrderDeliveryGroup myGroup = reload(groups.stream()
                    .filter(g -> g.getMarketId().equals(brand.marketId())).findFirst().orElseThrow());
            OrderDeliveryGroup theirGroup = deliveryGroupRepository.findOwned(groups.stream()
                    .filter(g -> g.getMarketId().equals(other.marketId())).findFirst().orElseThrow().getId(),
                    other.marketId()).orElseThrow();
            String orderNumber = myGroup.getOrder().getOrderNumber();
            assertThat(List.of(myGroup.getSubOrderNumber(), theirGroup.getSubOrderNumber()))
                    .containsExactlyInAnyOrder(orderNumber + "-01", orderNumber + "-02");
            // 발송기한 N 은 마켓별 설정의 스냅샷이다(기한 자체는 공구 마감 뒤에 확정된다).
            assertThat(myGroup.getShipDueBusinessDays()).isEqualTo(SHIPPING_LEAD_DAYS);
            assertThat(theirGroup.getShipDueBusinessDays()).isEqualTo(3);

            sellerGet(SELLER_ORDERS + "?tab=NEW")
                    .andExpect(jsonPath("$.content[*].deliveryGroupId", contains(myGroup.getId().intValue())))
                    .andExpect(jsonPath("$.content[0].items[*].productName", contains("글로우 크림 50ml")));
            sellerGet(otherToken, SELLER_ORDERS + "?tab=NEW")
                    .andExpect(jsonPath("$.content[*].deliveryGroupId", contains(theirGroup.getId().intValue())));

            prepareStart(myGroup.getId()).andExpect(jsonPath("$.succeeded").value(1));

            assertThat(deliveryGroupRepository.findOwned(theirGroup.getId(), other.marketId()).orElseThrow()
                    .getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            cancel(created.orderId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));
        }
    }

    private static ResultMatcher skippedCode(Long deliveryGroupId, String code) {
        return jsonPath("$.skipped[?(@.deliveryGroupId == " + deliveryGroupId + ")].code", contains(code));
    }
}

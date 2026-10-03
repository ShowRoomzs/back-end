package showroomz.api.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.support.BrandFixture;
import showroomz.support.ContractOptions;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오 6절 — 권한·격리·경합 횡단 케이스(X-01~X-10).
 *
 * <p>경합은 스레드를 띄워 재현하지 않는다 — H2 와 스케줄링에 따라 결과가 흔들린다. 대신 <b>두 커밋 순서를 각각 결정적으로</b>
 * 만들어, 어느 쪽이 먼저 커밋돼도 조건부 UPDATE 가 「둘 다 성공」을 허용하지 않는지 본다.
 */
@IntegrationTest
@DisplayName("[시나리오 X] 권한 · 격리 · 경합")
class OrderCrossCuttingScenarioIntegrationTest extends OrderFlowTestSupport {

    @Test
    @DisplayName("[X-01] 타 브랜드는 내 주문을 보지도 바꾸지도 못한다 — 상세·조작은 404(존재 비노출) · 다건 액션은 행만 빠진다")
    void otherBrandIsIsolated() throws Exception {
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른브랜드");
        String otherToken = sellerToken(other.seller());
        OrderDeliveryGroup mine = paidGroup();
        OrderDeliveryGroup preparing = preparingGroup();
        OrderCancelRequest request = seedCancelRequest(preparing, itemsOf(preparing));

        sellerGet(SELLER_ORDERS, otherToken).andExpect(jsonPath("$.content.length()").value(0));
        sellerGet(SELLER_ORDERS + "/summary", otherToken).andExpect(jsonPath("$.tabCounts.ALL").value(0));
        sellerGet(SELLER_ORDERS + "/" + mine.getId(), otherToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        sellerPost(SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(mine.getId())), otherToken)
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));
        sellerPost(SELLER_ORDERS + "/cancel", Map.of("deliveryGroupIds", List.of(mine.getId()),
                "reasonCode", "SOLD_OUT", "consumerMessage", "품절"), otherToken)
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_GROUP_NOT_FOUND"));
        sellerPatch(SELLER_ORDERS + "/" + mine.getId() + "/shipment",
                Map.of("carrier", "CJ", "trackingNumber", "111111111111"), otherToken)
                .andExpect(status().isNotFound());
        sellerPost(SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/approve", Map.of(), otherToken)
                .andExpect(status().isNotFound());
        sellerPost(SELLER_ORDERS + "/purchase-order",
                Map.of("deliveryGroupIds", List.of(mine.getId()), "columns", List.of("ORDER_NUMBER", "PHONE")), otherToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
        parseShipments(shipmentFile(List.of(List.of(preparing.getOrder().getOrderNumber(), "CJ대한통운", "222222222222"))),
                otherToken)
                .andExpect(jsonPath("$.rows[0].errorCode").value("ORDER_NOT_FOUND"));

        assertThat(reloadGroup(mine).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(refundTasks(mine)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM order_cancel_request WHERE cancel_request_id = ?",
                String.class, request.getId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class)).isZero();
    }

    @Test
    @DisplayName("[X-02] 두 브랜드 공구가 섞인 주문 — 하위주문 2개 · 발송기한은 마켓별 스냅샷 · 각자 자기 몫만 · 전액 취소는 양쪽 다 신규일 때만")
    void mixedBrandOrder() throws Exception {
        BrandFixture.Brand brandB = fixture.createBrand("brand-b@showroomz.test", "브랜드비");
        String brandBToken = sellerToken(brandB.seller());
        ProductVariant variantB = openGroupBuyOf(brandB);

        // 양쪽 다 신규 — 소비자 전액 취소가 된다.
        Mixed first = buyMixed(variantB);
        cancel(first.orderId()).andExpect(status().isOk());
        assertThat(reloadGroup(first.groupA()).getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
        assertThat(reloadGroup(first.groupB().getId(), brandB.marketId()).getCancelType()).isEqualTo(OrderCancelType.CONSUMER);

        Mixed second = buyMixed(variantB);
        OrderDeliveryGroup groupA = second.groupA();
        OrderDeliveryGroup groupB = second.groupB();
        String orderNumber = groupA.getOrder().getOrderNumber();
        assertThat(List.of(groupA.getSubOrderNumber(), groupB.getSubOrderNumber()))
                .containsExactlyInAnyOrder(orderNumber + "-01", orderNumber + "-02");
        LocalDateTime paidAt = groupA.getOrder().getPaidAt();
        assertThat(groupA.getShipDueAt()).isEqualTo(paidAt.plusDays(SHIPPING_LEAD_DAYS));
        assertThat(groupB.getShipDueAt()).isEqualTo(paidAt.plusDays(5));

        sellerOrders("tab=NEW")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].deliveryGroupId").value(groupA.getId()));
        sellerGet(SELLER_ORDERS + "?tab=NEW", brandBToken)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].deliveryGroupId").value(groupB.getId()));
        sellerGet(SELLER_ORDERS + "/" + groupB.getId()).andExpect(status().isNotFound());

        // A 만 준비 시작 — B 하위주문은 그대로 · 소비자 전액 취소는 이제 닫힌다.
        preparing(groupA);
        assertThat(reloadGroup(groupB.getId(), brandB.marketId()).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        appOrder(second.orderId()).andExpect(jsonPath("$.cancellable").value(false));
        cancel(second.orderId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));
    }

    @Test
    @DisplayName("[X-03] 소비자 취소가 PG 응답 대기 중이면 준비 시작·직권 취소가 끼어들지 못한다 — 수렴하면 소비자 취소로 닫히고 재고는 한 번만 돈다")
    void consumerCancelInFlightBlocksSellerActions() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = purchase.group();
        int stockBefore = stockOf(creamVariant);
        fake.willFailCancel(purchase.paymentId(), FakePaymentGateway.Failure.TIMEOUT);

        cancel(purchase.orderId()).andExpect(status().isAccepted());
        assertThat(payment(purchase.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);

        prepareStart(group.getId())
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));
        directCancel(List.of(group.getId()), "SOLD_OUT", "품절")
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(refundTasks(group)).isEmpty();
        assertThat(stockOf(creamVariant)).isEqualTo(stockBefore);

        // 수렴 — 소비자 취소로 닫힌다. 재고는 한 번만 돌아온다.
        fake.willAnswerCancel(purchase.paymentId(), PortOneCancelResult.Outcome.SUCCEEDED);
        LocalDateTime later = payment(purchase.paymentId()).getNextCancelRetryAt().plusSeconds(1);
        expirationService.convergeCancel(purchase.paymentId(), later);
        assertThat(order(purchase.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        OrderDeliveryGroup cancelled = reloadGroup(group);
        assertThat(cancelled.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
        assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
        assertThat(refundTasks(group)).isEmpty();
    }

    @Test
    @DisplayName("[X-04] 송장 확정 직전에 취소 요청이 도착하면 그 행만 빠진다 — 등록 이력이 남지 않는다")
    void requestArrivingBeforeInvoiceConfirm() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        sellerOrders("tab=PREPARING").andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()));

        seedCancelRequest(group, itemsOf(group));
        registerShipment(group, "CJ", "414141414141")
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("CANCEL_REQUEST_PENDING_EXISTS"));

        OrderDeliveryGroup reloaded = reloadGroup(group);
        assertThat(reloaded.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(reloaded.getTrackingNumber()).isNull();
        assertThat(fulfillmentEvents(group)).doesNotContain("INVOICE_REGISTERED");
    }

    @Test
    @DisplayName("[X-05] 승인과 직권 취소는 둘 다 성공하지 않는다 — 어느 순서든 환불 큐는 1행")
    void approvalAndDirectCancelNeverBothSucceed() throws Exception {
        // 승인이 먼저 — 그룹이 취소돼 직권 취소가 0행.
        OrderDeliveryGroup approvedFirst = preparingGroup();
        approveRequest(seedCancelRequest(approvedFirst, itemsOf(approvedFirst))).andExpect(status().isOk());
        directCancel(List.of(approvedFirst.getId()), "SOLD_OUT", "품절")
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));
        assertThat(refundTasks(approvedFirst)).hasSize(1);

        // 직권 취소가 먼저 시도 — 검토 중 요청이 있어 막히고, 승인만 성립한다.
        OrderDeliveryGroup directFirst = preparingGroup();
        OrderCancelRequest request = seedCancelRequest(directFirst, itemsOf(directFirst));
        directCancel(List.of(directFirst.getId()), "SOLD_OUT", "품절")
                .andExpect(jsonPath("$.skipped[0].code").value("CANCEL_REQUEST_PENDING_EXISTS"));
        approveRequest(request).andExpect(status().isOk());
        assertThat(refundTasks(directFirst)).extracting(RefundTask::source).containsExactly("CANCEL_REQUEST_APPROVED");
    }

    @Test
    @DisplayName("[X-06] 더블 클릭 — 두 번째 송장 확정·직권 취소는 0행 · 이력·환불 큐·재고 중복 없음")
    void doubleSubmitIsIdempotent() throws Exception {
        OrderDeliveryGroup shipping = preparingGroup();
        registerShipment(shipping, "CJ", "515151515151").andExpect(jsonPath("$.succeeded").value(1));
        registerShipment(shipping, "CJ", "515151515151")
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));
        assertThat(fulfillmentEvents(shipping)).filteredOn("INVOICE_REGISTERED"::equals).hasSize(1);

        OrderDeliveryGroup cancelling = paidGroup();
        int stockBefore = stockOf(creamVariant);
        directCancel(List.of(cancelling.getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(1));
        directCancel(List.of(cancelling.getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(0));
        assertThat(refundTasks(cancelling)).hasSize(1);
        assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
        assertThat(fulfillmentEvents(cancelling)).filteredOn("CANCELLED_BY_SELLER"::equals).hasSize(1);
    }

    @Test
    @DisplayName("[X-07] 조회 기간 1년 초과·정의되지 않은 값은 400 · 검색 결과가 없어도 요약 바는 전체 기준 숫자를 유지한다")
    void searchGuards() throws Exception {
        paidGroup();
        LocalDate today = LocalDate.now();

        sellerOrders("from=" + today.minusDays(400) + "&to=" + today)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_SEARCH_RANGE_EXCEEDED"));
        sellerOrders("tab=UNKNOWN").andExpect(status().isBadRequest());
        sellerOrders("dateBasis=UNKNOWN").andExpect(status().isBadRequest());

        sellerOrders("tab=NEW&searchType=ORDER_NUMBER&keyword=NO-SUCH-ORDER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        sellerSummary()
                .andExpect(jsonPath("$.actionBar.prepareStart").value(1))
                .andExpect(jsonPath("$.tabCounts.NEW").value(1));
    }

    @Test
    @DisplayName("[X-08] 공구가 중단돼도 이미 접수된 주문은 끝까지 처리된다 — 새 구매만 막힌다")
    void suspendedGroupBuyKeepsFulfilling() throws Exception {
        OrderDeliveryGroup group = paidGroup();

        moveTo(groupBuy.getId(), GroupBuyStatus.SUSPENDED);

        preparing(group);
        registerShipment(group, "CJ", "616161616161").andExpect(jsonPath("$.succeeded").value(1));
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
        createOrder(directOrder(newKey(), creamVariant, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_PURCHASABLE"));
    }

    @Test
    @DisplayName("[X-09] 게시물이 숨김이어도 판매와 발송은 계속된다 — 내려가는 것은 게시물뿐이다")
    void hiddenPostKeepsSellingAndShipping() throws Exception {
        OrderDeliveryGroup group = paidGroup();

        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);

        preparing(group);
        registerShipment(group, "CJ", "717171717171").andExpect(jsonPath("$.succeeded").value(1));
        placeCardOrder(creamVariant, 1);
    }

    @Test
    @DisplayName("[X-10] 발주서 반출은 매번 기록된다(누가 · 몇 건 · 어떤 컬럼) · 연락처·주소는 목록 API 에 없고 상세·발주서에만 있다")
    void personalDataExportIsTracked() throws Exception {
        OrderDeliveryGroup group = paidGroup();

        sellerOrders("tab=NEW")
                .andExpect(jsonPath("$.content[0].recipientName").value("김수민"))
                .andExpect(jsonPath("$.content[0].phone").doesNotExist())
                .andExpect(jsonPath("$.content[0].recipientPhone").doesNotExist())
                .andExpect(jsonPath("$.content[0].address").doesNotExist());
        sellerOrder(group)
                .andExpect(jsonPath("$.recipient.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.recipient.address").value("서울 강남구 테헤란로 000"));

        byte[] file = sellerPost(SELLER_ORDERS + "/purchase-order", Map.of(
                "columns", List.of("ORDER_NUMBER", "RECIPIENT", "PHONE", "ADDRESS"), "startPreparation", false))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(readSheet(file).get(1)).containsExactly(group.getOrder().getOrderNumber(), "김수민", "010-1234-5678",
                "서울 강남구 테헤란로 000 쇼룸타워 12층");
        // 다운로드만(견적·재고 확인용) — 준비 시작하지 않는다.
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);

        Map<String, Object> log = jdbc.queryForMap("SELECT market_id, seller_id, delivery_group_count, columns, prepare_started "
                + "FROM purchase_order_download_log");
        assertThat(((Number) log.get("market_id")).longValue()).isEqualTo(brand.marketId());
        assertThat(((Number) log.get("seller_id")).longValue()).isEqualTo(brand.seller().getId());
        assertThat(((Number) log.get("delivery_group_count")).intValue()).isEqualTo(1);
        assertThat(log.get("columns")).isEqualTo("ORDER_NUMBER,RECIPIENT,PHONE,ADDRESS");
        assertThat(log.get("prepare_started")).isEqualTo(false);
    }

    // ------------------------------------------------------------------ 픽스처

    private record Mixed(Long orderId, OrderDeliveryGroup groupA, OrderDeliveryGroup groupB) {
    }

    /** 브랜드 B 의 진행 중 공구 — 상품 1개(공구가 27,200) · 재고 10 · 배송비 3,000 · 발송 소요 5일. */
    private ProductVariant openGroupBuyOf(BrandFixture.Brand brandB) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy groupBuyB = seed(brandB, creator, "브랜드비 토너 공구", now.minusDays(3), now.plusDays(4));
        moveTo(groupBuyB.getId(), GroupBuyStatus.IN_PROGRESS);
        Long productId = jdbc.queryForObject("SELECT product_id FROM product WHERE market_id = ?", Long.class,
                brandB.marketId());
        Product productB = productRepository.findById(productId).orElseThrow();
        jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS' WHERE product_id = ?", productId);
        jdbc.update("UPDATE market SET default_delivery_fee = ?, free_shipping_threshold = ?, shipping_lead_days = ? "
                + "WHERE market_id = ?", DELIVERY_FEE, 50_000, 5, brandB.marketId());
        ProductVariant variantB = ContractOptions.variantsOf(productVariantRepository, productB).get(0);
        setStock(variantB, 10);
        groupBuyOfB = groupBuyB;
        marketOfB = brandB.marketId();
        return variantB;
    }

    private GroupBuy groupBuyOfB;
    private Long marketOfB;

    private Mixed buyMixed(ProductVariant variantB) throws Exception {
        Cart a = cartItem(creamVariant, 1);
        Cart b = cartRepository.save(new Cart(consumer, variantB, groupBuyOfB, 1));
        Created created = created(createOrder(Map.of(
                "idempotencyKey", newKey(),
                "cartItemIds", List.of(a.getId(), b.getId()),
                "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        complete(created.paymentId()).andExpect(status().isOk());
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(created.orderId());
        assertThat(groups).hasSize(2);
        OrderDeliveryGroup groupA = null;
        OrderDeliveryGroup groupB = null;
        for (OrderDeliveryGroup g : groups) {
            var mine = deliveryGroupRepository.findOwned(g.getId(), brand.marketId());
            if (mine.isPresent()) {
                groupA = mine.get();
            } else {
                groupB = reloadGroup(g.getId(), marketOfB);
            }
        }
        return new Mixed(created.orderId(), groupA, groupB);
    }
}

package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLine;
import showroomz.domain.settlement.type.SettlementItemStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 이음새 — 정산 모듈 밖에서 생긴 돈 · 회원 정보가 정산 행으로 들어오는 경계를 실제 경로로 잇는다. 단계별 정산 테스트가
 * 시드로 건너뛰는 세 곳이다.
 *
 * <p>① 클레임 재발송비 — 교환 선결제(PAID) · 교환 반려 충당(COVERED) · 반품 환불액 차감(DEDUCTED) 세 갈래가 브랜드 가산
 * {@code reship_fee}로 들어온다. 판정은 「돈의 행방」 하나다 — 소비자가 실제로 낸 돈(주문 결제 − PG 환불 + 클레임 결제)이
 * 정산의 재원(확정 거래액 + 재발송비 + 소비자 배송비)과 같아야 한다.
 * ② 두 브랜드가 섞인 주문 — 결제 1건이 공구별 정산 2건으로 갈리고, 각 정산은 자기 하위주문 · 자기 배송비만 가져간다.
 * ③ 파트너 정산계좌 변경 요청 — 「이미 확정된 정산 회차는 기존 계좌로 지급, 다음 회차부터 새 계좌」(어드민 05 승인 안내 ·
 * 파트너센터 06 승인 배너). 지급 지시 뒤에는 그때 계좌가 스냅샷으로 남는다.
 */
@DisplayName("[시나리오 SS] 정산 이음새 — 클레임 재발송비 · 두 브랜드 주문 · 정산계좌 변경")
class SettlementSeamScenarioIntegrationTest extends SettlementTestSupport {

    private static final String ADMIN = "/v1/admin/settlements";
    private static final String PARTNER = "/v1/seller/settlements";
    private static final String USER_CLAIMS = "/v1/user/claims";
    private static final String SELLER_CLAIMS = "/v1/seller/claims";
    private static final Map<String, Object> REJECT_BODY = Map.of("reasonCode", "USED",
            "detail", "사용 흔적이 있습니다.", "legalBasis", "ART17_2_2", "consumerMessage", "사용 흔적이 있습니다.",
            "evidenceImageUrls", List.of("https://img.test/e1.jpg"));

    // ================================================================== SS-1 클레임 재발송비

    @Test
    @DisplayName("SS-1a 고객 귀책 교환(재발송비 3,000 선결제) → 통과 → 새 옵션 재발송 · 도착 → 정산 재발송비 3,000 · 1건 · 교환 항목은 전액 반영")
    void paidExchangeAddsReshipFee() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant);
        OrderDeliveryGroup group = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(1));
        Long claimId = paidExchange(group, refill);
        passClaim(claimId);
        reshipAndArrive(claimId);
        assertThat(claimResult(claimId)).isEqualTo("EXCHANGED");

        Settlement s = settle(group);
        assertThat(s.getReshipFeeAmount()).isEqualTo(DELIVERY_FEE);
        assertThat(s.getReshipCount()).isEqualTo(1);
        assertThat(itemsOf(s)).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.CONFIRMED);
            assertThat(item.getSettledQuantity()).isEqualTo(1);
        });
        assertThat(inflowOf(s)).isEqualTo(consumerNet(group));
        adminGet(ADMIN + "/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.brand.reshipFee.amount").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.breakdown.brand.reshipFee.count").value(1));
    }

    @Test
    @DisplayName("SS-1b 선결제한 교환이 반려 → 반려 재발송비는 선결제분으로 충당(COVERED) → 받은 옵션 반송 · 도착 → 정산 재발송비는 소비자가 낸 3,000 한 번")
    void rejectedExchangeCountsPrepaidFeeOnce() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant);
        OrderDeliveryGroup group = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(1));
        Long claimId = paidExchange(group, refill);
        receiveAndReject(claimId);
        assertThat(chargeStatuses(claimId))
                .containsEntry("EXCHANGE_RESHIP", "PAID")
                .containsEntry("REJECT_RESHIP", "COVERED");
        reshipAndArrive(claimId);
        assertThat(claimResult(claimId)).isEqualTo("REJECTED");

        Settlement s = settle(group);
        // 소비자가 낸 돈 — 주문 30,200 + 교환 선결제 3,000. 반려 재발송은 그 선결제로 나갔다.
        assertThat(consumerNet(group)).isEqualTo(CREAM_PRICE + DELIVERY_FEE + DELIVERY_FEE);
        assertThat(s.getReshipFeeAmount()).isEqualTo(DELIVERY_FEE);
        assertThat(inflowOf(s)).isEqualTo(consumerNet(group));
    }

    @Test
    @DisplayName("SS-1c 반품 두 항목 중 하나 반려 → 반려 재발송비를 환불액에서 차감(DEDUCTED) · PG 환불 24,200 → 반려 상품 반송 · 도착 → 정산 재발송비 3,000 · 반품 항목 RETURNED")
    void partialRejectDeductsReshipFeeFromRefund() throws Exception {
        // 무료배송이 아니어야 반품 배송비 차감이 끼지 않는다 — 재발송비 한 갈래만 본다.
        jdbc.update("UPDATE market SET free_shipping_threshold = ? WHERE market_id = ?", 100_000, brand.marketId());
        OrderDeliveryGroup group = deliveredFrom(paidGroupWithTwoItems(), LocalDateTime.now().minusHours(1));
        OrderProduct creamItem = itemOf(group, cream);
        OrderProduct serumItem = itemOf(group, serum);
        List<Long> claimIds = claimService.request(new OrderClaimService.RequestCommand(consumer.getId(), group.getId(),
                ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, null, List.of(),
                List.of(new OrderClaimService.Item(creamItem.getId(), 1), new OrderClaimService.Item(serumItem.getId(), 1)),
                new OrderClaimService.Invoice(DeliveryCarrier.CJ, nextTrackingNumber()), null, null),
                LocalDateTime.now()).claimIds();
        Long creamClaim = claimOf(creamItem);
        Long serumClaim = claimOf(serumItem);
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", claimIds)).andExpect(status().isOk());
        sellerPost(SELLER_CLAIMS + "/" + creamClaim + "/inspection/pass", Map.of()).andExpect(status().isOk());
        sellerPost(SELLER_CLAIMS + "/" + serumClaim + "/inspection/reject", REJECT_BODY).andExpect(status().isOk());
        assertThat(chargeStatuses(serumClaim)).containsEntry("REJECT_RESHIP", "DEDUCTED");
        assertThat(pgRefunded(group)).isEqualTo(CREAM_PRICE - DELIVERY_FEE);
        reshipAndArrive(serumClaim);

        Settlement s = settle(group);
        assertThat(s.getConfirmedSalesAmount()).isEqualTo(SERUM_PRICE);
        assertThat(s.getReshipFeeAmount()).isEqualTo(DELIVERY_FEE);
        assertThat(itemsOf(s)).extracting(SettlementItem::getProductId, SettlementItem::getStatus)
                .containsExactlyInAnyOrder(tuple(cream.getProductId(), SettlementItemStatus.RETURNED),
                        tuple(serum.getProductId(), SettlementItemStatus.CONFIRMED));
        assertThat(inflowOf(s)).isEqualTo(consumerNet(group));
    }

    // ================================================================== SS-2 두 브랜드 주문

    @Test
    @DisplayName("SS-2 두 브랜드가 섞인 결제 1건 → 공구별 정산 2건 · 각자 자기 하위주문 · 자기 배송비만 · 두 정산의 재원 합 = 결제액 · 상대 브랜드 정산은 404")
    void mixedBrandOrderSplitsIntoTwoSettlements() throws Exception {
        MixedOrder order = deliverBoth(placeMixedBrandOrder(), LocalDateTime.now().minusDays(8));
        LocalDateTime now = LocalDateTime.now();
        assertThat(fulfillmentService.confirmPurchase(order.mine().getId(), now, now.minusDays(7))).isTrue();
        assertThat(fulfillmentService.confirmPurchase(order.other().getId(), now, now.minusDays(7))).isTrue();
        Long otherGroupBuyId = jdbc.queryForObject("SELECT group_buy_id FROM group_buy WHERE market_id = ?", Long.class,
                order.otherMarketId());
        endGroupBuy();
        moveTo(otherGroupBuyId, GroupBuyStatus.ENDED);

        Settlement mine = generate(now.withNano(0));
        Settlement other = settlement(generationService.generate(otherGroupBuyId, now.withNano(0)).orElseThrow());

        assertThat(itemsOf(mine)).extracting(SettlementItem::getDeliveryGroupId).containsOnly(order.mine().getId());
        assertThat(itemsOf(other)).extracting(SettlementItem::getDeliveryGroupId).containsOnly(order.other().getId());
        assertThat(mine.getConfirmedSalesAmount()).isEqualTo(goodsOf(order.mine()));
        assertThat(other.getConfirmedSalesAmount()).isEqualTo(goodsOf(order.other()));
        assertThat(List.of(mine, other)).extracting(Settlement::getConsumerDeliveryFeeAmount)
                .containsOnly((long) DELIVERY_FEE);
        assertThat(inflowOf(mine) + inflowOf(other)).isEqualTo(paymentAmount(order.paymentId()));

        sellerGet(PARTNER + "/" + mine.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.confirmedSalesAmount").value(mine.getConfirmedSalesAmount()));
        sellerGet(PARTNER + "/" + other.getId()).andExpect(status().isNotFound());
        sellerGet(PARTNER + "/" + other.getId(), order.otherToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.confirmedSalesAmount").value(other.getConfirmedSalesAmount()));
        sellerGet(PARTNER + "/" + mine.getId(), order.otherToken()).andExpect(status().isNotFound());
    }

    // ================================================================== SS-3 정산계좌 변경

    @Test
    @DisplayName("SS-3a 확정 전에 계좌 변경 승인 → 그 회차는 새 계좌로 지급 · 지급 뒤 다시 바꿔도 지급 행 · 파트너 지급 정보는 지시 때 계좌")
    void accountChangedBeforeConfirmationPaysNewAccount() throws Exception {
        readyToPayWithBanks();
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement reviewing = generate(LocalDateTime.now().withNano(0));

        approveAccountChange("004", "9876543210", "글로우랩 주식회사");
        Settlement scheduled = confirm(reviewing);
        assertThat(pay(scheduled, scheduled.getPayoutDueDate()).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(lastBrandLine()).extracting(PayoutLine::bankName, PayoutLine::accountNumber, PayoutLine::holder)
                .containsExactly("KB국민은행", "9876543210", "글로우랩 주식회사");

        approveAccountChange("088", "110555666777", "글로우랩");
        adminGet(ADMIN + "/" + scheduled.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.payouts.rows[?(@.payee == 'BRAND')].accountNumber").value("9876543210"))
                .andExpect(jsonPath("$.payouts.rows[?(@.payee == 'BRAND')].accountSource").value("SNAPSHOT"));
        sellerGet(PARTNER + "/" + scheduled.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.payment.accountMasked").value("****543210"));
    }

    @Test
    @DisplayName("SS-3b 확정 뒤 · 지급 지시 전에 계좌 변경 승인 → 이미 확정된 회차는 기존 계좌로 지급 · 다음 공구 회차부터 새 계좌")
    void accountChangedAfterConfirmationKeepsOldAccountForThatRound() throws Exception {
        readyToPayWithBanks();
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement scheduled = confirm(generate(LocalDateTime.now().withNano(0)));
        assertThat(scheduled.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);

        approveAccountChange("004", "9876543210", "글로우랩 주식회사");
        pay(scheduled, scheduled.getPayoutDueDate());
        assertThat(lastBrandLine()).extracting(PayoutLine::bankName, PayoutLine::accountNumber, PayoutLine::holder)
                .containsExactly("신한은행", "110123456789", "글로우랩");

        openNextGroupBuy(creator);
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement next = confirm(generate(LocalDateTime.now().withNano(0)));
        pay(next, next.getPayoutDueDate());
        assertThat(lastBrandLine()).extracting(PayoutLine::bankName, PayoutLine::accountNumber)
                .containsExactly("KB국민은행", "9876543210");
    }

    // ------------------------------------------------------------------ 흐름

    /** 공구 종료 → 정산 생성 — 클레임이 끝난 하위주문의 구매확정부터. */
    private Settlement settle(OrderDeliveryGroup group) {
        confirmLater(group);
        endGroupBuy();
        return generate(LocalDateTime.now().withNano(0));
    }

    /** 고객 귀책 교환을 앱 API 로 — 재발송비(기본 배송비) 선결제까지 끝내 회수 중으로. */
    private Long paidExchange(OrderDeliveryGroup group, ProductVariant exchangeTo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", "EXCHANGE");
        body.put("deliveryGroupId", group.getId());
        body.put("items", List.of(Map.of("orderProductId", itemsOf(group).get(0).getId(),
                "exchangeVariantId", exchangeTo.getVariantId())));
        body.put("reasonCode", "CHANGE_OF_MIND");
        body.put("expectedFee", DELIVERY_FEE);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", nextTrackingNumber()));
        body.put("payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN"));
        JsonNode created = json(mockMvc.perform(post(USER_CLAIMS).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body))).andExpect(status().isCreated()));
        String paymentId = created.at("/payment/paymentId").asText();
        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        mockMvc.perform(post(USER_CLAIMS + "/payments/" + paymentId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.claimStatus").value("COLLECTING"));
        return created.at("/claimIds/0").asLong();
    }

    private void receiveAndReject(Long claimId) throws Exception {
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId))).andExpect(status().isOk());
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", REJECT_BODY).andExpect(status().isOk());
    }

    /** 재발송 송장 등록(파트너센터) → 도착(추적 반영 — 시나리오 5-2) — 클레임 종결. */
    private void reshipAndArrive(Long claimId) throws Exception {
        String trackingNumber = nextTrackingNumber();
        sellerPost(SELLER_CLAIMS + "/reshipments", Map.of("items", List.of(Map.of(
                "claimId", claimId, "carrier", "CJ", "trackingNumber", trackingNumber))))
                .andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime at = LocalDateTime.now().minusMinutes(10).withNano(0);
        claimService.applyReshipTracking(claimId, DeliveryCarrier.CJ, trackingNumber,
                new TrackSnapshot(at, at, false, false, List.of(new TrackEvent(at, "강남", "배송완료", 6)), 6),
                LocalDateTime.now());
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim WHERE claim_id = ?", String.class, claimId))
                .isEqualTo("COMPLETED");
    }

    /** 세 수취자 모두 지시 가능 + 계좌 변경 요청이 은행 코드를 은행명으로 바꿀 수 있게 은행 둘. */
    private void readyToPayWithBanks() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        fixture.createBank("004", "KB국민은행");
        fixture.createBank("088", "신한은행");
    }

    /** 파트너센터 정산계좌 변경 요청 → 어드민 승인. */
    private void approveAccountChange(String bankCode, String accountNumber, String holder) throws Exception {
        long requestId = changeRequests.createSettlement(brandToken, bankCode, accountNumber, holder);
        mockMvc.perform(post("/v1/admin/change-requests/" + requestId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    /** 같은 상품 · 같은 가격의 옵션 「리필」을 그 공구 계약에 싣는다 — 고객 귀책 교환은 다른 옵션으로만 된다. */
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

    // ------------------------------------------------------------------ 돈의 행방

    /** 정산의 재원 — 검산식 왼쪽(확정 거래액 + 재발송비 + 소비자 배송비). */
    private static long inflowOf(Settlement s) {
        return s.getConfirmedSalesAmount() + s.getReshipFeeAmount() + s.getConsumerDeliveryFeeAmount();
    }

    /** 소비자가 이 하위주문에 실제로 낸 돈 — 주문 결제 − PG 환불 + 클레임 결제. 하위주문이 하나인 주문에만 쓴다. */
    private long consumerNet(OrderDeliveryGroup group) {
        String paymentId = paidPaymentIdOf(group);
        long claimPaid = jdbc.queryForObject("SELECT COALESCE(SUM(p.amount), 0) FROM order_claim_payment p "
                + "JOIN order_claim_collection c ON c.collection_id = p.collection_id "
                + "WHERE c.delivery_group_id = ? AND p.status = 'PAID'", Long.class, group.getId());
        return paymentAmount(paymentId) - pgRefunded(group) + claimPaid;
    }

    private long pgRefunded(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payment_cancel "
                + "WHERE payment_id = ? AND status = 'SUCCEEDED'", Long.class, paidPaymentIdOf(group));
    }

    private String paidPaymentIdOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT paid_payment_id FROM orders WHERE order_id = ?", String.class,
                group.getOrder().getId());
    }

    private long paymentAmount(String paymentId) {
        return jdbc.queryForObject("SELECT amount FROM payment WHERE payment_id = ?", Long.class, paymentId);
    }

    private long goodsOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT SUM(price * quantity) FROM order_product WHERE delivery_group_id = ?",
                Long.class, group.getId());
    }

    // ------------------------------------------------------------------ 읽기

    private Long claimOf(OrderProduct item) {
        return jdbc.queryForObject("SELECT claim_id FROM order_claim WHERE order_product_id = ?", Long.class,
                item.getId());
    }

    private String claimResult(Long claimId) {
        return jdbc.queryForObject("SELECT result FROM order_claim WHERE claim_id = ?", String.class, claimId);
    }

    /** 그 클레임 요청의 재발송비 행 — 유형 → 상태. */
    private Map<String, String> chargeStatuses(Long claimId) {
        Map<String, String> statuses = new HashMap<>();
        jdbc.queryForList("SELECT type, status FROM order_claim_charge WHERE collection_id = "
                        + "(SELECT collection_id FROM order_claim WHERE claim_id = ?)", claimId)
                .forEach(row -> statuses.put((String) row.get("type"), (String) row.get("status")));
        return statuses;
    }

    private PayoutLine lastBrandLine() {
        List<PayoutCommand> calls = payoutGateway.calls();
        return calls.get(calls.size() - 1).lines().stream()
                .filter(line -> line.payee() == SettlementPayee.BRAND)
                .findFirst().orElseThrow();
    }
}

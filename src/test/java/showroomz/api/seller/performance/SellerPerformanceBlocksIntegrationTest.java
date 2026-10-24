package showroomz.api.seller.performance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.member.creator.entity.CreatorFollowEvent;
import showroomz.domain.member.creator.repository.CreatorFollowEventRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.product.entity.ProductDetailView;
import showroomz.domain.product.repository.ProductDetailViewRepository;
import showroomz.domain.product.type.ProductDetailViewSource;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 성과 관리(12) — 상세 블록 검산(48 파트너 성과 테스트 시나리오 1-2 F-MAIN · 1-3 F-SHEET · 3절 FS · 6절 · 8절 · 9절).
 *
 * <p>F-MAIN 은 실제 경로로 종결한 하위주문 8건이다 — 구매확정 2 · 부분 반품 후 구매확정 1 · 전량 반품 1 · 직권 취소 1 · 결제 직후
 * 소비자 취소 1 · 분실(어드민 분실 처리) 1 · 반송 완료(추적 반영 → PG 자동 환불) 1. 소비자 · 결제 · 발송 시각만 SQL 로 맞춘다.
 */
@DisplayName("파트너센터 성과 관리(12) — 상세 블록 검산 · 값 상태 매트릭스 · 원가")
class SellerPerformanceBlocksIntegrationTest extends SettlementTestSupport {

    private static final String PERF = "/v1/seller/performance";
    private static final long FIXED_FEE = 300_000;
    private static final int SERUM = 24_000;
    private static final String CREAM_NAME_PART = "크림";

    @Autowired private ShowroomVisitRepository showroomVisitRepository;
    @Autowired private CreatorFollowEventRepository followEventRepository;
    @Autowired private ContractItemRepository contractItemRepository;
    @Autowired private ProductDetailViewRepository productDetailViewRepository;
    @Autowired private ProductInquiryRepository productInquiryRepository;

    // ------------------------------------------------------------------ F-MAIN

    /** F-MAIN 8건 + 방문 10명 · 상품 조회 · 문의 · 팔로우 — 공구는 종료 · 정산 전(집계 중)으로 둔다. */
    private void seedMain() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        OrderDeliveryGroup g1 = confirmedGroup(creamVariant, 1);
        OrderDeliveryGroup g2 = confirmedGroup(serumVariant, 1);

        OrderDeliveryGroup g3 = deliveredFrom(purchase(creamVariant, 3).group(), now.minusDays(1));
        passClaim(returnClaim(g3, itemsOf(g3).get(0), 1, ClaimReason.DAMAGED_OR_DEFECTIVE));
        g3 = confirmLater(g3);

        OrderDeliveryGroup g4 = deliveredFrom(purchase(serumVariant, 1).group(), now.minusDays(1));
        passClaim(returnClaim(g4, itemsOf(g4).get(0), 1, ClaimReason.CHANGE_OF_MIND));
        g4 = reloadGroup(g4);
        if (g4.getFulfillmentStatus().isSettlementOpen()) {
            g4 = confirmLater(g4);
        }

        OrderDeliveryGroup g5 = cancelledBySeller(preparing(purchase(creamVariant, 1).group()));

        Purchase p6 = purchase(serumVariant, 1);
        cancel(p6.orderId()).andExpect(status().isOk());
        OrderDeliveryGroup g6 = reloadGroup(p6.group());

        OrderDeliveryGroup g7 = shippingGroup(nextTrackingNumber());
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ?, shipped_at = ? "
                + "WHERE delivery_group_id = ?", now.minusDays(29), now.minusDays(30), g7.getId());
        adminPost(ADMIN_ORDERS + "/groups/" + g7.getId() + "/lost", Map.of("reason", "택배사 분실 확인"))
                .andExpect(status().isOk());

        OrderDeliveryGroup g8 = returningGroup(nextTrackingNumber());
        track(g8, new TrackSnapshot(now, null, true, true), now);

        // 소비자 — A(여 · 20대)가 G1 · G2, B(남 · 40대)가 G3, 나머지는 각자 다른 소비자
        identity(consumer, "1998-03-02", "FEMALE");
        Users b = createConsumer("perf-b", "소비자B");
        identity(b, "1985-01-10", "MALE");
        reassign(g3, b);
        List<OrderDeliveryGroup> others = List.of(g4, g5, g6, g7, g8);
        for (int i = 0; i < others.size(); i++) {
            reassign(others.get(i), createConsumer("perf-" + (char) ('c' + i), "소비자" + (char) ('C' + i)));
        }

        // 결제 · 발송 시각 — 결제 T0 · 기한 T0 + 2일 · 발송 T0 + 1일(G8 만 T0 + 3일 = 기한 초과)
        LocalDateTime t0 = groupBuy.getStartAt().plusDays(1);
        for (OrderDeliveryGroup g : List.of(g1, g2, g3, g4, g5, g6, g7, g8)) {
            jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = (SELECT order_id FROM order_delivery_group "
                    + "WHERE delivery_group_id = ?)", t0, g.getId());
        }
        for (OrderDeliveryGroup g : List.of(g1, g2, g3, g4, g7, g8)) {
            jdbc.update("UPDATE order_delivery_group SET ship_due_at = ?, shipped_at = ? WHERE delivery_group_id = ?",
                    t0.plusDays(2), g == g8 ? t0.plusDays(3) : t0.plusDays(1), g.getId());
        }

        // A 는 결제 전부터 팔로워
        followEventRepository.save(CreatorFollowEvent.follow(creator.getId(), consumer.getId(),
                groupBuy.getStartAt().minusDays(1), null));

        // 방문 10명 · 창 밖 1명
        for (int i = 1; i <= 10; i++) {
            visit("d:v" + i, groupBuy.getStartAt().plusHours(1));
        }
        visit("d:outside", groupBuy.getStartAt().minusMinutes(1));

        // 상품 상세 조회 — 크림 6회(조회자 v1 · v2 · v3) · 세럼 2회(v3 · v4) → 공구 고유 조회자 4명
        LocalDateTime viewAt = groupBuy.getStartAt().plusHours(2);
        for (String key : List.of("d:v1", "d:v1", "d:v1", "d:v2", "d:v2", "d:v3")) {
            productView(cream.getProductId(), key, viewAt);
        }
        productView(serum.getProductId(), "d:v3", viewAt);
        productView(serum.getProductId(), "d:v4", viewAt);

        // 상품 문의 — 답변 2h · 4h 만 든다(미답변 · 시작 전 · 종료 + 7일 정각은 빠진다)
        LocalDateTime periodEnd = reload(groupBuy.getId()).getEndAt();
        inquiry(t0, t0.plusHours(2));
        inquiry(t0, t0.plusHours(4));
        inquiry(t0, null);
        inquiry(groupBuy.getStartAt().minusMinutes(1), groupBuy.getStartAt().plusHours(1));
        inquiry(periodEnd.plusDays(7), periodEnd.plusDays(7).plusHours(1));

        endGroupBuy();
    }

    @Test
    @DisplayName("F-MAIN 집계 중(D2) — 종결 0 · 판매 PROJECTED · 예정 리워드 · 원가 잠금 → 정산 생성 뒤 같은 값이 FINAL(XC-03)")
    void projectedMatchesSettled() throws Exception {
        seedMain();

        JsonNode d2 = detail();
        assertThat(d2.at("/status").asText()).isEqualTo("AGGREGATING");
        assertThat(d2.at("/aggregating/unclosedCount").asInt()).isZero();
        assertThat(d2.at("/aggregating/openReturnCount").asLong()).isZero();
        assertThat(d2.at("/breakdown/pendingReturnCount").asLong()).isZero();
        assertThat(d2.at("/kpi/confirmedSales/state").asText()).isEqualTo("PROJECTED");
        assertThat(d2.at("/kpi/confirmedSales/value").asLong()).isEqualTo(105_600);
        assertThat(d2.at("/kpi/confirmedSales/basis").asText()).isEqualTo("구매확정 예정 3건 기준");
        assertThat(d2.at("/kpi/confirmedQuantity/value").asLong()).isEqualTo(4);
        assertThat(d2.at("/contract/reward/state").asText()).isEqualTo("PROJECTED");
        assertThat(d2.at("/contract/reward/value").asLong()).isEqualTo(12_192);
        assertThat(d2.at("/delivery/claimRate/state").asText()).isEqualTo("PROJECTED");
        assertThat(d2.at("/delivery/shipOnTimeRate/state").asText()).isEqualTo("FINAL");
        assertThat(d2.at("/breakdown/deductionTotal/state").asText()).isEqualTo("PROJECTED");
        assertThat(d2.at("/consumers/followerShare/state").asText()).isEqualTo("FINAL");
        assertThat(d2.at("/consumers/estimated").asBoolean()).isTrue();
        assertThat(d2.at("/cost/items/0/subtotal").isNull()).isTrue();

        Settlement s = generate(LocalDateTime.now().withNano(0));
        assertThat(s.getConfirmedSalesAmount()).isEqualTo(105_600);
        assertThat(s.getRewardAmount()).isEqualTo(12_192);
        JsonNode d1 = detail();
        assertThat(d1.at("/kpi/confirmedSales/state").asText()).isEqualTo("FINAL");
        assertThat(d1.at("/kpi/confirmedSales/value").asLong()).isEqualTo(d2.at("/kpi/confirmedSales/value").asLong());
        assertThat(d1.at("/kpi/confirmedQuantity/value").asLong()).isEqualTo(4);
        assertThat(d1.at("/contract/reward/value").asLong()).isEqualTo(d2.at("/contract/reward/value").asLong());
        assertThat(d1.at("/breakdown/deductionTotal/value").asLong())
                .isEqualTo(d2.at("/breakdown/deductionTotal/value").asLong());
        assertThat(d1.at("/aggregating").isNull()).isTrue();
        assertThat(d1.at("/breakdown/pendingReturnCount").isNull()).isTrue();
    }

    @Test
    @DisplayName("F-MAIN 정산 확인 중(D1) — KPI · 퍼널 · 상품별 · 하이라이트 · 분해 · 배송 품질 · 소비자 검산 · 목록 · 정산 13 과 대조")
    void mainBlocks() throws Exception {
        seedMain();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        JsonNode d = detail();

        // KPI 8 · 계약 바
        assertThat(d.at("/status").asText()).isEqualTo("REVIEWING");
        assertThat(d.at("/kpi/confirmedSales/value").asLong()).isEqualTo(105_600);
        assertThat(d.at("/kpi/confirmedSales/basis").asText()).isEqualTo("구매확정 3건 기준");
        assertThat(d.at("/kpi/payout/value").asLong()).isEqualTo(s.getBrandPayoutAmount());
        assertThat(d.at("/kpi/conversionRate/value").decimalValue()).isEqualByComparingTo("30.0");
        assertThat(d.at("/kpi/confirmRate/value").decimalValue()).isEqualByComparingTo("37.5");
        assertThat(d.at("/kpi/confirmRate/basis").asText()).isEqualTo("주문 8건 중 3건 확정");
        assertThat(d.at("/kpi/confirmedQuantity/value").asLong()).isEqualTo(4);
        assertThat(d.at("/kpi/confirmedQuantity/basis").asText()).isEqualTo("주문 수량 10개");
        assertThat(d.at("/kpi/orderedQuantity").asLong()).isEqualTo(10);
        assertThat(d.at("/kpi/aov/value").asLong()).isEqualTo(35_200);
        assertThat(d.at("/kpi/roas/value").asLong()).isEqualTo(34);
        assertThat(d.at("/kpi/repurchaseRate/value").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(d.at("/contract/reward/value").asLong()).isEqualTo(12_192);
        assertThat(d.at("/contract/totalSpend/value").asLong()).isEqualTo(FIXED_FEE + 12_192);
        assertThat(d.at("/contract/rewardRateLabel").asText()).isEqualTo("상품별");
        assertThat(d.at("/contract/fixedFeeAmount").asLong()).isEqualTo(FIXED_FEE);

        // ① 유입 · 퍼널(TR-04 창 밖 제외 · TR-11 누적 도달 · 검산식)
        JsonNode t = d.at("/traffic");
        assertThat(t.at("/visitorCount").asLong()).isEqualTo(10);
        assertThat(t.at("/funnel/orders").asInt()).isEqualTo(8);
        assertThat(t.at("/funnel/notOrdered").asLong()).isEqualTo(2);
        assertThat(t.at("/funnel/shipped").asLong()).isEqualTo(6);
        assertThat(t.at("/funnel/delivered").asLong()).isEqualTo(4);
        assertThat(t.at("/funnel/confirmed/value").asInt()).isEqualTo(3);
        assertThat(t.at("/funnel/cancelled").asInt()).isEqualTo(2);
        assertThat(t.at("/funnel/returned").asInt()).isEqualTo(1);
        assertThat(t.at("/funnel/deliveryException").asInt()).isEqualTo(2);
        assertThat(t.at("/funnel/conversionRate/value").decimalValue()).isEqualByComparingTo("30.0");
        assertThat(t.at("/limits").size()).isEqualTo(3);

        // ② 판매(SL-02 ~ 07)
        JsonNode creamRow = product(d, CREAM_NAME_PART);
        JsonNode serumRow = product(d, "세럼");
        assertThat(creamRow.at("/orderCount").asInt()).isEqualTo(5);
        assertThat(creamRow.at("/confirmedQuantity/value").asLong()).isEqualTo(3);
        assertThat(creamRow.at("/confirmedSales/value").asLong()).isEqualTo(81_600);
        assertThat(creamRow.at("/returnRate/value").decimalValue()).isEqualByComparingTo("33.3");
        assertThat(creamRow.at("/detailViews").asLong()).isEqualTo(6);
        assertThat(creamRow.at("/viewers").asLong()).isEqualTo(3);
        assertThat(creamRow.at("/conversionRate/value").decimalValue()).isEqualByComparingTo("166.7");
        assertThat(serumRow.at("/orderCount").asInt()).isEqualTo(3);
        assertThat(serumRow.at("/confirmedQuantity/value").asLong()).isEqualTo(1);
        assertThat(serumRow.at("/confirmedSales/value").asLong()).isEqualTo(SERUM);
        assertThat(serumRow.at("/returnRate/value").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(serumRow.at("/conversionRate/value").decimalValue()).isEqualByComparingTo("150.0");
        JsonNode total = d.at("/sales/total");
        assertThat(total.at("/detailViews").asLong()).isEqualTo(8);
        assertThat(total.at("/viewers").asLong()).isEqualTo(4); // 상품별 합 5 보다 작다 — 중복 제거
        assertThat(total.at("/orderCount").asInt()).isEqualTo(8);
        assertThat(total.at("/confirmedSales/value").asLong()).isEqualTo(105_600);
        assertThat(total.at("/returnRate/value").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(total.at("/returnRate/basis").asText()).isEqualTo("공구 전체 반품률 12.5% · 반품 1건 ÷ 주문 8건");
        assertThat(d.at("/sales/highlights/mostViewed/productName").asText()).contains(CREAM_NAME_PART);
        assertThat(d.at("/sales/highlights/mostViewed/basis").asText()).isEqualTo("상세 조회 6회 · 전체의 75.0%");
        assertThat(d.at("/sales/highlights/bestConversion/productName").asText()).contains(CREAM_NAME_PART);
        assertThat(d.at("/sales/highlights/highestReturn/productName").asText()).contains("세럼");
        assertThat(d.at("/sales/highlights/highestReturn/basis").asText()).isEqualTo("확정 1개 중 1개 · 100.0%");

        // ③ 분해(BD-01 · 02 · 04 · 05)
        JsonNode b = d.at("/breakdown");
        assertThat(b.at("/totalCount").asInt()).isEqualTo(5);
        assertThat(b.at("/deductionTotal/value").asLong()).isEqualTo(156_800)
                .isEqualTo(s.getCancelDeduction() + s.getReturnDeduction() + s.getDeliveryExceptionDeduction());
        assertThat(b.at("/cancel/amount").asLong() + b.at("/returned/amount").asLong()
                + b.at("/deliveryException/amount").asLong()).isEqualTo(156_800);
        assertThat(b.at("/cancel/count").asInt()).isEqualTo(2);
        assertThat(b.at("/cancel/amount").asLong()).isEqualTo(CREAM_PRICE + SERUM);
        assertLine(b.at("/cancel/lines"), "AFTER_PAYMENT", "결제 직후", 1, SERUM, 1);
        assertLine(b.at("/cancel/lines"), "PREPARING", "상품준비중", 0, 0, 0);
        assertLine(b.at("/cancel/lines"), "SELLER_DIRECT", "직권 취소", 1, CREAM_PRICE, 1);
        assertThat(keys(b.at("/cancel/lines"))).doesNotContain("OTHER");
        assertThat(b.at("/returned/count").asInt()).isEqualTo(1);
        assertThat(b.at("/returned/amount").asLong()).isEqualTo(CREAM_PRICE + SERUM);
        assertThat(keys(b.at("/returned/lines"))).containsExactly("CHANGE_OF_MIND", "DAMAGED_OR_DEFECTIVE");
        assertLine(b.at("/returned/lines"), "CHANGE_OF_MIND", "단순 변심", 1, SERUM, 1);
        assertLine(b.at("/returned/lines"), "DAMAGED_OR_DEFECTIVE", "배송 상품 파손 및 불량", 1, CREAM_PRICE, 0);
        assertThat(b.at("/deliveryException/count").asInt()).isEqualTo(2);
        assertLine(b.at("/deliveryException/lines"), "RETURNED_TO_SENDER", "반송", 1, CREAM_PRICE, 1);
        assertLine(b.at("/deliveryException/lines"), "LOST", "분실", 1, CREAM_PRICE, 1);
        assertThat(keys(b.at("/deliveryException/lines"))).doesNotContain("OPERATOR_REFUND");

        // ③ 배송 품질(DL-01 ~ 04)
        JsonNode dl = d.at("/delivery");
        assertThat(dl.at("/shipOnTimeRate/value").decimalValue()).isEqualByComparingTo("83.3");
        assertThat(dl.at("/shipOnTimeRate/basis").asText()).isEqualTo("발송 6건 중");
        assertThat(dl.at("/avgShipDays/value").decimalValue()).isEqualByComparingTo("1.3");
        assertThat(dl.at("/claimRate/value").decimalValue()).isEqualByComparingTo("25.0");
        assertThat(dl.at("/claimRate/basis").asText()).isEqualTo("반송·분실 2건");
        assertThat(dl.at("/avgInquiryHours/value").decimalValue()).isEqualByComparingTo("3.0");
        assertThat(dl.at("/avgInquiryHours/basis").asText()).isEqualTo("상품 문의 답변까지 · 2건");

        // ④ 소비자(CS-01 · 03 · 04 · 05 · 06 · 08 · 11)
        JsonNode c = d.at("/consumers");
        assertThat(c.at("/confirmedOrderCount").asInt()).isEqualTo(3);
        assertThat(c.at("/confirmedBuyerCount").asInt()).isEqualTo(2);
        assertThat(c.at("/newBuyerCount/value").asLong()).isEqualTo(2);
        assertThat(c.at("/newBuyerCount/basis").asText()).isEqualTo("구매확정 소비자 중 100.0%");
        assertThat(c.at("/repurchaseBuyerCount/value").asLong()).isZero();
        assertThat(c.at("/followerShare/value").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(c.at("/acquisitionCost/value").asLong()).isEqualTo(156_096);
        assertThat(c.at("/gender/female").asLong()).isEqualTo(1);
        assertThat(c.at("/gender/male").asLong()).isEqualTo(1);
        assertThat(c.at("/gender/unknown").asLong()).isZero();
        assertThat(c.at("/gender/femaleRatio").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(c.at("/ageBands").size()).isEqualTo(1);
        assertThat(c.at("/ageBands/0/band").asText()).isEqualTo("OTHER");
        assertThat(c.at("/ageBands/0/total").asLong()).isEqualTo(2);
        assertThat(c.at("/ageBands/0/ratio").decimalValue()).isEqualByComparingTo("100.0");
        assertNoConsumerIdentity(d);

        // ⑥ 정산 연결 · 교차(SE-02 · XC-01 · XC-02)
        assertThat(d.at("/settlement/status").asText()).isEqualTo("REVIEWING");
        assertThat(d.at("/settlement/reviewDueAt").isNull()).isFalse();
        assertThat(d.at("/settlement/payoutDueDate").isNull()).isTrue();
        assertThat(d.at("/settlement/adjustment").isNull()).isTrue();
        JsonNode partner13 = json(sellerGet("/v1/seller/settlements/" + s.getId()).andExpect(status().isOk()));
        assertThat(d.at("/settlement/breakdown")).isEqualTo(partner13.at("/breakdown"));

        JsonNode row = json(sellerGet(PERF + "/group-buys").andExpect(status().isOk())).at("/content/0");
        assertThat(row.at("/visitors/value").asLong()).isEqualTo(t.at("/visitorCount").asLong());
        assertThat(row.at("/conversionRate/value").decimalValue()).isEqualByComparingTo(d.at("/kpi/conversionRate/value").decimalValue());
        assertThat(row.at("/confirmedSales/value").asLong()).isEqualTo(d.at("/kpi/confirmedSales/value").asLong());
        assertThat(row.at("/payout/value").asLong()).isEqualTo(d.at("/kpi/payout/value").asLong());
        assertThat(row.at("/confirmedQuantity/value").asLong()).isEqualTo(d.at("/kpi/confirmedQuantity/value").asLong());
        assertThat(row.at("/confirmRate/value").decimalValue()).isEqualByComparingTo(d.at("/kpi/confirmRate/value").decimalValue());
    }

    @Test
    @DisplayName("CO-03 · 04 원가 — 소계는 확정 수량 기준 · 총 원가 47,000 · 수익 = 정산액 − 47,000 − 고정 지급비(음수 그대로)")
    void mainCost() throws Exception {
        seedMain();
        Settlement s = generate(LocalDateTime.now().withNano(0));

        JsonNode d = json(put(costUrl(), costBody(10_000L, 9_000L, 2_000L, 6_000L, null, 0L)).andExpect(status().isOk()));
        JsonNode creamItem = costItem(d, CREAM_NAME_PART);
        assertThat(creamItem.at("/confirmedQuantity/value").asLong()).isEqualTo(3);
        assertThat(creamItem.at("/subtotal").asLong()).isEqualTo(30_000); // 주문 수량 7 이 아니라 확정 3
        assertThat(costItem(d, "세럼").at("/subtotal").asLong()).isEqualTo(9_000);
        assertThat(d.at("/cost/totalCost/value").asLong()).isEqualTo(47_000);
        long profit = s.getBrandPayoutAmount() - 47_000 - FIXED_FEE;
        assertThat(profit).isNegative();
        assertThat(d.at("/cost/profit/value").asLong()).isEqualTo(profit);
        assertThat(d.at("/cost/profit/basis").asText()).isEqualTo("정산액 − 총 원가 − 고정 지급비");
        assertThat(d.at("/cost/profitRate/value").decimalValue())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(profit * 100.0 / 105_600)
                        .setScale(1, java.math.RoundingMode.HALF_UP));
        assertThat(d.at("/cost/profitRate/basis").asText()).isEqualTo("확정 거래액 대비");
    }

    // ------------------------------------------------------------------ FS 값 상태 매트릭스

    @Test
    @DisplayName("FS-01 · 03 · 04 값 상태 — 정산 확인 중 · 조정 협의 · 지급 예정 · 지급 완료 · 분배 실패 · 합의 반영 · 합의 없이 만료")
    void figureStateMatrix() throws Exception {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        visit("d:m1", groupBuy.getStartAt().plusHours(1));
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        put(costUrl(), costBody(10_000L, 9_000L, 0L, 0L, 0L, 0L)).andExpect(status().isOk());
        long sales = s.getConfirmedSalesAmount();
        long reward = s.getRewardAmount();
        long totalCost = 19_000;

        // 정산 확인 중 — 돈 값 FINAL · 원래 값 없음
        JsonNode reviewing = detail();
        assertSettledFinal(reviewing, "REVIEWING", "NEUTRAL");
        assertThat(reviewing.at("/kpi/payout/original").isNull()).isTrue();
        assertThat(reviewing.at("/contract/reward/original").isNull()).isTrue();
        assertThat(reviewing.at("/cost/profit/value").asLong()).isEqualTo(s.getBrandPayoutAmount() - totalCost - FIXED_FEE);

        // 조정 협의(FS-04 — 리워드가 같아도 NEGOTIATING 경로)
        moveSettlement(s, "ADJUSTING");
        JsonNode adjusting = detail();
        assertThat(adjusting.at("/statusTone").asText()).isEqualTo("WARNING");
        assertThat(adjusting.at("/kpi/confirmedSales/state").asText()).isEqualTo("FINAL");
        assertThat(adjusting.at("/traffic/funnel/confirmed/state").asText()).isEqualTo("FINAL");
        for (String path : List.of("/kpi/payout", "/kpi/roas", "/contract/reward", "/contract/totalSpend",
                "/consumers/acquisitionCost", "/cost/profit", "/cost/profitRate")) {
            assertThat(adjusting.at(path + "/state").asText()).as(path).isEqualTo("NEGOTIATING");
            assertThat(adjusting.at(path + "/value").isNull()).as(path).isTrue();
            assertThat(adjusting.at(path + "/original").isNull()).as(path).isFalse();
        }
        assertThat(adjusting.at("/kpi/payout/original").asLong()).isEqualTo(s.getBrandPayoutAmount());
        assertThat(adjusting.at("/contract/reward/original").asLong()).isEqualTo(s.getOriginalRewardAmount());
        assertThat(adjusting.at("/contract/reward/basis").asText()).isEqualTo("조정 협의 중");
        assertThat(adjusting.at("/contract/totalSpend/basis").asText()).isEqualTo("합의 후 변동");
        assertThat(adjusting.at("/cost/profit/original").asLong())
                .isEqualTo(s.getBrandPayoutAmount() - totalCost - FIXED_FEE);
        assertThat(adjusting.at("/cost/totalCost/state").asText()).isEqualTo("FINAL");
        assertThat(adjusting.at("/cost/editable").asBoolean()).isTrue();
        put(costUrl(), costBody(10_000L, 9_000L, 0L, 0L, 0L, 0L)).andExpect(status().isOk()); // CO-11 협의 중에도 저장

        // 합의 없이 만료(FS-03) — 지급 예정 · 원래 값 없음
        moveSettlement(s, "PAYOUT_SCHEDULED");
        JsonNode scheduled = detail();
        assertSettledFinal(scheduled, "PAYOUT_SCHEDULED", "NEUTRAL");
        for (String path : List.of("/kpi/payout", "/kpi/roas", "/contract/reward", "/contract/totalSpend",
                "/consumers/acquisitionCost", "/cost/profit", "/cost/profitRate")) {
            assertThat(scheduled.at(path + "/original").isNull()).as(path).isTrue();
        }
        assertThat(scheduled.at("/kpi/payout/basis").asText()).doesNotContain("합의 반영");

        // 지급 완료 · 분배 실패 — 같은 화면(FS-02)
        moveSettlement(s, "PAID");
        assertSettledFinal(detail(), "PAID", "SUCCESS");
        moveSettlement(s, "PAYOUT_FAILED");
        assertSettledFinal(detail(), "PAID", "SUCCESS");

        // 합의 반영(D4) — 리워드 −1,000 · 정산액은 저장값 · 원래 값은 정산 계산기가 원래 리워드로 낸 값
        long agreed = reward - 1_000;
        long agreedPayout = settlementCalculator.preview(s, agreed).brandPayoutAmount();
        jdbc.update("UPDATE settlement SET status = 'PAID', reward_amount = ?, brand_payout_amount = ? "
                + "WHERE settlement_id = ?", agreed, agreedPayout, s.getId());
        JsonNode d4 = detail();
        assertSettledFinal(d4, "PAID", "SUCCESS");
        assertThat(d4.at("/kpi/payout/value").asLong()).isEqualTo(agreedPayout);
        assertThat(d4.at("/kpi/payout/original").asLong()).isEqualTo(s.getBrandPayoutAmount());
        assertThat(d4.at("/kpi/payout/basis").asText()).isEqualTo("합의 반영");
        assertThat(d4.at("/contract/reward/value").asLong()).isEqualTo(agreed);
        assertThat(d4.at("/contract/reward/original").asLong()).isEqualTo(reward);
        assertThat(d4.at("/contract/reward/basis").asText()).isEqualTo("합의 반영");
        assertThat(d4.at("/contract/totalSpend/value").asLong()).isEqualTo(FIXED_FEE + agreed);
        assertThat(d4.at("/contract/totalSpend/original").asLong()).isEqualTo(FIXED_FEE + reward);
        assertThat(d4.at("/kpi/roas/original").asLong()).isEqualTo(Math.round(sales * 100.0 / (FIXED_FEE + reward)));
        assertThat(d4.at("/cost/profit/value").asLong()).isEqualTo(agreedPayout - totalCost - FIXED_FEE);
        assertThat(d4.at("/cost/profit/original").asLong()).isEqualTo(s.getBrandPayoutAmount() - totalCost - FIXED_FEE);
        JsonNode row = json(sellerGet(PERF + "/group-buys")).at("/content/0");
        assertThat(row.at("/payout/value").asLong()).isEqualTo(agreedPayout);
        assertThat(row.at("/payout/original").asLong()).isEqualTo(s.getBrandPayoutAmount());
    }

    @Test
    @DisplayName("PF5 ② · PF6 ② F-SHEET — 시안 D1(4,712,800 · 578% · 1,224,200 · 22.3%) · D4(4,773,080 · 614% · 1,284,480 · 23.4%)")
    void sheetFigures() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE contract SET fixed_fee_amount = 400000 WHERE contract_id = ?", groupBuy.getContract().getId());
        jdbc.update("UPDATE settlement SET status = 'PAID', confirmed_sales_amount = 5480000, reward_amount = 548000, "
                + "original_reward_amount = 548000, reward_vat_amount = 54800, pg_fee_amount = 164400, "
                + "platform_fee_amount = 0, consumer_delivery_fee_amount = 0, reship_fee_amount = 0, "
                + "brand_payout_amount = 4712800 WHERE settlement_id = ?", s.getId());
        put(costUrl(), costBody(0L, 0L, null, null, null, 3_088_600L)).andExpect(status().isOk());

        JsonNode d1 = detail();
        assertThat(d1.at("/kpi/payout/value").asLong()).isEqualTo(4_712_800);
        assertThat(d1.at("/kpi/payout/original").isNull()).isTrue();
        assertThat(d1.at("/kpi/payout/basis").asText()).isEqualTo("PG·리워드·리워드 부가세 차감 후 실지급 · 정산 관리 기준");
        assertThat(d1.at("/contract/totalSpend/value").asLong()).isEqualTo(948_000);
        assertThat(d1.at("/kpi/roas/value").asLong()).isEqualTo(578);
        assertThat(d1.at("/cost/totalCost/value").asLong()).isEqualTo(3_088_600);
        assertThat(d1.at("/cost/profit/value").asLong()).isEqualTo(1_224_200);
        assertThat(d1.at("/cost/profitRate/value").decimalValue()).isEqualByComparingTo("22.3");

        jdbc.update("UPDATE settlement SET reward_amount = 493200, reward_vat_amount = 49320, "
                + "brand_payout_amount = 4773080 WHERE settlement_id = ?", s.getId());
        JsonNode d4 = detail();
        assertThat(d4.at("/status").asText()).isEqualTo("PAID");
        assertThat(d4.at("/kpi/payout/value").asLong()).isEqualTo(4_773_080);
        assertThat(d4.at("/kpi/payout/original").asLong()).isEqualTo(4_712_800); // 정산 계산기가 원래 리워드로 낸 값(0-1)
        assertThat(d4.at("/contract/totalSpend/value").asLong()).isEqualTo(893_200);
        assertThat(d4.at("/contract/totalSpend/original").asLong()).isEqualTo(948_000);
        assertThat(d4.at("/kpi/roas/value").asLong()).isEqualTo(614);
        assertThat(d4.at("/kpi/roas/original").asLong()).isEqualTo(578);
        assertThat(d4.at("/cost/profit/value").asLong()).isEqualTo(1_284_480);
        assertThat(d4.at("/cost/profit/original").asLong()).isEqualTo(1_224_200);
        assertThat(d4.at("/cost/profitRate/value").decimalValue()).isEqualByComparingTo("23.4");
        assertThat(d4.at("/cost/profitRate/original").decimalValue()).isEqualByComparingTo("22.3");
    }

    /** 다른 공구의 정산 후 환불 차감(−24,150)이 이 공구 성과를 깎으면 안 된다 — 차감 후 금액은 정산 연결 breakdown 의 「이번 지급액」으로만 보인다. */
    @Test
    @DisplayName("[56 A-1] 1021 시안 숫자 — 이전 정산 차감이 있어도 정산액은 브랜드 수취액 4,775,800 · 수익 1,287,200 · 23.5% · 이번 지급액 4,751,650 은 정산 연결에만")
    void payoutIsBeforeClawback() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE contract SET fixed_fee_amount = 400000 WHERE contract_id = ?", groupBuy.getContract().getId());
        jdbc.update("UPDATE settlement SET status = 'PAYOUT_SCHEDULED', confirmed_sales_amount = 5480000, reward_amount = 548000, "
                + "original_reward_amount = 548000, reward_vat_amount = 54800, pg_fee_amount = 164400, "
                + "platform_fee_amount = 0, consumer_delivery_fee_amount = 30000, reship_fee_amount = 27000, "
                + "return_deduction_fee_amount = 6000, brand_clawback_amount = 24150, "
                + "brand_payout_amount = 4751650 WHERE settlement_id = ?", s.getId());
        put(costUrl(), costBody(0L, 0L, null, null, null, 3_088_600L)).andExpect(status().isOk());

        JsonNode d = detail();
        assertThat(d.at("/kpi/payout/value").asLong()).isEqualTo(4_775_800);
        assertThat(d.at("/cost/profit/value").asLong()).isEqualTo(1_287_200);
        assertThat(d.at("/cost/profitRate/value").decimalValue()).isEqualByComparingTo("23.5");
        assertThat(d.at("/settlement/breakdown/brandPayoutBeforeClawback").asLong()).isEqualTo(4_775_800);
        assertThat(d.at("/settlement/breakdown/brandClawbackAmount").asLong()).isEqualTo(24_150);
        assertThat(d.at("/settlement/breakdown/brandPayoutAmount").asLong()).isEqualTo(4_751_650);
        JsonNode row = json(sellerGet(PERF + "/group-buys")).at("/content/0");
        assertThat(row.at("/payout/value").asLong()).isEqualTo(4_775_800);

        // 합의 반영(D4) — 원래 정산액도 차감 전 기준(정산 계산기가 원래 리워드로 낸 차감 전 수취액)
        jdbc.update("UPDATE settlement SET reward_amount = 493200, reward_vat_amount = 49320, "
                + "brand_payout_amount = 4811930 WHERE settlement_id = ?", s.getId());
        JsonNode d4 = detail();
        assertThat(d4.at("/kpi/payout/value").asLong()).isEqualTo(4_836_080);
        assertThat(d4.at("/kpi/payout/original").asLong()).isEqualTo(4_775_800);
    }

    // ------------------------------------------------------------------ CO 원가 저장

    @Test
    @DisplayName("CO-05 · 06 · 08 · 09 원가 — 전체 교체(1행 유지 · null 로 비움) · 전부 null 허용 · 금액 경계 · 실패는 부분 저장 없음")
    void costReplaceAndValidation() throws Exception {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        generate(LocalDateTime.now().withNano(0));

        JsonNode first = json(put(costUrl(), costBody(10_000L, 9_000L, 2_000L, 6_000L, null, 0L)).andExpect(status().isOk()));
        LocalDateTime createdAt = jdbc.queryForObject("SELECT created_at FROM group_buy_cost WHERE group_buy_id = ?",
                LocalDateTime.class, groupBuy.getId());
        assertThat(first.at("/cost/updatedAt").isNull()).isFalse();

        JsonNode second = json(put(costUrl(), costBody(12_000L, 9_000L, null, 6_000L, null, 0L)).andExpect(status().isOk()));
        assertThat(second.at("/cost/packagingCost").isNull()).isTrue(); // 부분 수정이 아니라 교체
        assertThat(costItem(second, CREAM_NAME_PART).at("/unitCost").asLong()).isEqualTo(12_000);
        assertThat(second.at("/cost/totalCost/value").asLong()).isEqualTo(12_000 + 9_000 + 6_000);
        assertThat(count("group_buy_cost")).isEqualTo(1);
        assertThat(count("group_buy_cost_item")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT created_at FROM group_buy_cost WHERE group_buy_id = ?",
                LocalDateTime.class, groupBuy.getId())).isEqualTo(createdAt);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM group_buy_cost WHERE group_buy_id = ?", Long.class,
                groupBuy.getId())).isEqualTo(brand.seller().getId());

        // 금액 경계 — 10억 정확히 · 0 허용 / 10억 초과 · 음수 · 문자 400 · 부분 저장 없음
        put(costUrl(), costBody(1_000_000_000L, 0L, 0L, 0L, 0L, 0L)).andExpect(status().isOk());
        put(costUrl(), costBody(12_000L, 9_000L, 1_000_000_001L, 6_000L, null, 0L))
                .andExpect(status().isBadRequest()).andExpect(code("INVALID_INPUT"));
        put(costUrl(), costBody(5L, -1L, 0L, 0L, 0L, 0L))
                .andExpect(status().isBadRequest()).andExpect(code("INVALID_INPUT"));
        Map<String, Object> text = costBody(1L, 1L, 0L, 0L, 0L, 0L);
        text.put("etcCost", "abc");
        put(costUrl(), text).andExpect(status().isBadRequest()).andExpect(code("INVALID_INPUT"));
        assertThat(costItem(detail(), CREAM_NAME_PART).at("/unitCost").asLong()).isEqualTo(1_000_000_000L);

        // 전부 null — 행을 지우지 않고 값만 비운다(D1-C 로 돌아간다)
        JsonNode cleared = json(put(costUrl(), costBody(null, null, null, null, null, null)).andExpect(status().isOk()));
        assertThat(count("group_buy_cost")).isEqualTo(1);
        assertThat(cleared.at("/cost/totalCost/value").isNull()).isTrue();
        assertThat(cleared.at("/cost/profit/basis").asText()).isEqualTo("개당 원가를 입력하면 수익이 계산됩니다");
        assertThat(cleared.at("/cost/items/0/unitCost").isNull()).isTrue();
    }

    @Test
    @DisplayName("CO-07 · 10 원가 검증 순서 — 항목 불일치 5종 400 · 집계 중이면 본문이 틀려도 409 · 남의 공구는 404 · 저장 없음")
    void costGuards() throws Exception {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();

        // 집계 중 — 잠금이 본문 검증보다 먼저
        Map<String, Object> missing = costBody(1L, 1L, 0L, 0L, 0L, 0L);
        ((List<?>) missing.get("items")).remove(1);
        put(costUrl(), missing).andExpect(status().isConflict()).andExpect(code("PERFORMANCE_COST_LOCKED"));
        put(costUrl(), costBody(-1L, 1L, 0L, 0L, 0L, 0L)).andExpect(status().isConflict());
        // 남의 브랜드 — 소유 검사가 잠금보다 먼저
        String other = sellerToken(fixture.createBrand("perf-other@showroomz.test", "다른브랜드").seller());
        put(costUrl(), costBody(1L, 1L, 0L, 0L, 0L, 0L), other).andExpect(status().isNotFound())
                .andExpect(code("GROUP_BUY_NOT_FOUND"));
        assertThat(count("group_buy_cost")).isZero();

        generate(LocalDateTime.now().withNano(0));
        List<Long> ids = contractItemIds();
        List<Map<String, Object>> cases = new ArrayList<>();
        cases.add(itemsBody(List.of(item(ids.get(0), 1L)))); // 누락
        cases.add(itemsBody(List.of(item(ids.get(0), 1L), item(ids.get(1), 1L), item(999_999L, 1L)))); // 남의 항목
        cases.add(itemsBody(List.of(item(ids.get(0), 1L), item(ids.get(0), 2L)))); // 중복
        cases.add(itemsBody(List.of(item(ids.get(0), 1L), item(null, 1L)))); // id 없음
        cases.add(itemsBody(List.of())); // 빈 목록
        for (Map<String, Object> body : cases) {
            put(costUrl(), body).andExpect(status().isBadRequest()).andExpect(code("PERFORMANCE_COST_ITEM_MISMATCH"));
        }
        assertThat(count("group_buy_cost")).isZero();
        assertThat(count("group_buy_cost_item")).isZero();
    }

    @Test
    @DisplayName("CO-12 원가 격리 — 스튜디오 성과 · 파트너 목록 · 요약 · 정산 13 어디에도 원가 키가 없고 숫자도 그대로다")
    void costIsolation() throws Exception {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE settlement SET status = 'PAID' WHERE settlement_id = ?", s.getId());

        String listBefore = body(sellerGet(PERF + "/group-buys"));
        String summaryBefore = body(sellerGet(PERF + "/summary"));
        String settlementBefore = body(sellerGet("/v1/seller/settlements/" + s.getId()));
        put(costUrl(), costBody(10_000L, 9_000L, 2_000L, 6_000L, 7_777L, 0L)).andExpect(status().isOk());

        assertThat(body(sellerGet(PERF + "/group-buys"))).isEqualTo(listBefore);
        assertThat(body(sellerGet(PERF + "/summary"))).isEqualTo(summaryBefore);
        assertThat(body(sellerGet("/v1/seller/settlements/" + s.getId()))).isEqualTo(settlementBefore);
        for (String surface : List.of(body(creatorGet("/v1/creator/performance/" + groupBuy.getId())
                        .andExpect(status().isOk())),
                body(creatorGet("/v1/creator/performance/summary").andExpect(status().isOk())),
                listBefore, summaryBefore)) {
            JsonNode node = objectMapper.readTree(surface);
            assertThat(fieldNames(node)).doesNotContain("cost", "unitCost", "totalCost", "profit", "profitRate",
                    "packagingCost", "shippingCost", "returnDisposalCost", "etcCost");
            assertThat(surface).doesNotContain("7777");
        }
    }

    // ------------------------------------------------------------------ 도우미

    private String detailUrl() {
        return PERF + "/group-buys/" + groupBuy.getId();
    }

    private String costUrl() {
        return detailUrl() + "/cost";
    }

    private JsonNode detail() throws Exception {
        return json(sellerGet(detailUrl()).andExpect(status().isOk()));
    }

    private ResultActions put(String url, Object body) throws Exception {
        return put(url, body, brandToken);
    }

    private ResultActions put(String url, Object body, String token) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private static org.springframework.test.web.servlet.ResultMatcher code(String code) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(code);
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private void moveSettlement(Settlement s, String status) {
        jdbc.update("UPDATE settlement SET status = ? WHERE settlement_id = ?", status, s.getId());
    }

    /** 정산이 있고 협의 중이 아닌 상태 — 판매 · 돈 · 원가 값이 전부 FINAL. */
    private static void assertSettledFinal(JsonNode d, String status, String tone) {
        assertThat(d.at("/status").asText()).isEqualTo(status);
        assertThat(d.at("/statusTone").asText()).isEqualTo(tone);
        for (String path : List.of("/kpi/confirmedSales", "/kpi/payout", "/kpi/conversionRate", "/kpi/confirmRate",
                "/kpi/confirmedQuantity", "/kpi/aov", "/kpi/roas", "/kpi/repurchaseRate", "/contract/reward",
                "/contract/totalSpend", "/consumers/acquisitionCost", "/cost/totalCost", "/cost/profit",
                "/cost/profitRate", "/breakdown/deductionTotal", "/delivery/claimRate", "/traffic/funnel/confirmed")) {
            assertThat(d.at(path + "/state").asText()).as(path).isEqualTo("FINAL");
            assertThat(d.at(path + "/value").isNull()).as(path).isFalse();
        }
        assertThat(d.at("/aggregating").isNull()).isTrue();
        assertThat(d.at("/consumers/estimated").asBoolean()).isFalse();
        assertThat(d.at("/cost/editable").asBoolean()).isTrue();
        assertThat(d.at("/cost/lockedReason").isNull()).isTrue();
        assertThat(d.at("/settlement/status").asText()).isEqualTo(status);
    }

    private void identity(Users user, String birthday, String gender) {
        inTransaction(() -> {
            Users u = userRepository.findById(user.getId()).orElseThrow();
            u.updateIdentity(u.getName() == null ? "소비자" : u.getName(), birthday, gender, "010-1234-5678",
                    LocalDateTime.now());
            return null;
        });
    }

    private void reassign(OrderDeliveryGroup group, Users user) {
        jdbc.update("UPDATE orders SET user_id = ? WHERE order_id = (SELECT order_id FROM order_delivery_group "
                + "WHERE delivery_group_id = ?)", user.getId(), group.getId());
    }

    private void visit(String visitorKey, LocalDateTime at) {
        inTransaction(() -> showroomVisitRepository.save(new ShowroomVisit(
                creatorRepository.findById(creator.getId()).orElseThrow(), null, visitorKey,
                ShowroomVisitSource.DIRECT, at)));
    }

    private void productView(Long productId, String viewerKey, LocalDateTime at) {
        productDetailViewRepository.save(new ProductDetailView(productId, groupBuy.getId(), creator.getId(), null,
                viewerKey, ProductDetailViewSource.SHOWROOM, null, at));
    }

    private void inquiry(LocalDateTime createdAt, LocalDateTime answeredAt) {
        Long id = inTransaction(() -> productInquiryRepository.save(ProductInquiry.builder()
                .user(userRepository.findById(consumer.getId()).orElseThrow())
                .product(productRepository.findById(cream.getProductId()).orElseThrow())
                .type(ProductInquiryType.ETC).content("배송 언제 오나요").secret(false).build()).getId());
        jdbc.update("UPDATE PRODUCT_INQUIRY SET CREATED_AT = ?, ANSWERED_AT = ? WHERE PRODUCT_INQUIRY_ID = ?",
                createdAt, answeredAt, id);
    }

    private List<Long> contractItemIds() {
        return contractItemRepository.findWithProductByContractIds(List.of(groupBuy.getContract().getId())).stream()
                .map(ContractItem::getId).toList();
    }

    private static Map<String, Object> item(Long contractItemId, Long unitCost) {
        Map<String, Object> item = new HashMap<>();
        item.put("contractItemId", contractItemId);
        item.put("unitCost", unitCost);
        return item;
    }

    private static Map<String, Object> itemsBody(List<Map<String, Object>> items) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", new ArrayList<>(items));
        body.put("packagingCost", 0L);
        return body;
    }

    /** 계약 항목 순서(크림 → 세럼)대로 개당 원가 + 공구 전체 비용 4종. */
    private Map<String, Object> costBody(Long creamCost, Long serumCost, Long packaging, Long shipping, Long disposal,
                                         Long etc) {
        List<Long> ids = contractItemIds();
        Map<String, Object> body = itemsBody(List.of(item(ids.get(0), creamCost), item(ids.get(1), serumCost)));
        body.put("packagingCost", packaging);
        body.put("shippingCost", shipping);
        body.put("returnDisposalCost", disposal);
        body.put("etcCost", etc);
        return body;
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static JsonNode product(JsonNode detail, String namePart) {
        for (JsonNode row : detail.at("/sales/products")) {
            if (row.at("/productName").asText().contains(namePart)) {
                return row;
            }
        }
        throw new AssertionError("상품 행 없음: " + namePart);
    }

    private static JsonNode costItem(JsonNode detail, String namePart) {
        for (JsonNode row : detail.at("/cost/items")) {
            if (row.at("/productName").asText().contains(namePart)) {
                return row;
            }
        }
        throw new AssertionError("원가 항목 없음: " + namePart);
    }

    private static List<String> keys(JsonNode lines) {
        List<String> keys = new ArrayList<>();
        lines.forEach(line -> keys.add(line.at("/key").asText()));
        return keys;
    }

    private static void assertLine(JsonNode lines, String key, String label, long quantity, long amount, int count) {
        for (JsonNode line : lines) {
            if (key.equals(line.at("/key").asText())) {
                assertThat(line.at("/label").asText()).as(key).isEqualTo(label);
                assertThat(line.at("/quantity").asLong()).as(key + " quantity").isEqualTo(quantity);
                assertThat(line.at("/amount").asLong()).as(key + " amount").isEqualTo(amount);
                assertThat(line.at("/count").asInt()).as(key + " count").isEqualTo(count);
                return;
            }
        }
        throw new AssertionError("분해 줄 없음: " + key);
    }

    /** 성과 상세에 소비자 식별값이 없다(§47-2 분포만) — 키 이름으로 본다. */
    private static void assertNoConsumerIdentity(JsonNode detail) {
        assertThat(fieldNames(detail)).doesNotContain("userId", "consumerUserId", "phoneNumber", "birthday",
                "orderNumber", "recipientName", "email");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        collect(node, names);
        return names;
    }

    private static void collect(JsonNode node, List<String> names) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                names.add(field.getKey());
                collect(field.getValue(), names);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }
}

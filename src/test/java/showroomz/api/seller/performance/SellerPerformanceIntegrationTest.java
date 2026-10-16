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
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.CreatorFollowEvent;
import showroomz.domain.member.creator.repository.CreatorFollowEventRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImpression;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 성과 관리(12) — 48 파트너 성과 설계서 10절 PF1 ~ PF11. 정산 산식 · 분류는 정산 테스트가 소유하고 여기서는 <b>성과 응답의
 * 상태 · 분모 · 검산</b>을 본다. 시드 공구: 크림 27,200(리워드 12%) · 세럼 24,000(10%) · 고정 지급비 300,000.
 */
@DisplayName("파트너센터 성과 관리(12) — 목록 · 요약 · 상세 · 원가")
class SellerPerformanceIntegrationTest extends SettlementTestSupport {

    private static final String PERF = "/v1/seller/performance";
    private static final long FIXED_FEE = 300_000;

    @Autowired private ShowroomVisitRepository showroomVisitRepository;
    @Autowired private PostImpressionRepository postImpressionRepository;
    @Autowired private CreatorFollowEventRepository followEventRepository;
    @Autowired private ContractItemRepository contractItemRepository;

    // ------------------------------------------------------------------ 시드

    /** 크림 1 · 세럼 1 구매확정 → 공구 종료 → 정산 생성(정산 확인 중). */
    private Settlement reviewing() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(LocalDateTime.now().withNano(0));
    }

    private String detailUrl() {
        return PERF + "/group-buys/" + groupBuy.getId();
    }

    private ResultActions sellerPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private List<Long> contractItemIds() {
        return contractItemRepository.findWithProductByContractIds(List.of(groupBuy.getContract().getId())).stream()
                .map(ContractItem::getId).toList();
    }

    private Map<String, Object> costBody(Long creamCost, Long serumCost) {
        List<Long> ids = contractItemIds();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> cream = new HashMap<>();
        cream.put("contractItemId", ids.get(0));
        cream.put("unitCost", creamCost);
        Map<String, Object> serum = new HashMap<>();
        serum.put("contractItemId", ids.get(1));
        serum.put("unitCost", serumCost);
        items.add(cream);
        items.add(serum);
        Map<String, Object> body = new HashMap<>();
        body.put("items", items);
        body.put("packagingCost", 2_000L);
        body.put("shippingCost", 6_000L);
        body.put("returnDisposalCost", null);
        body.put("etcCost", 0L);
        return body;
    }

    private void visit(Users user, String visitorKey, ShowroomVisitSource source, LocalDateTime at) {
        inTransaction(() -> showroomVisitRepository.save(new ShowroomVisit(
                creatorRepository.findById(creator.getId()).orElseThrow(), user, visitorKey, source, at)));
    }

    private JsonNode detailJson() throws Exception {
        return json(sellerGet(detailUrl()).andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ PF1 모집단

    @Test
    @DisplayName("PF1 모집단 — 진행 중 · 중단 · 판매 0건 공구는 목록에 없고 상세 404 · 남의 브랜드 404")
    void population() throws Exception {
        confirmedGroup(creamVariant, 1); // 진행 중 공구 — 주문이 있어도 성과가 없다
        sellerGet(PERF + "/group-buys").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.listPeriod").doesNotExist());
        sellerGet(detailUrl()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"));

        GroupBuy zeroSales = seed(brand, creator, "판매 0건 공구", LocalDateTime.now().minusDays(9).withNano(0),
                LocalDateTime.now().minusDays(2).withNano(0));
        moveTo(zeroSales.getId(), GroupBuyStatus.ENDED);
        sellerGet(PERF + "/group-buys/" + zeroSales.getId()).andExpect(status().isNotFound());

        moveTo(groupBuy.getId(), GroupBuyStatus.SUSPENDED);
        sellerGet(detailUrl()).andExpect(status().isNotFound());

        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        sellerGet(detailUrl()).andExpect(status().isOk());
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른브랜드");
        sellerGet(detailUrl(), sellerToken(other.seller())).andExpect(status().isNotFound());
        sellerGet(PERF + "/group-buys", sellerToken(other.seller()))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ------------------------------------------------------------------ PF2 · PF4 집계 중

    @Test
    @DisplayName("PF4 상세 집계 중(D2) — 판매 PROJECTED · 돈 PENDING_SETTLEMENT · 미종결 배너 · 원가 잠금 409 · 목록 행 「—」")
    void aggregating() throws Exception {
        confirmedGroup(creamVariant, 1);
        deliveredFrom(purchase(serumVariant, 1).group()); // 배송완료 · 구매확정 전 = 미종결
        endGroupBuy();

        JsonNode d = detailJson();
        assertThat(d.at("/status").asText()).isEqualTo("AGGREGATING");
        assertThat(d.at("/statusTone").asText()).isEqualTo("NEUTRAL");
        assertThat(d.at("/aggregating/unclosedCount").asInt()).isEqualTo(1);
        assertThat(d.at("/kpi/confirmedSales/state").asText()).isEqualTo("PROJECTED");
        assertThat(d.at("/kpi/confirmedSales/value").asLong()).isEqualTo(CREAM_PRICE + SERUM_PRICE);
        assertThat(d.at("/kpi/payout/state").asText()).isEqualTo("PENDING_SETTLEMENT");
        assertThat(d.at("/kpi/payout/value").isNull()).isTrue();
        assertThat(d.at("/kpi/roas/state").asText()).isEqualTo("PENDING_SETTLEMENT");
        assertThat(d.at("/contract/reward/state").asText()).isEqualTo("PROJECTED");
        assertThat(d.at("/contract/fixedFeeAmount").asLong()).isEqualTo(FIXED_FEE);
        assertThat(d.at("/consumers/estimated").asBoolean()).isTrue();
        assertThat(d.at("/consumers/acquisitionCost/state").asText()).isEqualTo("PENDING_SETTLEMENT");
        assertThat(d.at("/cost/editable").asBoolean()).isFalse();
        assertThat(d.at("/cost/profit/state").asText()).isEqualTo("PENDING_SETTLEMENT");
        assertThat(d.at("/settlement").isNull()).isTrue();

        sellerPut(detailUrl() + "/cost", costBody(10_000L, 9_000L)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_COST_LOCKED"));

        sellerGet(PERF + "/group-buys").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("AGGREGATING"))
                .andExpect(jsonPath("$.content[0].confirmedSales.value").doesNotExist())
                .andExpect(jsonPath("$.content[0].payout.value").doesNotExist())
                .andExpect(jsonPath("$.content[0].detailAvailable").value(true));
    }

    // ------------------------------------------------------------------ PF5 확정

    @Test
    @DisplayName("PF5 상세 정산 확인 중 — 정산 값 그대로 · ROAS 분모 = 고정 지급비 + 리워드 · 검산식 · 정산 분해 13 과 동일")
    void settledDetail() throws Exception {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        cancelledBySeller(preparing(purchase(creamVariant, 1).group()));
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));

        JsonNode d = detailJson();
        long sales = s.getConfirmedSalesAmount();
        long spend = FIXED_FEE + s.getRewardAmount();
        assertThat(d.at("/status").asText()).isEqualTo("REVIEWING");
        assertThat(d.at("/statusTone").asText()).isEqualTo("NEUTRAL");
        assertThat(d.at("/kpi/confirmedSales/value").asLong()).isEqualTo(sales);
        assertThat(d.at("/kpi/confirmedSales/state").asText()).isEqualTo("FINAL");
        assertThat(d.at("/kpi/payout/value").asLong()).isEqualTo(s.getBrandPayoutAmount());
        assertThat(d.at("/kpi/payout/original").isNull()).isTrue();
        assertThat(d.at("/contract/totalSpend/value").asLong()).isEqualTo(spend);
        assertThat(d.at("/kpi/roas/value").asLong()).isEqualTo(Math.round(sales * 100.0 / spend));
        assertThat(d.at("/kpi/confirmRate/value").decimalValue()).isEqualByComparingTo("66.7");

        // 검산식 — 주문 = 구매확정 + 취소 + 반품 + 배송 예외
        JsonNode funnel = d.at("/traffic/funnel");
        assertThat(funnel.at("/orders").asInt()).isEqualTo(3);
        assertThat(funnel.at("/confirmed/value").asInt() + funnel.at("/cancelled").asInt()
                + funnel.at("/returned").asInt() + funnel.at("/deliveryException").asInt()).isEqualTo(3);
        assertThat(d.at("/breakdown/cancel/count").asInt()).isEqualTo(1);
        assertThat(d.at("/breakdown/cancel/amount").asLong()).isEqualTo(s.getCancelDeduction());
        assertThat(lineOf(d.at("/breakdown/cancel/lines"), "SELLER_DIRECT").at("/count").asInt()).isEqualTo(1);

        assertThat(d.at("/sales/products").size()).isEqualTo(2);
        assertThat(d.at("/sales/total/confirmedSales/value").asLong()).isEqualTo(sales);
        assertThat(d.at("/settlement/settlementId").asLong()).isEqualTo(s.getId());
        assertThat(d.at("/settlement/breakdown/brandPayoutAmount").asLong()).isEqualTo(s.getBrandPayoutAmount());
        assertThat(d.at("/settlement/breakdown/cancel/amount").asLong()).isEqualTo(s.getCancelDeduction());
        assertThat(d.at("/cost/editable").asBoolean()).isTrue();
        assertThat(d.at("/cost/totalCost/value").isNull()).isTrue();
        assertThat(d.at("/cost/profit/basis").asText()).contains("개당 원가를 입력하면");

        sellerGet(PERF + "/group-buys").andExpect(jsonPath("$.content[0].status").value("REVIEWING"))
                .andExpect(jsonPath("$.content[0].payout.value").value(s.getBrandPayoutAmount()))
                .andExpect(jsonPath("$.content[0].confirmedQuantity.value").value(2));
    }

    // ------------------------------------------------------------------ PF6 조정 협의 · 합의 반영

    @Test
    @DisplayName("PF6 조정 협의(D3) — 리워드에 걸린 값만 NEGOTIATING + 원래 값 · 합의 반영(D4) — FINAL + 원래 값 · 상태는 지급 완료 그대로")
    void adjustment() throws Exception {
        Settlement s = reviewing();
        jdbc.update("UPDATE settlement SET status = 'ADJUSTING' WHERE settlement_id = ?", s.getId());

        JsonNode d = detailJson();
        assertThat(d.at("/status").asText()).isEqualTo("ADJUSTING");
        assertThat(d.at("/statusTone").asText()).isEqualTo("WARNING");
        assertThat(d.at("/kpi/confirmedSales/state").asText()).isEqualTo("FINAL");
        assertThat(d.at("/kpi/payout/state").asText()).isEqualTo("NEGOTIATING");
        assertThat(d.at("/kpi/payout/original").asLong()).isEqualTo(s.getBrandPayoutAmount());
        assertThat(d.at("/kpi/roas/state").asText()).isEqualTo("NEGOTIATING");
        assertThat(d.at("/contract/reward/original").asLong()).isEqualTo(s.getOriginalRewardAmount());
        sellerGet(PERF + "/group-buys").andExpect(jsonPath("$.content[0].payout.state").value("NEGOTIATING"));

        long agreed = s.getRewardAmount() - 1_000;
        jdbc.update("UPDATE settlement SET status = 'PAID', reward_amount = ? WHERE settlement_id = ?", agreed, s.getId());
        JsonNode after = detailJson();
        long sales = s.getConfirmedSalesAmount();
        assertThat(after.at("/status").asText()).isEqualTo("PAID");
        assertThat(after.at("/statusTone").asText()).isEqualTo("SUCCESS");
        assertThat(after.at("/kpi/payout/state").asText()).isEqualTo("FINAL");
        assertThat(after.at("/kpi/payout/original").isNull()).isFalse();
        assertThat(after.at("/contract/reward/value").asLong()).isEqualTo(agreed);
        assertThat(after.at("/contract/reward/original").asLong()).isEqualTo(s.getOriginalRewardAmount());
        assertThat(after.at("/kpi/roas/value").asLong()).isEqualTo(Math.round(sales * 100.0 / (FIXED_FEE + agreed)));
        assertThat(after.at("/kpi/roas/original").asLong())
                .isEqualTo(Math.round(sales * 100.0 / (FIXED_FEE + s.getOriginalRewardAmount())));
    }

    // ------------------------------------------------------------------ PF3 요약

    @Test
    @DisplayName("PF3 요약 — 지급 완료만 합산 · 빈 상태는 null · 상태 칩은 모집단 전체")
    void summaryKpi() throws Exception {
        sellerGet(PERF + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.population.count").value(0))
                .andExpect(jsonPath("$.confirmedSales.value").doesNotExist())
                .andExpect(jsonPath("$.statusCounts.PAID").value(0));

        Settlement s = reviewing();
        sellerGet(PERF + "/summary")
                .andExpect(jsonPath("$.population.count").value(0))
                .andExpect(jsonPath("$.statusCounts.REVIEWING").value(1));

        jdbc.update("UPDATE settlement SET status = 'PAYOUT_FAILED' WHERE settlement_id = ?", s.getId());
        sellerGet(PERF + "/summary")
                .andExpect(jsonPath("$.population.count").value(1))
                .andExpect(jsonPath("$.statusCounts.PAID").value(1))
                .andExpect(jsonPath("$.confirmedSales.value").value(s.getConfirmedSalesAmount()))
                .andExpect(jsonPath("$.avgConfirmRate.value").value(100.0))
                .andExpect(jsonPath("$.avgRoas.value").value(
                        Math.round(s.getConfirmedSalesAmount() * 100.0 / (FIXED_FEE + s.getRewardAmount()))));
        sellerGet(PERF + "/group-buys?status=PAID").andExpect(jsonPath("$.content.length()").value(1));
        sellerGet(PERF + "/group-buys?status=AGGREGATING").andExpect(jsonPath("$.content.length()").value(0));
    }

    // ------------------------------------------------------------------ PF7 유입

    @Test
    @DisplayName("PF7 유입 — 고유 방문자 · 사람당 첫 방문 소스(합 = 방문 명) · 기간 밖 제외 · 게시물 조회 · 고유 · 전환율 분모")
    void trafficBlock() throws Exception {
        var post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        LocalDateTime inside = groupBuy.getStartAt().plusHours(2);
        visit(consumer, "u:" + consumer.getId(), ShowroomVisitSource.INSTAGRAM_LINK, inside);
        visit(consumer, "u:" + consumer.getId(), ShowroomVisitSource.GROUP_BUY_POST, inside.plusHours(1)); // 같은 사람
        visit(null, "d:device-1", ShowroomVisitSource.PRODUCT_DIRECT_LINK, inside);
        visit(null, "d:device-2", ShowroomVisitSource.DIRECT, inside);
        visit(null, "d:device-3", ShowroomVisitSource.APP_SEARCH, groupBuy.getStartAt().minusHours(1)); // 기간 밖
        inTransaction(() -> {
            Post p = postRepository.findById(post.getPostId()).orElseThrow();
            postImpressionRepository.save(new PostImpression(p, creator.getId(), null, "d:device-1", inside));
            postImpressionRepository.save(new PostImpression(p, creator.getId(), null, "d:device-1", inside.plusHours(2)));
            postImpressionRepository.save(new PostImpression(p, creator.getId(), null, "d:device-9", inside));
            return null;
        });
        reviewing();

        JsonNode t = detailJson().at("/traffic");
        assertThat(t.at("/visitorCount").asLong()).isEqualTo(3);
        assertThat(t.at("/visitCount").asLong()).isEqualTo(4);
        assertThat(sourceCount(t, "PROFILE_LINK")).isEqualTo(1);
        assertThat(sourceCount(t, "POST_LINK")).isEqualTo(0); // 첫 방문 소스만
        assertThat(sourceCount(t, "PRODUCT_LINK")).isEqualTo(1);
        assertThat(sourceCount(t, "INTERNAL")).isEqualTo(1);
        long sum = 0;
        for (JsonNode source : t.at("/sources")) {
            sum += source.at("/count").asLong();
        }
        assertThat(sum).isEqualTo(3);
        assertThat(t.at("/post/impressionCount").asLong()).isEqualTo(3);
        assertThat(t.at("/post/uniqueViewerCount").asLong()).isEqualTo(2);
        assertThat(t.at("/post/shareCount/state").asText()).isEqualTo("NOT_AVAILABLE");
        // 전환율 = 구매확정 2건 ÷ 방문 3명
        assertThat(t.at("/funnel/conversionRate/value").decimalValue()).isEqualByComparingTo("66.7");
        assertThat(t.at("/limits").size()).isGreaterThan(0);
    }

    // ------------------------------------------------------------------ PF8 소비자

    @Test
    @DisplayName("PF8 소비자 — 건 ≠ 명 · 성별 미확인 분모 제외 · 5명 미만 구간 「기타」 · 결제 시각 팔로워 비중")
    void consumerBlock() throws Exception {
        inTransaction(() -> {
            Users u = userRepository.findById(consumer.getId()).orElseThrow();
            u.updateIdentity("김수민", "1998-03-02", "FEMALE", "010-1234-5678", LocalDateTime.now());
            return null;
        });
        followEventRepository.save(CreatorFollowEvent.follow(creator.getId(), consumer.getId(),
                LocalDateTime.now().minusDays(30), null));
        reviewing(); // 같은 소비자의 주문 2건

        JsonNode c = detailJson().at("/consumers");
        assertThat(c.at("/confirmedOrderCount").asInt()).isEqualTo(2);
        assertThat(c.at("/confirmedBuyerCount").asInt()).isEqualTo(1);
        assertThat(c.at("/gender/female").asLong()).isEqualTo(1);
        assertThat(c.at("/gender/femaleRatio").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(c.at("/followerShare/value").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(c.at("/repurchaseBuyerCount/value").asLong()).isEqualTo(0);
        assertThat(c.at("/newBuyerCount/value").asLong()).isEqualTo(1);
        JsonNode bands = c.at("/ageBands");
        assertThat(bands.size()).isEqualTo(1);
        assertThat(bands.get(0).at("/band").asText()).isEqualTo("OTHER"); // 20대 1명 — 5명 미만이라 합산
        assertThat(bands.get(0).at("/merged").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------ PF10 원가

    @Test
    @DisplayName("PF10 원가 — 미입력(D1-C) · 저장(D1-D) 수익 = 정산액 − 총 원가 − 고정 지급비 · 전체 교체 · 항목 불일치 · 음수 400")
    void costInput() throws Exception {
        Settlement s = reviewing();

        Map<String, Object> partial = costBody(10_000L, null);
        JsonNode d1c = json(sellerPut(detailUrl() + "/cost", partial).andExpect(status().isOk()));
        assertThat(d1c.at("/cost/totalCost/value").isNull()).isTrue();
        assertThat(d1c.at("/cost/items/0/unitCost").asLong()).isEqualTo(10_000);

        JsonNode d1d = json(sellerPut(detailUrl() + "/cost", costBody(10_000L, 9_000L)).andExpect(status().isOk()));
        long total = 10_000 + 9_000 + 2_000 + 6_000;
        long profit = s.getBrandPayoutAmount() - total - FIXED_FEE;
        assertThat(d1d.at("/cost/totalCost/value").asLong()).isEqualTo(total);
        assertThat(d1d.at("/cost/profit/value").asLong()).isEqualTo(profit);
        assertThat(d1d.at("/cost/items/1/subtotal").asLong()).isEqualTo(9_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_buy_cost_item", Long.class)).isEqualTo(2);

        Map<String, Object> missing = costBody(1L, 1L);
        ((List<?>) missing.get("items")).remove(1);
        sellerPut(detailUrl() + "/cost", missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_COST_ITEM_MISMATCH"));
        sellerPut(detailUrl() + "/cost", costBody(-1L, 1L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // 실패한 요청은 저장값을 바꾸지 않는다
        assertThat(detailJson().at("/cost/totalCost/value").asLong()).isEqualTo(total);
    }

    // ------------------------------------------------------------------ PF2 목록

    @Test
    @DisplayName("PF2 목록 — 키워드 · 거래액 정렬에서 집계 중 행은 맨 뒤 · 조회기간 · size 상한")
    void listing() throws Exception {
        Settlement first = reviewing();
        GroupBuy settledGroupBuy = groupBuy;
        openNextGroupBuy(creator);
        confirmedGroup(creamVariant, 1);
        endGroupBuy(); // 두 번째 공구는 집계 중

        sellerGet(PERF + "/group-buys?sort=SALES_DESC").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].groupBuyId").value(settledGroupBuy.getId()))
                .andExpect(jsonPath("$.content[1].status").value("AGGREGATING"))
                .andExpect(jsonPath("$.listPeriod.from").exists());
        sellerGet(PERF + "/group-buys?keyword=앵콜").andExpect(jsonPath("$.content.length()").value(2));
        sellerGet(PERF + "/group-buys?keyword=없는공구").andExpect(jsonPath("$.content.length()").value(0));
        sellerGet(PERF + "/group-buys?status=REVIEWING")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].payout.value").value(first.getBrandPayoutAmount()));
        sellerGet(PERF + "/group-buys?size=101").andExpect(status().isBadRequest());
    }

    private static long sourceCount(JsonNode traffic, String source) {
        for (JsonNode node : traffic.at("/sources")) {
            if (source.equals(node.at("/source").asText())) {
                return node.at("/count").asLong();
            }
        }
        throw new AssertionError("source 없음: " + source);
    }

    private static JsonNode lineOf(JsonNode lines, String key) {
        for (JsonNode line : lines) {
            if (key.equals(line.at("/key").asText())) {
                return line;
            }
        }
        throw new AssertionError("line 없음: " + key);
    }
}

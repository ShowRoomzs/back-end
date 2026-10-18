package showroomz.api.seller.performance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 성과 관리(12) — 권한 · 모집단 · 목록 · 요약(48 파트너 성과 테스트 시나리오 2절 · 4절 · 5절).
 *
 * <p>목록 시드는 같은 브랜드의 종료 공구 4건이다 — GB1 정산 확인 중 · GB2 지급 완료(다른 인플루언서) · GB3 집계 중 · GB4 분배
 * 실패(= 지급 완료). 종료 시각은 SQL 로 벌려 정렬을 고정한다.
 */
@DisplayName("파트너센터 성과 관리(12) — 권한 · 모집단 · 목록 · 요약")
class SellerPerformanceListIntegrationTest extends SettlementTestSupport {

    private static final String PERF = "/v1/seller/performance";
    private static final long FIXED_FEE = 300_000;

    @Autowired private ShowroomVisitRepository showroomVisitRepository;

    // ------------------------------------------------------------------ AU 권한

    @Test
    @DisplayName("AU-01 ~ 04 · 06 권한 — 토큰 없음 401 · 소비자 · 운영자 403 · 인플루언서 404 SELLER_NOT_FOUND · 마켓 없는 셀러 404 · id 형식 400")
    void access() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        generate(LocalDateTime.now().withNano(0));
        String detail = PERF + "/group-buys/" + groupBuy.getId();
        List<String> reads = List.of(PERF + "/group-buys", PERF + "/summary", detail);

        for (String url : reads) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken)).andExpect(status().isForbidden());
            mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, adminToken)).andExpect(status().isForbidden());
            // 보안 설정이 /v1/seller/** 를 CREATOR 에 연다 — 셀러 조회에서 막힌다(시나리오 14절 #9 · 현재 동작 고정)
            creatorGet(url).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
        }
        mockMvc.perform(put(detail + "/cost").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(detail + "/cost").header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        Seller noMarket = fixture.createAdmin("perf-no-market@showroomz.test", "마켓없음");
        String noMarketToken = bearerToken(noMarket.getEmail(), RoleType.SELLER, noMarket.getId());
        sellerGet(PERF + "/summary", noMarketToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MARKET_NOT_FOUND"));

        sellerGet(PERF + "/group-buys/abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        sellerGet(PERF + "/group-buys/999999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_buy_cost", Long.class)).isZero();
    }

    @Test
    @DisplayName("AU-05 다른 브랜드 — 상세 · 원가 PUT 404(403 아님 · 존재를 알리지 않는다) · 목록 0건 · 요약 모집단 0 · 칩 전부 0")
    void otherBrand() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        generate(LocalDateTime.now().withNano(0));
        String other = sellerToken(fixture.createBrand("perf-other@showroomz.test", "다른브랜드").seller());

        sellerGet(PERF + "/group-buys/" + groupBuy.getId(), other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"));
        mockMvc.perform(put(PERF + "/group-buys/" + groupBuy.getId() + "/cost").header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"));
        sellerGet(PERF + "/group-buys", other).andExpect(jsonPath("$.content.length()").value(0));
        JsonNode summary = json(sellerGet(PERF + "/summary", other).andExpect(status().isOk()));
        assertThat(summary.at("/population/count").asLong()).isZero();
        summary.at("/statusCounts").forEach(count -> assertThat(count.asLong()).isZero());
    }

    // ------------------------------------------------------------------ PF1 · PO 모집단

    @Test
    @DisplayName("PF1 ② · PO-02 · 03 모집단 — 결제 주문이 있어도 종료 전 · 중단 상태는 404 · 종료 · 정산완료만 열린다(조회 시점 상태로 판정)")
    void populationByGroupBuyStatus() throws Exception {
        confirmedGroup(creamVariant, 1);
        String detail = PERF + "/group-buys/" + groupBuy.getId();
        for (GroupBuyStatus status : List.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY, GroupBuyStatus.IN_PROGRESS,
                GroupBuyStatus.SUSPENSION_SCHEDULED, GroupBuyStatus.SUSPENDED)) {
            moveTo(groupBuy.getId(), status);
            sellerGet(detail).andExpect(status().isNotFound());
            sellerGet(PERF + "/group-buys").andExpect(jsonPath("$.content.length()").value(0));
        }
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        sellerGet(detail).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGGREGATING"));

        Settlement s = generate(LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE settlement SET status = 'PAID' WHERE settlement_id = ?", s.getId());
        moveTo(groupBuy.getId(), GroupBuyStatus.SETTLED);
        sellerGet(detail).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.statusLabel").value("지급 완료"))
                .andExpect(jsonPath("$.statusTone").value("SUCCESS"));
        sellerGet(PERF + "/summary").andExpect(jsonPath("$.population.count").value(1));
    }

    @Test
    @DisplayName("PF1 ③ · PO-01 모집단 — 결제 전 주문만 있는 종료 공구는 404 · 결제 후 전량 취소된 종료 공구는 모집단 안(확정 거래액 0 · 소비자 0)")
    void populationByPayment() throws Exception {
        placeCardOrder(creamVariant, 1); // 결제 전(PENDING)
        endGroupBuy();
        sellerGet(PERF + "/group-buys/" + groupBuy.getId()).andExpect(status().isNotFound());

        openNextGroupBuy(creator);
        cancelledBySeller(preparing(purchase(creamVariant, 1).group()));
        cancel(purchase(serumVariant, 1).orderId()).andExpect(status().isOk());
        endGroupBuy();

        JsonNode d = json(sellerGet(PERF + "/group-buys/" + groupBuy.getId()).andExpect(status().isOk()));
        assertThat(d.at("/kpi/confirmedSales/value").asLong()).isZero(); // 측정된 0 — null 이 아니다
        assertThat(d.at("/kpi/confirmedSales/value").isNull()).isFalse();
        assertThat(d.at("/kpi/confirmRate/value").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(d.at("/kpi/aov/value").isNull()).isTrue();
        assertThat(d.at("/kpi/conversionRate/value").isNull()).isTrue(); // 방문 0
        assertThat(d.at("/kpi/repurchaseRate/value").isNull()).isTrue();
        assertThat(d.at("/consumers/confirmedBuyerCount").asInt()).isZero();
        assertThat(d.at("/consumers/ageBands").size()).isZero();
        assertThat(d.at("/traffic/funnel/cancelled").asInt()).isEqualTo(2);
        assertThat(d.at("/breakdown/cancel/count").asInt()).isEqualTo(2);
        sellerGet(PERF + "/group-buys").andExpect(jsonPath("$.content.length()").value(1));
    }

    // ------------------------------------------------------------------ LS 목록

    /** GB1 ~ GB4 — 시나리오 4절 시드. */
    private record Seeded(GroupBuy gb1, GroupBuy gb2, GroupBuy gb3, GroupBuy gb4, Settlement s1, Settlement s2,
                          Settlement s4, Creator otherCreator) {
    }

    private Seeded seedFour(boolean withVisitors) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Users female = createConsumer("perf-f", "소비자F");
        Users male = createConsumer("perf-m", "소비자M");
        identity(consumer, "FEMALE");
        identity(female, "FEMALE");
        identity(male, "MALE");

        // GB1 — 정산 확인 중 · 공구명 「봄 한정 크림 공구」
        GroupBuy gb1 = groupBuy;
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s1 = generate(now);
        jdbc.update("UPDATE contract SET title = '봄 한정 크림 공구' WHERE contract_id = ?", gb1.getContract().getId());

        // GB2 — 지급 완료 · 다른 인플루언서 · 구매확정 2건(소비자 2명 · 둘 다 여성)
        Creator otherCreator = createCreator("다른쇼룸_하나", "hana");
        GroupBuy gb2 = openNextGroupBuy(otherCreator);
        confirmedGroup(creamVariant, 1);
        reassign(confirmedGroup(creamVariant, 1), female);
        endGroupBuy();
        Settlement s2 = generate(now);
        jdbc.update("UPDATE settlement SET status = 'PAID' WHERE settlement_id = ?", s2.getId());

        // GB3 — 집계 중(정산 없음)
        GroupBuy gb3 = openNextGroupBuy(creator);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();

        // GB4 — 분배 실패(= 지급 완료) · 세럼 구매확정 1(남성) + 직권 취소 3
        GroupBuy gb4 = openNextGroupBuy(creator);
        reassign(confirmedGroup(serumVariant, 1), male);
        for (int i = 0; i < 3; i++) {
            cancelledBySeller(preparing(purchase(creamVariant, 1).group()));
        }
        endGroupBuy();
        Settlement s4 = generate(now);
        jdbc.update("UPDATE settlement SET status = 'PAYOUT_FAILED' WHERE settlement_id = ?", s4.getId());

        // 종료 시각 — GB1 > GB2 = GB4 > GB3 (GB2 · GB4 동률은 id 내림차순)
        endedAt(gb1, now.minusDays(1));
        endedAt(gb2, now.minusDays(2));
        endedAt(gb4, now.minusDays(2));
        endedAt(gb3, now.minusHours(60));

        if (withVisitors) {
            for (int i = 0; i < 10; i++) {
                visit(otherCreator, "d:gb2-" + i, reload(gb2.getId()).getStartAt().plusHours(1));
            }
            for (int i = 0; i < 90; i++) {
                visit(creator, "d:gb4-" + i, reload(gb4.getId()).getStartAt().plusHours(1));
            }
        }
        return new Seeded(gb1, gb2, gb3, gb4, s1, s2, s4, otherCreator);
    }

    @Test
    @DisplayName("PF2 · LS-01 ~ 07 목록 — 종료일순(동률 id) · 거래액순(집계 중 맨 뒤) · 상태 필터 · 검색 · 페이지 · 조회기간 · 행 값 규칙")
    void listing() throws Exception {
        Seeded s = seedFour(false);

        // LS-01 기본 정렬 = 종료일 최신순 · 동률은 id 내림차순
        assertThat(ids(PERF + "/group-buys")).containsExactly(s.gb1().getId(), s.gb4().getId(), s.gb2().getId(),
                s.gb3().getId());
        assertThat(ids(PERF + "/group-buys?sort=ENDED_DESC")).isEqualTo(ids(PERF + "/group-buys"));
        // LS-02 거래액순 — 54,400 > 27,200 > 24,000 · 집계 중 맨 뒤
        assertThat(ids(PERF + "/group-buys?sort=SALES_DESC")).containsExactly(s.gb2().getId(), s.gb1().getId(),
                s.gb4().getId(), s.gb3().getId());
        sellerGet(PERF + "/group-buys?sort=FOO").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // LS-03 상태 필터
        assertThat(ids(PERF + "/group-buys?status=AGGREGATING")).containsExactly(s.gb3().getId());
        assertThat(ids(PERF + "/group-buys?status=PAID")).containsExactly(s.gb4().getId(), s.gb2().getId());
        assertThat(ids(PERF + "/group-buys?status=REVIEWING&status=ADJUSTING")).containsExactly(s.gb1().getId());
        assertThat(ids(PERF + "/group-buys?status=AGGREGATING&status=REVIEWING&status=ADJUSTING"
                + "&status=PAYOUT_SCHEDULED&status=PAID")).isEqualTo(ids(PERF + "/group-buys"));
        sellerGet(PERF + "/group-buys?status=FOO").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // LS-04 검색 — 공구명 · 쇼룸명 부분 일치 · trim · 빈 값은 전체
        assertThat(ids(PERF + "/group-buys?keyword=봄 한정")).containsExactly(s.gb1().getId());
        assertThat(ids(PERF + "/group-buys?keyword=  봄 한정  ")).containsExactly(s.gb1().getId());
        assertThat(ids(PERF + "/group-buys?keyword=하나")).containsExactly(s.gb2().getId());
        assertThat(ids(PERF + "/group-buys?keyword=")).hasSize(4);
        assertThat(ids(PERF + "/group-buys?keyword=없는공구")).isEmpty();
        String number = jdbc.queryForObject("SELECT group_buy_number FROM group_buy WHERE group_buy_id = ?",
                String.class, s.gb1().getId());
        assertThat(ids(PERF + "/group-buys?keyword=" + number)).isEmpty(); // 공구번호는 검색 대상이 아니다

        // LS-05 페이지
        JsonNode page1 = json(sellerGet(PERF + "/group-buys?size=3").andExpect(status().isOk()));
        assertThat(page1.at("/content").size()).isEqualTo(3);
        assertThat(page1.at("/pageInfo/totalResults").asLong()).isEqualTo(4);
        assertThat(page1.at("/pageInfo/hasNext").asBoolean()).isTrue();
        assertThat(ids(PERF + "/group-buys?size=3&page=2")).containsExactly(s.gb3().getId());
        assertThat(ids(PERF + "/group-buys?size=3&page=0")).isEqualTo(ids(PERF + "/group-buys?size=3&page=1"));
        sellerGet(PERF + "/group-buys?size=100").andExpect(status().isOk());
        sellerGet(PERF + "/group-buys?size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(json(sellerGet(PERF + "/group-buys")).at("/pageInfo/limit").asInt()).isEqualTo(20);

        // LS-06 조회기간 — 필터를 통과한 전체 · 날짜 · 0건이면 없음
        JsonNode all = json(sellerGet(PERF + "/group-buys?size=1"));
        assertThat(all.at("/listPeriod/from").asText()).isEqualTo(minStart().toString());
        assertThat(all.at("/listPeriod/to").asText()).isEqualTo(LocalDate.now().minusDays(1).toString());
        JsonNode aggregating = json(sellerGet(PERF + "/group-buys?status=AGGREGATING"));
        assertThat(aggregating.at("/listPeriod/to").asText())
                .isEqualTo(LocalDateTime.now().minusHours(60).toLocalDate().toString());
        sellerGet(PERF + "/group-buys?keyword=없는공구").andExpect(jsonPath("$.listPeriod").doesNotExist());

        // PF2 · LS-07 행 값 — 집계 중은 여섯 Figure 전부 「—」(PROJECTED) · 나머지 FINAL · 상세 열람 가능
        JsonNode rows = json(sellerGet(PERF + "/group-buys")).at("/content");
        JsonNode gb3Row = row(rows, s.gb3().getId());
        for (String figure : List.of("visitors", "conversionRate", "confirmedSales", "payout", "confirmedQuantity",
                "confirmRate")) {
            assertThat(gb3Row.at("/" + figure + "/value").isNull()).as(figure).isTrue();
            assertThat(gb3Row.at("/" + figure + "/state").asText()).as(figure).isEqualTo("PROJECTED");
        }
        assertThat(gb3Row.at("/detailAvailable").asBoolean()).isTrue();
        JsonNode gb1Row = row(rows, s.gb1().getId());
        assertThat(gb1Row.at("/groupBuyTitle").asText()).isEqualTo("봄 한정 크림 공구");
        assertThat(gb1Row.at("/influencer/showroomName").asText()).isEqualTo("글로우_지민");
        assertThat(gb1Row.at("/statusLabel").asText()).isEqualTo("정산 확인 중");
        assertThat(gb1Row.at("/payout/state").asText()).isEqualTo("FINAL");
        assertThat(gb1Row.at("/payout/value").asLong()).isEqualTo(s.s1().getBrandPayoutAmount());
        assertThat(gb1Row.has("returnRate")).isFalse(); // 기간 · 반품취소율 열은 없다(§48-1)
        assertThat(row(rows, s.gb4().getId()).at("/status").asText()).isEqualTo("PAID");

        // 조정 협의 행 — 정산액만 「협의 중」 + 원래 값
        jdbc.update("UPDATE settlement SET status = 'ADJUSTING' WHERE settlement_id = ?", s.s1().getId());
        JsonNode adjusting = row(json(sellerGet(PERF + "/group-buys")).at("/content"), s.gb1().getId());
        assertThat(adjusting.at("/payout/state").asText()).isEqualTo("NEGOTIATING");
        assertThat(adjusting.at("/payout/value").isNull()).isTrue();
        assertThat(adjusting.at("/payout/original").asLong()).isEqualTo(s.s1().getBrandPayoutAmount());
        assertThat(adjusting.at("/confirmedSales/state").asText()).isEqualTo("FINAL");
        assertThat(adjusting.at("/statusTone").asText()).isEqualTo("WARNING");
    }

    // ------------------------------------------------------------------ SM 요약

    @Test
    @DisplayName("PF3 · SM-02 · 03 · 05 · 06 · 08 · 09 요약 — 지급 완료만 합산 · 합산 비율(평균의 평균 아님) · 성비 · 방문 누적 · 목록 합과 일치 · 필터 무관")
    void summaryRatios() throws Exception {
        Seeded s = seedFour(true);

        JsonNode summary = json(sellerGet(PERF + "/summary").andExpect(status().isOk()));
        assertThat(summary.at("/population/status").asText()).isEqualTo("PAID");
        assertThat(summary.at("/population/count").asLong()).isEqualTo(2); // GB2 지급 완료 + GB4 분배 실패
        assertThat(summary.at("/statusCounts/AGGREGATING").asLong()).isEqualTo(1);
        assertThat(summary.at("/statusCounts/REVIEWING").asLong()).isEqualTo(1);
        assertThat(summary.at("/statusCounts/ADJUSTING").asLong()).isZero();
        assertThat(summary.at("/statusCounts/PAYOUT_SCHEDULED").asLong()).isZero();
        assertThat(summary.at("/statusCounts/PAID").asLong()).isEqualTo(2);

        long sales = s.s2().getConfirmedSalesAmount() + s.s4().getConfirmedSalesAmount();
        assertThat(sales).isEqualTo(2L * CREAM_PRICE + 24_000);
        assertThat(summary.at("/confirmedSales/value").asLong()).isEqualTo(sales);
        assertThat(summary.at("/confirmedSales/basis").asText()).isEqualTo("지급 완료 2건 합산");
        // 합산 비율 — 전환율 3 ÷ 100(평균의 평균이면 10.6) · 구매확정률 3 ÷ 6(62.5 아님) · AOV 78,400 ÷ 3(25,600 아님)
        assertThat(summary.at("/visitors/value").asLong()).isEqualTo(100);
        assertThat(summary.at("/visitors/basis").asText()).isEqualTo("2건 누적 · 고유 방문");
        assertThat(summary.at("/avgConversionRate/value").decimalValue()).isEqualByComparingTo("3.0");
        assertThat(summary.at("/avgConversionRate/basis").asText()).isEqualTo("방문 100명 대비 구매확정 3건");
        assertThat(summary.at("/avgConfirmRate/value").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(summary.at("/avgConfirmRate/basis").asText()).isEqualTo("주문 6건 중 3건 확정");
        assertThat(summary.at("/avgAov/value").asLong()).isEqualTo(26_133);
        assertThat(summary.at("/avgAov/basis").asText()).isEqualTo("확정 거래액 ÷ 확정 주문 3건");
        long spend = 2 * FIXED_FEE + s.s2().getRewardAmount() + s.s4().getRewardAmount();
        assertThat(summary.at("/avgRoas/value").asLong()).isEqualTo(Math.round(sales * 100.0 / spend));
        assertThat(summary.at("/avgRoas/basis").asText()).isEqualTo("확정 거래액 ÷ 총 집행비 %,d원".formatted(spend));
        // 성비 — 여 2 · 남 1 → 67 / 33(합 100) · 재구매 0
        assertThat(summary.at("/genderRatio/female").asInt()).isEqualTo(67);
        assertThat(summary.at("/genderRatio/male").asInt()).isEqualTo(33);
        assertThat(summary.at("/avgRepurchaseRate/value").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(summary.at("/avgRepurchaseRate/basis").asText()).isEqualTo("구매확정 3명 중 재구매 0명");

        // SM-09 목록 대조 · SM-05 목록 필터는 요약을 바꾸지 않는다
        JsonNode paidRows = json(sellerGet(PERF + "/group-buys?status=PAID&keyword=앵콜")).at("/content");
        long rowSales = 0, rowVisitors = 0;
        for (JsonNode row : paidRows) {
            rowSales += row.at("/confirmedSales/value").asLong();
            rowVisitors += row.at("/visitors/value").asLong();
        }
        assertThat(rowSales).isEqualTo(sales);
        assertThat(rowVisitors).isEqualTo(100);
        assertThat(json(sellerGet(PERF + "/summary"))).isEqualTo(summary);
    }

    @Test
    @DisplayName("SM-01 · 04 · 06 요약 경계 — 빈 상태 null · 집행비 0 공구는 ROAS 분자 · 분모 모두 제외(거래액 합에는 든다) · 그것뿐이면 null · 성별 미확인만이면 null")
    void summaryEdges() throws Exception {
        JsonNode empty = json(sellerGet(PERF + "/summary").andExpect(status().isOk()));
        assertThat(empty.at("/population/count").asLong()).isZero();
        for (String figure : List.of("confirmedSales", "visitors", "avgAov", "avgConversionRate", "avgRoas",
                "avgConfirmRate", "avgRepurchaseRate")) {
            assertThat(empty.at("/" + figure + "/value").isNull()).as(figure).isTrue();
            assertThat(empty.at("/" + figure + "/basis").asText()).as(figure).isEqualTo("집계된 공구 없음");
        }
        assertThat(empty.at("/genderRatio/female").isNull()).isTrue();
        assertThat(empty.at("/statusCounts").size()).isEqualTo(5);

        // 집행비 0 공구(고정 지급비 없음 · 리워드 0) — 성별 미확인 소비자
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement zero = generate(LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE contract SET fixed_fee_amount = NULL WHERE contract_id = ?", groupBuy.getContract().getId());
        jdbc.update("UPDATE settlement SET status = 'PAID', reward_amount = 0, original_reward_amount = 0 "
                + "WHERE settlement_id = ?", zero.getId());
        JsonNode onlyZero = json(sellerGet(PERF + "/summary"));
        assertThat(onlyZero.at("/population/count").asLong()).isEqualTo(1);
        assertThat(onlyZero.at("/confirmedSales/value").asLong()).isEqualTo(zero.getConfirmedSalesAmount());
        assertThat(onlyZero.at("/avgRoas/value").isNull()).isTrue();
        assertThat(onlyZero.at("/genderRatio/female").isNull()).isTrue();
        JsonNode zeroDetail = json(sellerGet(PERF + "/group-buys/" + groupBuy.getId()));
        assertThat(zeroDetail.at("/contract/fixedFeeAmount").isNull()).isTrue(); // HB-03
        assertThat(zeroDetail.at("/kpi/roas/value").isNull()).isTrue();
        assertThat(zeroDetail.at("/kpi/roas/basis").asText()).isEqualTo("고정 지급비 · 리워드가 모두 0원");
        assertThat(zeroDetail.at("/consumers/acquisitionCost/value").asLong()).isZero(); // 0 ÷ 신규 1 = 측정된 0

        // 정상 공구 하나 추가 — ROAS 는 그 공구로만 · 거래액 합은 둘 다
        openNextGroupBuy(creator);
        confirmedGroup(creamVariant, 2);
        endGroupBuy();
        Settlement normal = generate(LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE settlement SET status = 'PAID' WHERE settlement_id = ?", normal.getId());
        long spend = FIXED_FEE + normal.getRewardAmount();
        JsonNode both = json(sellerGet(PERF + "/summary"));
        assertThat(both.at("/confirmedSales/value").asLong())
                .isEqualTo(zero.getConfirmedSalesAmount() + normal.getConfirmedSalesAmount());
        assertThat(both.at("/avgRoas/value").asLong())
                .isEqualTo(Math.round(normal.getConfirmedSalesAmount() * 100.0 / spend));
        assertThat(both.at("/avgRoas/basis").asText()).isEqualTo("확정 거래액 ÷ 총 집행비 %,d원".formatted(spend));
    }

    // ------------------------------------------------------------------ 도우미

    private List<Long> ids(String url) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(sellerGet(url).andExpect(status().isOk())).at("/content")
                .forEach(row -> ids.add(row.at("/groupBuyId").asLong()));
        return ids;
    }

    private static JsonNode row(JsonNode rows, Long groupBuyId) {
        for (JsonNode row : rows) {
            if (row.at("/groupBuyId").asLong() == groupBuyId) {
                return row;
            }
        }
        throw new AssertionError("행 없음: " + groupBuyId);
    }

    private LocalDate minStart() {
        return jdbc.queryForObject("SELECT MIN(start_at) FROM group_buy WHERE market_id = ? AND status = 'ENDED'",
                LocalDateTime.class, brand.marketId()).toLocalDate();
    }

    private void endedAt(GroupBuy groupBuy, LocalDateTime at) {
        jdbc.update("UPDATE group_buy SET ended_at = ? WHERE group_buy_id = ?", at, groupBuy.getId());
    }

    private void identity(Users user, String gender) {
        inTransaction(() -> {
            Users u = userRepository.findById(user.getId()).orElseThrow();
            u.updateIdentity(u.getName() == null ? "소비자" : u.getName(), null, gender, "010-1234-5678",
                    LocalDateTime.now());
            return null;
        });
    }

    private void reassign(OrderDeliveryGroup group, Users user) {
        jdbc.update("UPDATE orders SET user_id = ? WHERE order_id = (SELECT order_id FROM order_delivery_group "
                + "WHERE delivery_group_id = ?)", user.getId(), group.getId());
    }

    private void visit(Creator showroom, String visitorKey, LocalDateTime at) {
        inTransaction(() -> showroomVisitRepository.save(new ShowroomVisit(
                creatorRepository.findById(showroom.getId()).orElseThrow(), null, visitorKey,
                ShowroomVisitSource.DIRECT, at)));
    }
}

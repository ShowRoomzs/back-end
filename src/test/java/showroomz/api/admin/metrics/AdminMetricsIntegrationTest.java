package showroomz.api.admin.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.metrics.service.AdminMetricsQueryService;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 운영 지표(1017 설계서 7절) — 기간 규칙 · 거래액(결제 시점 · 취소 포함) · 비율의 분자/분모 · 증감 방향 · 탭 배지 · 5탭 전부 조회.
 */
class AdminMetricsIntegrationTest extends SettlementTestSupport {

    private static final String METRICS = "/v1/admin/metrics";

    @Autowired private AdminMetricsQueryService metricsQueryService;

    /** 지표는 같은 기간을 10분 캐시한다 — 테스트마다 데이터가 다르므로 비운다. */
    @BeforeEach
    void clearMetricsCache() {
        ((Map<?, ?>) ReflectionTestUtils.getField(metricsQueryService, "cache")).clear();
    }

    @Test
    @DisplayName("M-01 5탭 × 프리셋 — 전부 조회된다(원천 쿼리 · 차트 · 분해 표)")
    void allTabsAndPresets() throws Exception {
        purchase(creamVariant, 1);
        for (String preset : new String[]{"TODAY", "D7", "M1", "M6"}) {
            for (String tab : new String[]{"CORE", "SUPPLY", "GROUP_BUY", "DEMAND", "OPERATION"}) {
                JsonNode body = json(adminGet(METRICS + "?preset=" + preset + "&tab=" + tab).andExpect(status().isOk()));
                assertThat(body.get("rows")).as(preset + "/" + tab).isNotEmpty();
                assertThat(body.at("/chart/points")).as(preset + "/" + tab).isNotEmpty();
                assertThat(body.at("/breakdown/columns")).as(preset + "/" + tab).isNotEmpty();
            }
        }
        LocalDate today = LocalDate.now();
        JsonNode custom = json(adminGet(METRICS + "?preset=CUSTOM&from=" + today.minusDays(120) + "&to=" + today)
                .andExpect(status().isOk()));
        assertThat(custom.at("/chart/unit").asText()).isEqualTo("MONTH");
        adminGet(METRICS + "?preset=CUSTOM&from=" + today + "&to=" + today.minusDays(1)).andExpect(status().isBadRequest());
        adminGet(METRICS + "?preset=CUSTOM&from=" + today.minusDays(400) + "&to=" + today).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("M-02 핵심 · 오늘 — 거래액 · 주문(결제 뒤 취소 포함) · 증감 GOOD · 카드 4칸 · 시간별 차트 합 = 거래액")
    void coreToday() throws Exception {
        Purchase kept = purchase(creamVariant, 2);
        Purchase cancelled = purchase(serumVariant, 1);
        jdbc.update("UPDATE orders SET status = 'CANCELLED', cancelled_at = ? WHERE order_id = ?", LocalDateTime.now(),
                cancelled.orderId());

        JsonNode body = json(adminGet(METRICS + "?preset=TODAY&tab=CORE").andExpect(status().isOk()));
        Map<String, JsonNode> rows = rows(body);
        assertThat(rows.get("GMV").get("current").asLong()).isEqualTo(CREAM_PRICE * 2L + SERUM_PRICE);
        assertThat(rows.get("ORDERS").get("current").asLong()).isEqualTo(2);
        assertThat(rows.get("ORDERS").get("deltaKind").asText()).isEqualTo("GOOD");
        assertThat(rows.get("FEE_REVENUE").get("current").asLong()).isEqualTo((CREAM_PRICE * 2L + SERUM_PRICE) * 2 / 100);
        assertThat(body.get("cards")).hasSize(4);
        assertThat(body.at("/chart/unit").asText()).isEqualTo("HOUR");
        double chartSum = 0;
        for (JsonNode point : body.at("/chart/points")) {
            chartSum += point.get("value").asDouble();
        }
        assertThat(chartSum).isEqualTo(body.at("/chart/total").asDouble()).isEqualTo(CREAM_PRICE * 2.0 + SERUM_PRICE);
        // 58 계획 5-32 — 비교 기간(어제) 같은 단위 · 같은 버킷 수 · 합 = 비교 기간 값
        assertThat(body.at("/chart/comparePoints")).hasSize(body.at("/chart/points").size());
        double compareSum = 0;
        for (JsonNode point : body.at("/chart/comparePoints")) {
            compareSum += point.get("value").asDouble();
        }
        assertThat(compareSum).isEqualTo(rows.get("GMV").get("previous").asDouble());
        assertThat(kept.orderId()).isNotNull();
        assertThat(body.at("/breakdown/rows/0/0").asText()).isEqualTo("글로우 크림 앵콜 공구");
    }

    @Test
    @DisplayName("M-03 비율 — 반품 · 교환율 · 브랜드 귀책 반품률(접수 ÷ 배송완료 · 「1/1」) · 방향 DOWN 이면 오르면 BAD")
    void ratesWithCounts() throws Exception {
        OrderDeliveryGroup delivered = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(1));
        returnClaim(delivered, itemsOf(delivered).get(0), 1, ClaimReason.DAMAGED_OR_DEFECTIVE);

        Map<String, JsonNode> core = rows(json(adminGet(METRICS + "?preset=TODAY&tab=CORE").andExpect(status().isOk())));
        assertThat(core.get("RETURN_EXCHANGE_RATE").get("current").asDouble()).isEqualTo(100.0);
        assertThat(core.get("RETURN_EXCHANGE_RATE").get("currentCount").asText()).isEqualTo("1/1");
        assertThat(core.get("RETURN_EXCHANGE_RATE").get("deltaKind").asText()).isEqualTo("NEUTRAL"); // 어제는 분모 0

        Map<String, JsonNode> operation = rows(json(adminGet(METRICS + "?preset=TODAY&tab=OPERATION")
                .andExpect(status().isOk())));
        assertThat(operation.get("BRAND_FAULT_RETURN_RATE").get("current").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("M-04 탭 배지 — 나빠진 지표 수(환불 실패가 어제 0 → 오늘 1 이면 운영 탭 +1)")
    void tabBadges() throws Exception {
        Long taskId = operatorRefund(confirmedGroup(creamVariant, 1), "POST_CONFIRM_DEFECT", 1_000);
        jdbc.update("UPDATE order_refund_task SET status = 'FAILED', failed_at = ? WHERE refund_task_id = ?",
                LocalDateTime.now().minusMinutes(5), taskId);

        JsonNode tabs = json(adminGet(METRICS + "/tabs?preset=TODAY").andExpect(status().isOk()));
        assertThat(tabs).hasSize(5);
        Map<String, Long> worsened = new HashMap<>();
        tabs.forEach(t -> worsened.put(t.get("tab").asText(), t.get("worsenedCount").asLong()));
        assertThat(worsened.get("OPERATION")).isGreaterThanOrEqualTo(1);
        Map<String, JsonNode> operation = rows(json(adminGet(METRICS + "?preset=TODAY&tab=OPERATION")));
        assertThat(operation.get("REFUND_FAILURES").get("current").asLong()).isEqualTo(1);
        assertThat(operation.get("REFUND_FAILURES").get("deltaKind").asText()).isEqualTo("BAD");
    }

    @Test
    @DisplayName("[56 D1] 탭 배지 — 핵심 탭은 표 줄 + 4칸을 센다(같은 지표는 한 번) · 다른 탭은 표 줄만")
    void coreBadgeCountsCards() throws Exception {
        Purchase earlier = purchase(creamVariant, 1);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // 비교 기간(그제)에만 결제 — 어제는 거래액 · 주문 · 수수료 · 활성 인플루언서(4칸)가 모두 줄었다
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", yesterday.minusDays(1).atTime(12, 0),
                earlier.orderId());
        String period = "?preset=CUSTOM&from=" + yesterday + "&to=" + yesterday;

        JsonNode core = json(adminGet(METRICS + period + "&tab=CORE").andExpect(status().isOk()));
        Set<String> badRows = worsened(core.get("rows"));
        Set<String> badCards = worsened(core.get("cards"));
        assertThat(badRows).containsExactlyInAnyOrder("GMV", "ORDERS", "FEE_REVENUE");
        assertThat(badCards).containsExactly("ACTIVE_CREATORS");
        Set<String> distinct = new HashSet<>(badRows);
        distinct.addAll(badCards);

        Map<String, Long> badges = new HashMap<>();
        json(adminGet(METRICS + "/tabs" + period).andExpect(status().isOk()))
                .forEach(t -> badges.put(t.get("tab").asText(), t.get("worsenedCount").asLong()));
        assertThat(badges.get("CORE")).isEqualTo((long) distinct.size()).isEqualTo(4L);
        for (String tab : new String[]{"SUPPLY", "GROUP_BUY", "DEMAND", "OPERATION"}) {
            JsonNode body = json(adminGet(METRICS + period + "&tab=" + tab).andExpect(status().isOk()));
            assertThat(body.path("cards").isNull() || body.path("cards").isMissingNode()).as(tab).isTrue();
            assertThat(badges.get(tab)).as(tab).isEqualTo((long) worsened(body.get("rows")).size());
        }
    }

    @Test
    @DisplayName("[56 D7] 기간 생략 — 지표 · 탭 배지 모두 한 달(M1)이 기본이다")
    void defaultPresetIsThisMonth() throws Exception {
        purchase(creamVariant, 1);

        JsonNode omitted = json(adminGet(METRICS + "?tab=CORE").andExpect(status().isOk()));
        JsonNode month = json(adminGet(METRICS + "?preset=M1&tab=CORE").andExpect(status().isOk()));
        assertThat(omitted.get("preset").asText()).isEqualTo("M1");
        assertThat(omitted.get("period")).isEqualTo(month.get("period"));
        assertThat(omitted.get("compare")).isEqualTo(month.get("compare"));
        assertThat(omitted.get("rows")).isEqualTo(month.get("rows"));

        JsonNode omittedTabs = json(adminGet(METRICS + "/tabs").andExpect(status().isOk()));
        assertThat(omittedTabs).isEqualTo(json(adminGet(METRICS + "/tabs?preset=M1").andExpect(status().isOk())));
    }

    private static Set<String> worsened(JsonNode rows) {
        Set<String> keys = new HashSet<>();
        rows.forEach(r -> {
            if ("BAD".equals(r.get("deltaKind").asText())) {
                keys.add(r.get("key").asText());
            }
        });
        return keys;
    }

    private static Map<String, JsonNode> rows(JsonNode body) {
        Map<String, JsonNode> rows = new HashMap<>();
        body.get("rows").forEach(r -> rows.put(r.get("key").asText(), r));
        return rows;
    }
}

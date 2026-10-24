package showroomz.api.admin.metrics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Facts;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Value;
import showroomz.api.admin.metrics.type.MetricKey;
import showroomz.api.admin.metrics.type.MetricsTab;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탭 배지가 세는 지표(56 D1) — 핵심 탭은 표 줄 + 4칸, 다른 탭은 표 줄만. 4칸과 표 줄에 같은 지표가 있으면 한 번만 센다.
 * 지금은 겹치는 키가 없어 통합 테스트로는 드러나지 않으므로 키 계산을 직접 본다.
 */
@DisplayName("AdminMetricsQueryService — 탭 배지 키")
class AdminMetricsBadgeKeysTest {

    private static final LocalDateTime FROM = LocalDateTime.parse("2026-10-01T00:00:00");

    @Test
    @DisplayName("[56 D1-03] 4칸과 표 줄에 같은 지표가 있으면 한 번만 센다 — 표 줄 순서 뒤에 4칸에만 있는 지표 · 나빠진 수도 한 번")
    void overlappingKeyCountsOnce() {
        List<MetricKey> rows = List.of(MetricKey.GMV, MetricKey.ORDERS, MetricKey.RETURN_EXCHANGE_RATE);
        List<MetricKey> cards = List.of(MetricKey.ACTIVE_CREATORS, MetricKey.GMV, MetricKey.RETURN_EXCHANGE_RATE);

        List<MetricKey> keys = AdminMetricsQueryService.badgeKeys(rows, cards);

        assertThat(keys).containsExactly(MetricKey.GMV, MetricKey.ORDERS, MetricKey.RETURN_EXCHANGE_RATE,
                MetricKey.ACTIVE_CREATORS);

        // 배지 = 키마다 증감이 BAD 인 수(getTabs 와 같은 셈) — 겹친 GMV · 반품·교환율이 두 번 세지지 않는다
        Facts current = new Facts(FROM, FROM.plusDays(7));
        Facts previous = new Facts(FROM.minusDays(7), FROM);
        current.put(MetricKey.GMV, Value.of(100));
        previous.put(MetricKey.GMV, Value.of(200));                      // 줄었다 — UP 지표라 BAD
        current.put(MetricKey.RETURN_EXCHANGE_RATE, Value.rate(2, 10));
        previous.put(MetricKey.RETURN_EXCHANGE_RATE, Value.rate(1, 10)); // 늘었다 — DOWN 지표라 BAD
        current.put(MetricKey.ACTIVE_CREATORS, Value.of(3));
        previous.put(MetricKey.ACTIVE_CREATORS, Value.of(1));            // 늘었다 — GOOD
        long worsened = keys.stream()
                .filter(k -> "BAD".equals(AdminMetricsQueryService.row(k, current, previous).deltaKind())).count();
        assertThat(worsened).isEqualTo(2);
        List<MetricKey> withDuplicates = new ArrayList<>(rows);
        withDuplicates.addAll(cards);
        assertThat(withDuplicates.stream()
                .filter(k -> "BAD".equals(AdminMetricsQueryService.row(k, current, previous).deltaKind())).count())
                .as("겹친 키를 두 번 세면 4가 된다").isEqualTo(4);
    }

    @Test
    @DisplayName("[56 D1-03] 지금 설정 — 핵심 탭은 표 줄 + 4칸(중복 없음) · 다른 탭은 표 줄 그대로")
    void currentTabs() {
        List<MetricKey> core = AdminMetricsQueryService.badgeKeys(MetricsTab.CORE);
        List<MetricKey> expected = new ArrayList<>(AdminMetricsQueryService.TAB_ROWS.get(MetricsTab.CORE));
        AdminMetricsQueryService.CORE_CARDS.stream().filter(k -> !expected.contains(k)).forEach(expected::add);

        assertThat(core).containsExactlyElementsOf(expected);
        assertThat(new HashSet<>(core)).hasSameSizeAs(core);
        assertThat(core).containsAll(AdminMetricsQueryService.CORE_CARDS);
        for (MetricsTab tab : MetricsTab.values()) {
            if (tab != MetricsTab.CORE) {
                assertThat(AdminMetricsQueryService.badgeKeys(tab)).as(tab.name())
                        .containsExactlyElementsOf(AdminMetricsQueryService.TAB_ROWS.get(tab));
            }
        }
    }
}

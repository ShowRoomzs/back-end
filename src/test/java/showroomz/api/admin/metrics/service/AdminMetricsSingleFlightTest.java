package showroomz.api.admin.metrics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.metrics.dto.AdminMetricsDto;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Facts;
import showroomz.api.admin.metrics.type.MetricsPreset;
import showroomz.api.admin.metrics.type.MetricsTab;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 같은 기간 계산은 한 번만(58 계획 1-1) — 화면이 {@code /metrics} 와 {@code /metrics/tabs} 를 같이 부르면 캐시가 빈 동안 같은 계산이 겹쳐
 * 돌며 커넥션을 잡았다(10-26 개발 서버 무응답). 두 요청이 겹쳐도 계산은 기간마다 1회다.
 */
@DisplayName("AdminMetricsQueryService — 같은 기간 동시 요청은 계산 1회")
class AdminMetricsSingleFlightTest {

    @Test
    @DisplayName("지표 · 탭 배지를 동시에 불러도 현재 · 비교 기간 계산은 각 1회 — 늦게 온 요청은 먼저 온 계산 결과를 받는다")
    void concurrentRequestsShareOneComputation() throws Exception {
        MetricsFactsCalculator calculator = mock(MetricsFactsCalculator.class);
        AtomicInteger computes = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(calculator.compute(any(), any(), any())).thenAnswer(invocation -> {
            computes.incrementAndGet();
            firstStarted.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new Facts(invocation.getArgument(0), invocation.getArgument(1));
        });
        AdminMetricsQueryService service = new AdminMetricsQueryService(new MetricsPeriodResolver(), calculator);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AdminMetricsDto.Response> metrics =
                    pool.submit(() -> service.getMetrics(MetricsPreset.D7, null, null, MetricsTab.CORE));
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<AdminMetricsDto.TabBadge>> tabs = pool.submit(() -> service.getTabs(MetricsPreset.D7, null, null));
            // 두 번째 요청이 계산 중인 현재 기간을 기다리는 자리에 들어갈 시간
            Thread.sleep(200);
            release.countDown();

            assertThat(metrics.get(5, TimeUnit.SECONDS).rows()).isNotEmpty();
            assertThat(tabs.get(5, TimeUnit.SECONDS)).hasSize(MetricsTab.values().length);
        } finally {
            pool.shutdownNow();
        }
        assertThat(computes.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("계산이 끝나면 캐시 — 같은 기간을 다시 불러도 계산하지 않는다")
    void cachedAfterCompute() {
        MetricsFactsCalculator calculator = mock(MetricsFactsCalculator.class);
        AtomicInteger computes = new AtomicInteger();
        when(calculator.compute(any(), any(), any())).thenAnswer(invocation -> {
            computes.incrementAndGet();
            return new Facts(invocation.getArgument(0), invocation.getArgument(1));
        });
        AdminMetricsQueryService service = new AdminMetricsQueryService(new MetricsPeriodResolver(), calculator);

        service.getTabs(MetricsPreset.D7, null, null);
        service.getMetrics(MetricsPreset.D7, null, null, MetricsTab.SUPPLY);

        assertThat(computes.get()).isEqualTo(2);
    }
}

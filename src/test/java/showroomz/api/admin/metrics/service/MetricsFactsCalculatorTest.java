package showroomz.api.admin.metrics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Value;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 지표 계산 규칙(1017 설계서 7-0 ④ · 7-1) — 공구당 지표는 중앙값 · 비율은 분모 0 이면 계산 불가(null) · 「4/6」 표기.
 */
@DisplayName("MetricsFactsCalculator — 중앙값 · 비율 · 반올림")
class MetricsFactsCalculatorTest {

    @Test
    @DisplayName("중앙값 — 홀수는 가운데 · 짝수는 가운데 둘의 평균 · 비면 null")
    void median() {
        assertThat(MetricsFactsCalculator.median(List.of(300L, 100L, 200L))).isEqualTo(200.0);
        assertThat(MetricsFactsCalculator.median(List.of(400L, 100L, 300L, 200L))).isEqualTo(250.0);
        assertThat(MetricsFactsCalculator.median(List.of(5L))).isEqualTo(5.0);
        assertThat(MetricsFactsCalculator.median(List.of())).isNull();
        assertThat(MetricsFactsCalculator.medianDouble(List.of(1.5, 3.25))).isEqualTo(2.4);
        assertThat(MetricsFactsCalculator.medianDouble(List.of())).isNull();
    }

    @Test
    @DisplayName("비율 — 소수 첫째 자리 % · 분자/분모 표기 · 분모 0 이면 값 없음")
    void rate() {
        Value rate = Value.rate(4, 6);
        assertThat(rate.value()).isEqualTo(66.7);
        assertThat(rate.countText()).isEqualTo("4/6");

        Value empty = Value.rate(0, 0);
        assertThat(empty.value()).isNull();
        assertThat(empty.countText()).isEqualTo("0/0");

        assertThat(Value.of(12).countText()).isNull();
        assertThat(Value.of(12).withNote("신규 2 · 재공구 10").note()).isEqualTo("신규 2 · 재공구 10");
    }

    @Test
    @DisplayName("반올림 — 소수 첫째 자리")
    void round1() {
        assertThat(MetricsFactsCalculator.round1(2.449)).isEqualTo(2.4);
        assertThat(MetricsFactsCalculator.round1(2.45)).isEqualTo(2.5);
        assertThat(MetricsFactsCalculator.round1(0)).isEqualTo(0.0);
    }
}

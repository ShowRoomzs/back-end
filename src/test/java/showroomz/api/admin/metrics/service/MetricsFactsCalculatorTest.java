package showroomz.api.admin.metrics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Value;

import java.math.BigDecimal;
import java.util.Arrays;
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
    @DisplayName("[56 D4] 환불 실패 사유 — 모두 같으면 「모두 …」 · 섞이면 많은 순 두 사유 + 「외 K건」 · 사유가 남지 않았으면 null")
    void refundFailureNote() {
        assertThat(MetricsFactsCalculator.refundFailureNote(List.of("카드 유효기간 만료", "카드 유효기간 만료")))
                .isEqualTo("모두 카드 유효기간 만료");
        assertThat(MetricsFactsCalculator.refundFailureNote(Arrays.asList("한도 초과", "카드 유효기간 만료", "카드 유효기간 만료",
                "잔액 부족", null))).isEqualTo("카드 유효기간 만료 2건 · 잔액 부족 1건 외 2건");
        assertThat(MetricsFactsCalculator.refundFailureNote(List.of("잔액 부족", "한도 초과")))
                .isEqualTo("잔액 부족 1건 · 한도 초과 1건");
        // 재시도가 성공해 사유가 지워진 건은 「모두」로 묶지 않는다
        assertThat(MetricsFactsCalculator.refundFailureNote(Arrays.asList("카드 유효기간 만료", null)))
                .isEqualTo("카드 유효기간 만료 1건 외 1건");
        assertThat(MetricsFactsCalculator.refundFailureNote(Arrays.asList(null, " "))).isNull();
        assertThat(MetricsFactsCalculator.refundFailureNote(List.of())).isNull();
    }

    @Test
    @DisplayName("[56 D4] 요율 표기 — 0.02 → 2% · 0 → 0% · 0.025 → 2.5%")
    void percentText() {
        assertThat(MetricsFactsCalculator.percentText(new BigDecimal("0.02"))).isEqualTo("2%");
        assertThat(MetricsFactsCalculator.percentText(new BigDecimal("0.00"))).isEqualTo("0%");
        assertThat(MetricsFactsCalculator.percentText(new BigDecimal("0.025"))).isEqualTo("2.5%");
    }

    @Test
    @DisplayName("반올림 — 소수 첫째 자리")
    void round1() {
        assertThat(MetricsFactsCalculator.round1(2.449)).isEqualTo(2.4);
        assertThat(MetricsFactsCalculator.round1(2.45)).isEqualTo(2.5);
        assertThat(MetricsFactsCalculator.round1(0)).isEqualTo(0.0);
    }
}

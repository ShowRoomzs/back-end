package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 백분율 한 벌(정합 계획서 R4-1) — 분모 0 은 null · 정확한 십진 반올림. */
class PercentTest {

    @Test
    @DisplayName("소수 1자리 · HALF_UP · 분모 0 은 null")
    void oneDecimal() {
        assertThat(Percent.oneDecimal(291, 5010)).isEqualTo(5.8);
        assertThat(Percent.oneDecimal(1, 8)).isEqualTo(12.5);
        assertThat(Percent.oneDecimal(1, 2000)).isEqualTo(0.1);
        assertThat(Percent.oneDecimal(0, 0)).isNull();
        assertThat(Percent.of(1, 3, 1)).isEqualByComparingTo(new BigDecimal("33.3"));
    }

    @Test
    @DisplayName("정수 백분율 — ROAS · 성비")
    void whole() {
        assertThat(Percent.whole(4_712_800, 815_000)).isEqualTo(578L);
        assertThat(Percent.whole(84, 100)).isEqualTo(84L);
        assertThat(Percent.whole(5, 0)).isNull();
    }

    @Test
    @DisplayName("평균 — 값 없는 항목은 빼고 · 전부 없으면 null")
    void average() {
        assertThat(Percent.average(Arrays.asList(5.8, null, 5.0))).isEqualTo(5.4);
        assertThat(Percent.average(Arrays.asList(null, null))).isNull();
    }
}

package showroomz.api.creator.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 성과 비율 규칙 한 벌 — 소수 1자리 반올림 · 분모 0 은 {@code null}(「—」).
 *
 * <p>0% 로 내리면 「반응이 없었다」로 읽히지만 분모가 0 이면 사실은 「잰 적이 없다」다(게시물 인사이트와 같은 판단).
 */
public final class CreatorPerformanceRates {

    private CreatorPerformanceRates() {
    }

    public static Double percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 공구별 비율의 산술평균(「공구 1건당」 · §49-1) — 값이 없는 공구는 빼고, 남는 게 없으면 null. */
    public static Double average(List<Double> rates) {
        List<Double> present = rates.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        double sum = present.stream().mapToDouble(Double::doubleValue).sum();
        return BigDecimal.valueOf(sum / present.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}

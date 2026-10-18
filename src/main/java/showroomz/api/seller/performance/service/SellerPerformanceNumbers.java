package showroomz.api.seller.performance.service;

import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.global.utils.Percent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.ToLongFunction;

/** 파트너 성과 응답의 수치 표기 — 백분율 · ROAS · 나눗셈 · 산식 문장의 숫자(48 파트너 설계서 5절 · 정합 계획서 R4-4). */
final class SellerPerformanceNumbers {

    private SellerPerformanceNumbers() {
    }

    /** % 값 · 소수 1자리 · 분모 0 이면 null. */
    static BigDecimal pct(long numerator, long denominator) {
        return Percent.of(numerator, denominator, 1);
    }

    /** ROAS % 정수 · 집행비 0 이면 null(0-11). */
    static Long roas(long sales, long spend) {
        return Percent.whole(sales, spend);
    }

    static Long div(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    static String ratioText(String prefix, long numerator, long denominator) {
        BigDecimal ratio = pct(numerator, denominator);
        return ratio == null ? null : "%s %s%%".formatted(prefix, plain(ratio));
    }

    static String plain(BigDecimal value) {
        return value == null ? "—" : value.toPlainString();
    }

    static long zero(Long value) {
        return value == null ? 0 : value;
    }

    static long sum(Collection<PerformanceFacts> facts, ToLongFunction<PerformanceFacts> value) {
        return facts.stream().mapToLong(value).sum();
    }
}

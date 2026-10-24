package showroomz.global.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 백분율 한 벌 — 분자 × 100 ÷ 분모를 <b>정확한 십진 연산</b>으로 반올림(HALF_UP)한다. 분모가 0 이하면 null(「—」)이다 — 0% 로 내리면 「반응이
 * 없었다」로 읽히지만 분모가 0 이면 사실은 「잰 적이 없다」다(게시물 인사이트 · 성과 관리 공통 판단 · 정합 계획서 R4-1).
 *
 * <p>성과(파트너 · 스튜디오) · 게시물 인사이트 · 어드민 게시물 지표 · 쇼룸 현황이 이 클래스로 같은 숫자를 낸다. double 로 나눈 뒤 반올림하던
 * 식({@code Math.round(x * 10) / 10.0})은 부동소수 오차로 경계값에서 한 끗씩 갈렸다.
 */
public final class Percent {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Percent() {
    }

    /** 소수 {@code scale}자리 백분율 · 분모 0 이하면 null. */
    public static BigDecimal of(long numerator, long denominator, int scale) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    /** 소수 1자리 백분율(double) · 분모 0 이하면 null. */
    public static Double oneDecimal(long numerator, long denominator) {
        BigDecimal value = of(numerator, denominator, 1);
        return value == null ? null : value.doubleValue();
    }

    /** 정수 백분율 · 분모 0 이하면 null. */
    public static Long whole(long numerator, long denominator) {
        BigDecimal value = of(numerator, denominator, 0);
        return value == null ? null : value.longValue();
    }

    /** 비율들의 산술평균(「공구 1건당」) — 값이 없는 항목은 빼고, 남는 게 없으면 null. 소수 1자리. */
    public static Double average(List<Double> rates) {
        List<Double> present = rates.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(present.size()), 1, RoundingMode.HALF_UP).doubleValue();
    }
}

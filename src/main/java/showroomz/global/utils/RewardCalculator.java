package showroomz.global.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 예상 리워드 계산 — 계약 응답 · 정산 · 계약서 PDF 세 곳이 이 메서드만 호출한다(설계서 1-5).
 *
 * <p><b>1원 단위 · 소수점 버림</b>으로 확정. 10원 단위 제약은 공구가의 것이고(§25-5-3)
 * 리워드는 그 파생값이다. 계약서 생성규격 v0.1 §4-4의 「원 단위 절사」와 같은 규칙이다 —
 * 계약서에 인쇄되는 값과 화면·정산이 다르면 계약서가 근거가 되지 못한다.
 *
 * <p>반올림·올림이 아닌 이유: 이 값은 브랜드가 인플루언서에게 지급할 금액의 단가이고,
 * 그 차액은 수량만큼 곱해진다. 계약서에 근거가 없는 금액이 정산에 섞이면
 * 분쟁 해결 경로(계약서가 근거, §25-5-4)가 무너진다.
 *
 * <p>{@code FLOOR}가 아니라 {@code DOWN}인 이유: 두 값은 양수에서 같지만 음수에서 갈린다.
 * 리워드가 음수가 될 일은 없어야 하지만 그 「없어야 한다」에 기대지 않는다.
 * 절사는 "소수부를 버린다"는 뜻이고 그것을 그대로 표현하는 모드는 {@code DOWN}이다.
 */
public final class RewardCalculator {

    private RewardCalculator() {
    }

    /** 1개당 예상 리워드(원). 입력이 비어 있으면 계산하지 않고 null을 돌려준다(작성중 계약). */
    public static Long calcUnitReward(Integer groupBuyPrice, BigDecimal rewardRate) {
        if (groupBuyPrice == null || rewardRate == null) {
            return null;
        }
        return calcUnitReward(groupBuyPrice.intValue(), rewardRate);
    }

    public static long calcUnitReward(int groupBuyPrice, BigDecimal rewardRate) {
        return BigDecimal.valueOf(groupBuyPrice)
                .multiply(rewardRate)
                .divide(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }
}

package showroomz.domain.performance.vo;

import showroomz.global.utils.Percent;

/**
 * 성비 — 미확인 제외 · 정수 % · 반올림 합이 100 이 되게 남성을 나머지로 둔다(파트너 KPI · 스튜디오 분포가 같은 식 · 정합 계획서 R4-2).
 * 아는 성별이 없으면 null 이다.
 */
public record GenderSplit(int femalePercent, int malePercent) {

    public static GenderSplit of(long female, long male) {
        Long femalePercent = Percent.whole(female, female + male);
        if (femalePercent == null) {
            return null;
        }
        return new GenderSplit(femalePercent.intValue(), 100 - femalePercent.intValue());
    }

    public boolean femaleFirst() {
        return femalePercent >= malePercent;
    }
}

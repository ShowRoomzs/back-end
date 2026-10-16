package showroomz.api.creator.performance.service;

import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.api.creator.showroom.service.Demographics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 구매 소비자 연령 · 성별 분포(49 스튜디오 성과 설계서 0-10).
 *
 * <p>구간은 쇼룸 현황 · 게시물 인사이트와 같은 {@link Demographics} 라벨이다 — 같은 사람이 한쪽에서는 「25–34세」, 다른 쪽에서는
 * 「20대」로 세어지면 안 된다. 가림 규칙 둘을 겹친다 — 구간 인원 {@value #MINIMUM_BAND_SIZE}명 미만은 「기타」로 합산(소규모 공구의
 * 「50대+ 1명」은 특정 개인이다 · §47-2), 표본 {@value #MINIMUM_SAMPLE_SIZE}명 미만이면 비율 전체를 비공개(쇼룸 현황과 같은 잠정값).
 *
 * <p>나이 기준일은 <b>공구 종료일</b>이다 — 해가 바뀌어도 지난 공구의 분포가 움직이지 않게 한다.
 */
public final class CreatorPerformanceDistribution {

    public static final int MINIMUM_SAMPLE_SIZE = 30;
    public static final int MINIMUM_BAND_SIZE = 5;
    public static final String OTHER_LABEL = "기타";

    private CreatorPerformanceDistribution() {
    }

    /** 소비자 한 명의 분포 재료 — 식별값 없이 성별 · 생년월일만. */
    public record Person(String gender, String birthday) {
    }

    public static CreatorPerformanceDto.Distribution of(List<Person> buyers, LocalDate asOf) {
        long sample = buyers.size();
        if (sample < MINIMUM_SAMPLE_SIZE) {
            return new CreatorPerformanceDto.Distribution(true, sample, MINIMUM_SAMPLE_SIZE, null, List.of());
        }

        long female = 0;
        long male = 0;
        Map<String, long[]> bands = new LinkedHashMap<>();   // label → [합계, 여, 남]
        for (String label : Demographics.AGE_LABELS) {
            bands.put(label, new long[3]);
        }
        for (Person buyer : buyers) {
            String gender = Demographics.genderLabelOf(buyer.gender());
            String band = Demographics.ageGroupOf(buyer.birthday(), asOf);
            long[] counts = bands.computeIfAbsent(band, k -> new long[3]);
            counts[0]++;
            if ("여성".equals(gender)) {
                female++;
                counts[1]++;
            } else if ("남성".equals(gender)) {
                male++;
                counts[2]++;
            }
        }
        long known = female + male;
        CreatorPerformanceDto.GenderSplit genders = new CreatorPerformanceDto.GenderSplit(
                CreatorPerformanceRates.percent(female, known), CreatorPerformanceRates.percent(male, known),
                female, male, sample - known);

        List<CreatorPerformanceDto.AgeGroup> groups = new ArrayList<>();
        long[] other = new long[3];
        for (Map.Entry<String, long[]> entry : bands.entrySet()) {
            long[] counts = entry.getValue();
            if (counts[0] == 0) {
                continue;
            }
            if (counts[0] < MINIMUM_BAND_SIZE) {
                for (int i = 0; i < 3; i++) {
                    other[i] += counts[i];
                }
                continue;
            }
            groups.add(group(entry.getKey(), counts, sample));
        }
        if (other[0] > 0) {
            groups.add(group(OTHER_LABEL, other, sample));
        }
        return new CreatorPerformanceDto.Distribution(false, sample, MINIMUM_SAMPLE_SIZE, genders, groups);
    }

    /** 성비 KPI — 미확인 제외 · 큰 쪽을 앞에. 아는 성별이 없으면 null. 반올림 합이 100 이 되게 한쪽을 나머지로 둔다. */
    public static CreatorPerformanceDto.GenderRatio genderRatio(long female, long male) {
        long known = female + male;
        if (known == 0) {
            return null;
        }
        int femalePercent = (int) Math.round(female * 100.0 / known);
        int malePercent = 100 - femalePercent;
        return new CreatorPerformanceDto.GenderRatio(femalePercent >= malePercent ? "FEMALE" : "MALE",
                femalePercent, malePercent);
    }

    private static CreatorPerformanceDto.AgeGroup group(String label, long[] counts, long sample) {
        return new CreatorPerformanceDto.AgeGroup(label, counts[0], CreatorPerformanceRates.percent(counts[0], sample),
                CreatorPerformanceRates.percent(counts[1], sample), CreatorPerformanceRates.percent(counts[2], sample));
    }
}

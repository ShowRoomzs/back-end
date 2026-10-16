package showroomz.api.creator.performance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.api.creator.performance.type.CreatorPerformanceStatus;
import showroomz.api.creator.performance.type.RewardStage;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.performance.type.PerformanceTone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 스튜디오 성과 응답 규칙 — 상태 접기 · 돈의 단계 · 분포 가림 · 하이라이트 문장 · 비율(49 스튜디오 성과 설계서 0-3 · 0-10 · 3-5). */
@DisplayName("스튜디오 성과 — 상태 접기 · 분포 · 하이라이트 · 비율")
class CreatorPerformanceRulesTest {

    private static final LocalDate END = LocalDate.of(2026, 8, 31);

    @Test
    @DisplayName("공통 5종 → 스튜디오 3종 — 돈이 들어오기 전은 전부 집계 중 · 톤은 중립 · 경고 · 성공뿐")
    void foldsStatus() {
        assertThat(Arrays.stream(PerformanceStatus.values()).map(CreatorPerformanceStatus::of))
                .containsExactly(CreatorPerformanceStatus.COLLECTING, CreatorPerformanceStatus.COLLECTING,
                        CreatorPerformanceStatus.ADJUSTING, CreatorPerformanceStatus.COLLECTING,
                        CreatorPerformanceStatus.SETTLED);
        assertThat(CreatorPerformanceStatus.COLLECTING.getTone()).isEqualTo(PerformanceTone.NEUTRAL);
        assertThat(CreatorPerformanceStatus.ADJUSTING.getTone()).isEqualTo(PerformanceTone.WARNING);
        assertThat(RewardStage.of(CreatorPerformanceStatus.COLLECTING)).isEqualTo(RewardStage.AFTER_SETTLEMENT);
        assertThat(RewardStage.of(CreatorPerformanceStatus.ADJUSTING)).isEqualTo(RewardStage.AFTER_AGREEMENT);
        assertThat(RewardStage.of(CreatorPerformanceStatus.SETTLED)).isEqualTo(RewardStage.CONFIRMED);
    }

    @Test
    @DisplayName("분포 — 표본 30명 미만이면 비율 비공개 · 30명이면 공개")
    void suppressesSmallSample() {
        List<CreatorPerformanceDistribution.Person> small = people("FEMALE", "2000-01-01", 29);
        CreatorPerformanceDto.Distribution hidden = CreatorPerformanceDistribution.of(small, END);
        assertThat(hidden.suppressed()).isTrue();
        assertThat(hidden.genders()).isNull();
        assertThat(hidden.ageGroups()).isEmpty();
        assertThat(hidden.sampleSize()).isEqualTo(29);

        assertThat(CreatorPerformanceDistribution.of(people("FEMALE", "2000-01-01", 30), END).suppressed()).isFalse();
    }

    @Test
    @DisplayName("분포 — 구간 5명 미만은 「기타」로 합산 · 성별 비율은 미확인 제외 · 나이 기준일은 종료일")
    void mergesSmallBands() {
        List<CreatorPerformanceDistribution.Person> buyers = new ArrayList<>();
        buyers.addAll(people("FEMALE", "2003-09-01", 20));   // 종료일 기준 22세 → 18–24세
        buyers.addAll(people("MALE", "1995-01-01", 8));      // 31세 → 25–34세
        buyers.addAll(people("FEMALE", "1985-01-01", 3));    // 41세 → 35–44세 · 5명 미만 → 기타
        buyers.addAll(people(null, null, 2));                // 미확인 · 5명 미만 → 기타

        CreatorPerformanceDto.Distribution d = CreatorPerformanceDistribution.of(buyers, END);

        assertThat(d.suppressed()).isFalse();
        assertThat(d.ageGroups()).extracting(CreatorPerformanceDto.AgeGroup::label)
                .containsExactly("18–24세", "25–34세", "기타");
        assertThat(d.ageGroups().get(2).count()).isEqualTo(5);
        assertThat(d.ageGroups().get(0).total()).isEqualTo(60.6);   // 20 / 33
        assertThat(d.genders().female()).isEqualTo(74.2);           // 23 / 31(미확인 2 제외)
        assertThat(d.genders().unknownCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("성비 KPI — 큰 쪽을 앞에 · 합 100 · 아는 성별이 없으면 null")
    void genderRatio() {
        CreatorPerformanceDto.GenderRatio ratio = CreatorPerformanceDistribution.genderRatio(84, 16);
        assertThat(ratio.majorFirst()).isEqualTo("FEMALE");
        assertThat(ratio.female() + ratio.male()).isEqualTo(100);
        assertThat(CreatorPerformanceDistribution.genderRatio(10, 30).majorFirst()).isEqualTo("MALE");
        assertThat(CreatorPerformanceDistribution.genderRatio(0, 0)).isNull();
    }

    @Test
    @DisplayName("하이라이트 — 근거 수치와 분모를 문장에 · 값이 전부 0 이면 카드 없음")
    void highlights() {
        List<CreatorPerformanceDto.ProductRow> rows = List.of(
                new CreatorPerformanceDto.ProductRow(1L, "데일리 선크림 50ml", 4280, 251, 220, 4_400_000,
                        new BigDecimal("12.0"), 528_000),
                new CreatorPerformanceDto.ProductRow(2L, "데일리 선크림 리필", 1640, 62, 54, 1_080_000,
                        new BigDecimal("7.0"), 75_600));

        CreatorPerformanceDto.Highlights h = CreatorPerformanceHighlights.of(rows, false);

        assertThat(h.mostViewed().basis()).isEqualTo("상세 조회 4,280회 · 전체의 72%");
        assertThat(h.bestConversion().basis()).isEqualTo("조회 4,280회 중 251건 · 5.9%");
        assertThat(h.topReward().basis()).isEqualTo("528,000원 · 계약 기준 판매 리워드의 87%");

        List<CreatorPerformanceDto.ProductRow> empty = List.of(new CreatorPerformanceDto.ProductRow(1L, "x", 0, 0, 0, 0,
                BigDecimal.TEN, 0));
        CreatorPerformanceDto.Highlights none = CreatorPerformanceHighlights.of(empty, true);
        assertThat(none.mostViewed()).isNull();
        assertThat(none.bestConversion()).isNull();
        assertThat(none.topReward()).isNull();
    }

    @Test
    @DisplayName("비율 — 분모 0 은 null(잰 적 없음) · 평균은 값 있는 공구만의 산술평균")
    void rates() {
        assertThat(CreatorPerformanceRates.percent(291, 5010)).isEqualTo(5.8);
        assertThat(CreatorPerformanceRates.percent(0, 0)).isNull();
        assertThat(CreatorPerformanceRates.average(Arrays.asList(5.8, null, 5.0))).isEqualTo(5.4);
        assertThat(CreatorPerformanceRates.average(Arrays.asList(null, null))).isNull();
    }

    private static List<CreatorPerformanceDistribution.Person> people(String gender, String birthday, int n) {
        List<CreatorPerformanceDistribution.Person> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new CreatorPerformanceDistribution.Person(gender, birthday));
        }
        return list;
    }
}

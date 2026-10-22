package showroomz.api.seller.performance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.domain.performance.type.PerformanceAgeBand;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.performance.type.PerformanceTone;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파트너 성과 조립 규칙 — 스프링 없이(48 파트너 성과 테스트 시나리오 ED-01 ~ 04 · CS-07 ~ 09 · FS-02).
 * 반올림 · 분모 0 · 연령 구간 · 「기타」 합산 · 상태 파생은 응답 숫자의 모양을 정하는 규칙이라 통합 테스트보다 여기서 촘촘히 본다.
 */
@DisplayName("파트너 성과 조립 규칙 — 반올림 · 연령 구간 · 「기타」 합산 · 상태 파생")
class SellerPerformanceAssemblerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    // ------------------------------------------------------------------ ED 반올림 · 분모 0

    @Test
    @DisplayName("ED-01 비율 — 소수 1자리 HALF_UP · 분모 0 이면 null")
    void percent() {
        assertThat(SellerPerformanceNumbers.pct(2, 3)).isEqualByComparingTo("66.7");
        assertThat(SellerPerformanceNumbers.pct(1, 8)).isEqualByComparingTo("12.5");
        assertThat(SellerPerformanceNumbers.pct(1, 6)).isEqualByComparingTo("16.7");
        assertThat(SellerPerformanceNumbers.pct(0, 4)).isEqualByComparingTo("0.0");
        assertThat(SellerPerformanceNumbers.pct(3, 0)).isNull();
    }

    @Test
    @DisplayName("ED-02 ROAS — % 정수 HALF_UP(시안 D1 578 · D4 614 · F-MAIN 34) · 집행비 0 이면 null")
    void roas() {
        assertThat(SellerPerformanceNumbers.roas(5_480_000, 948_000)).isEqualTo(578);
        assertThat(SellerPerformanceNumbers.roas(5_480_000, 893_200)).isEqualTo(614);
        assertThat(SellerPerformanceNumbers.roas(105_600, 312_192)).isEqualTo(34);
        assertThat(SellerPerformanceNumbers.roas(105_600, 0)).isNull();
    }

    @Test
    @DisplayName("ED-03 AOV · 획득 단가 — 원 단위 HALF_UP · 분모 0 null · 분자 0 은 0(측정된 0)")
    void division() {
        assertThat(SellerPerformanceNumbers.div(78_400, 3)).isEqualTo(26_133);
        assertThat(SellerPerformanceNumbers.div(312_192, 2)).isEqualTo(156_096);
        assertThat(SellerPerformanceNumbers.div(105_600, 0)).isNull();
        assertThat(SellerPerformanceNumbers.div(0, 2)).isZero();
    }

    @Test
    @DisplayName("ED-04 수익률 — 시안 D1 22.3 · D4 23.4 · 음수 수익도 소수 1자리")
    void profitRate() {
        assertThat(SellerPerformanceNumbers.pct(1_224_200, 5_480_000)).isEqualByComparingTo("22.3");
        assertThat(SellerPerformanceNumbers.pct(1_284_480, 5_480_000)).isEqualByComparingTo("23.4");
        assertThat(SellerPerformanceNumbers.pct(-250_000, 105_600)).isEqualByComparingTo("-236.7");
    }

    // ------------------------------------------------------------------ CS 연령 · 「기타」

    @Test
    @DisplayName("CS-07 연령 경계 — 공구 시작일 기준 만 나이 · 생일 없음 · 형식 오류는 미확인")
    void ageBandBoundaries() {
        LocalDate asOf = START.toLocalDate();
        assertThat(PerformanceAgeBand.of("2006-09-01", asOf)).isEqualTo(PerformanceAgeBand.TWENTIES);
        assertThat(PerformanceAgeBand.of("2006-09-02", asOf)).isEqualTo(PerformanceAgeBand.TEENS);
        assertThat(PerformanceAgeBand.of("1996-09-02", asOf)).isEqualTo(PerformanceAgeBand.TWENTIES);
        assertThat(PerformanceAgeBand.of("1996-09-01", asOf)).isEqualTo(PerformanceAgeBand.THIRTIES);
        assertThat(PerformanceAgeBand.of("1976-09-01", asOf)).isEqualTo(PerformanceAgeBand.FIFTIES_PLUS);
        assertThat(PerformanceAgeBand.of("1976-09-02", asOf)).isEqualTo(PerformanceAgeBand.FORTIES);
        assertThat(PerformanceAgeBand.of(null, asOf)).isEqualTo(PerformanceAgeBand.UNKNOWN);
        assertThat(PerformanceAgeBand.of(" ", asOf)).isEqualTo(PerformanceAgeBand.UNKNOWN);
        assertThat(PerformanceAgeBand.of("1998/03/02", asOf)).isEqualTo(PerformanceAgeBand.UNKNOWN);
        // 해가 바뀌어도 지난 공구의 분포가 움직이지 않는다 — 기준일은 공구 시작일이다
        assertThat(PerformanceAgeBand.of("2006-09-02", asOf.plusYears(1))).isEqualTo(PerformanceAgeBand.TWENTIES);
    }

    @Test
    @DisplayName("CS-08 「기타」 합산 — 5명 이상 구간은 그대로 · 5명 미만 구간 합산(merged) · 미확인은 5명 미만이어도 별도 행 · 0명 구간은 행 없음")
    void ageBandMerging() {
        List<PerformanceFacts.Buyer> buyers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            buyers.add(buyer("FEMALE", "1998-03-02"));
        }
        buyers.add(buyer("MALE", "1999-05-05"));
        for (int i = 0; i < 3; i++) {
            buyers.add(buyer("FEMALE", "1990-01-01"));
        }
        buyers.add(buyer("MALE", "1982-01-01"));
        buyers.add(buyer("FEMALE", null));
        buyers.add(buyer("MALE", ""));

        List<SellerPerformanceDto.AgeBand> bands = SellerPerformanceSections.ageBands(facts(buyers));

        assertThat(bands).extracting(SellerPerformanceDto.AgeBand::band)
                .containsExactly("TWENTIES", "OTHER", "UNKNOWN");
        SellerPerformanceDto.AgeBand twenties = bands.get(0);
        assertThat(twenties.female()).isEqualTo(5);
        assertThat(twenties.male()).isEqualTo(1);
        assertThat(twenties.total()).isEqualTo(6);
        assertThat(twenties.ratio()).isEqualByComparingTo("50.0"); // 분모 = 구매확정 소비자 12명
        assertThat(twenties.merged()).isFalse();
        SellerPerformanceDto.AgeBand other = bands.get(1);
        assertThat(other.label()).isEqualTo("기타");
        assertThat(other.female()).isEqualTo(3);
        assertThat(other.male()).isEqualTo(1);
        assertThat(other.total()).isEqualTo(4);
        assertThat(other.merged()).isTrue();
        SellerPerformanceDto.AgeBand unknown = bands.get(2);
        assertThat(unknown.label()).isEqualTo("미확인");
        assertThat(unknown.total()).isEqualTo(2);
        assertThat(unknown.merged()).isFalse();
    }

    @Test
    @DisplayName("CS-08 ② 5명 미만 구간이 없으면 「기타」 행이 없다 · 구매자 0명이면 빈 목록")
    void ageBandWithoutMerge() {
        List<PerformanceFacts.Buyer> buyers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            buyers.add(buyer("MALE", "1985-01-10"));
        }
        assertThat(SellerPerformanceSections.ageBands(facts(buyers))).extracting(SellerPerformanceDto.AgeBand::band)
                .containsExactly("FORTIES");
        assertThat(SellerPerformanceSections.ageBands(facts(List.of()))).isEmpty();
    }

    @Test
    @DisplayName("CS-09 성별 미확인 소비자는 나이를 알아도 「미확인」 행으로 간다(현재 동작 고정 · 시나리오 14절 #4)")
    void genderlessBuyerGoesToUnknown() {
        List<SellerPerformanceDto.AgeBand> bands = SellerPerformanceSections.ageBands(
                facts(List.of(buyer(null, "1998-03-02"))));
        assertThat(bands).singleElement().satisfies(band -> {
            assertThat(band.band()).isEqualTo("UNKNOWN");
            assertThat(band.female()).isZero();
            assertThat(band.male()).isZero();
            assertThat(band.total()).isEqualTo(1);
        });
    }

    // ------------------------------------------------------------------ FS 상태 파생

    @Test
    @DisplayName("FS-02 상태 파생 — 정산 없음 = 집계 중 · 분배 실패는 지급 완료로 접는다 · 톤은 정보 · 위험색 없음")
    void statusDerivation() {
        assertThat(PerformanceStatus.of(null)).isEqualTo(PerformanceStatus.AGGREGATING);
        assertThat(PerformanceStatus.of(SettlementStatus.REVIEWING)).isEqualTo(PerformanceStatus.REVIEWING);
        assertThat(PerformanceStatus.of(SettlementStatus.ADJUSTING)).isEqualTo(PerformanceStatus.ADJUSTING);
        assertThat(PerformanceStatus.of(SettlementStatus.PAYOUT_SCHEDULED)).isEqualTo(PerformanceStatus.PAYOUT_SCHEDULED);
        assertThat(PerformanceStatus.of(SettlementStatus.PAID)).isEqualTo(PerformanceStatus.PAID);
        assertThat(PerformanceStatus.of(SettlementStatus.PAYOUT_FAILED)).isEqualTo(PerformanceStatus.PAID);

        assertThat(PerformanceStatus.AGGREGATING.getTone()).isEqualTo(PerformanceTone.NEUTRAL);
        assertThat(PerformanceStatus.REVIEWING.getTone()).isEqualTo(PerformanceTone.NEUTRAL);
        assertThat(PerformanceStatus.ADJUSTING.getTone()).isEqualTo(PerformanceTone.WARNING);
        assertThat(PerformanceStatus.PAYOUT_SCHEDULED.getTone()).isEqualTo(PerformanceTone.NEUTRAL);
        assertThat(PerformanceStatus.PAID.getTone()).isEqualTo(PerformanceTone.SUCCESS);

        assertThat(PerformanceStatus.PAID.settlementStatuses())
                .containsExactlyInAnyOrder(SettlementStatus.PAID, SettlementStatus.PAYOUT_FAILED);
        assertThat(PerformanceStatus.AGGREGATING.settlementStatuses()).isEmpty();
    }

    // ------------------------------------------------------------------ 시드

    private static PerformanceFacts.Buyer buyer(String gender, String birthday) {
        return new PerformanceFacts.Buyer(null, gender, birthday, false, false, false);
    }

    /** {@link SellerPerformanceAssembler#ageBands}는 구매자 목록과 공구 시작일만 읽는다. */
    private static PerformanceFacts facts(List<PerformanceFacts.Buyer> buyers) {
        return new PerformanceFacts(1L, 1L, 1L, 1L, PerformanceStatus.PAID, START, START.plusDays(7), false, null, 0,
                null, null, null, null, List.of(), buyers, null, null, null);
    }
}

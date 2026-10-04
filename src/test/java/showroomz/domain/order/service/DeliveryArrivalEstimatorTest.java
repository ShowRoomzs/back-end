package showroomz.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("도착 예정일 — 집화일 + N배송일(일요일·공휴일 제외) · 택배사별 실제 소요일 평균으로 보정")
class DeliveryArrivalEstimatorTest {

    private static final DeliveryCarrier MEASURED = DeliveryCarrier.values()[0];
    private static final DeliveryCarrier SPARSE = DeliveryCarrier.values()[1];
    /** 2026-09-14 는 월요일. */
    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 9, 14, 19, 30);

    private final OrderDeliveryGroupRepository repository = mock(OrderDeliveryGroupRepository.class);

    @Test
    @DisplayName("기본 3배송일 — 집화일은 세지 않고, 토요일은 세고, 일요일은 건너뛴다")
    void defaultThreeDeliveryDays() {
        DeliveryArrivalEstimator estimator = estimator();

        assertThat(estimator.estimate(MEASURED, MONDAY)).isEqualTo(LocalDate.of(2026, 9, 17));
        // 목 집화 → 금 · 토 · (일) · 월
        assertThat(estimator.estimate(MEASURED, MONDAY.plusDays(3))).isEqualTo(LocalDate.of(2026, 9, 21));
        // 토 집화 → (일) · 월 · 화 · 수
        assertThat(estimator.estimate(MEASURED, MONDAY.plusDays(5))).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("공휴일은 건너뛴다")
    void skipsHolidays() {
        DeliveryArrivalEstimator estimator = estimator("2026-09-18");

        // 목 집화 → (금 공휴일) · 토 · (일) · 월 · 화
        assertThat(estimator.estimate(MEASURED, MONDAY.plusDays(3))).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    @DisplayName("집화 전이면 예정일이 없다")
    void noPickupNoEstimate() {
        assertThat(estimator().estimate(MEASURED, null)).isNull();
    }

    @Test
    @DisplayName("표본이 충분한 택배사는 실제 소요일 평균(올림)으로 보정하고, 모자란 택배사는 기본값을 쓴다")
    void correctsByCarrierAverage() {
        List<Object[]> samples = new ArrayList<>();
        // 15건은 1일, 15건은 2일 → 평균 1.5 → 올림 2
        samples.addAll(samples(MEASURED, 15, 1));
        samples.addAll(samples(MEASURED, 15, 2));
        samples.addAll(samples(SPARSE, 29, 1));
        when(repository.findTransitSamples(any())).thenReturn(samples);
        DeliveryArrivalEstimator estimator = estimator();

        assertThat(estimator.estimate(MEASURED, MONDAY)).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(estimator.estimate(SPARSE, MONDAY)).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(estimator.estimate(null, MONDAY)).isEqualTo(LocalDate.of(2026, 9, 17));
        // 평균은 메모리에 둔다 — 조회마다 집계하지 않는다.
        verify(repository, times(1)).findTransitSamples(any());
    }

    @Test
    @DisplayName("소요일은 배송일로 센다(일요일 제외) — 집화와 완료를 같은 날 본 표본은 버린다")
    void transitCountsDeliveryDaysOnly() {
        List<Object[]> samples = new ArrayList<>();
        // 토 집화 → 화 완료 = 월 · 화 2일(일요일 제외). 달력일로 세면 3일이다.
        for (int i = 0; i < 30; i++) {
            samples.add(new Object[]{MEASURED, MONDAY.plusDays(5), MONDAY.plusDays(8)});
        }
        // 같은 날 표본 100건 — 섞이면 평균이 1 아래로 끌려간다.
        for (int i = 0; i < 100; i++) {
            samples.add(new Object[]{MEASURED, MONDAY, MONDAY.plusHours(1)});
        }
        when(repository.findTransitSamples(any())).thenReturn(samples);

        assertThat(estimator().estimate(MEASURED, MONDAY)).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    private DeliveryArrivalEstimator estimator(String... holidays) {
        return new DeliveryArrivalEstimator(new BusinessCalendar(holidays), repository, new OrderProperties());
    }

    /** 월요일 집화 → {@code days}일 뒤 완료(주 중이라 달력일 = 배송일). */
    private static List<Object[]> samples(DeliveryCarrier carrier, int count, int days) {
        List<Object[]> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            samples.add(new Object[]{carrier, MONDAY, MONDAY.plusDays(days)});
        }
        return samples;
    }
}

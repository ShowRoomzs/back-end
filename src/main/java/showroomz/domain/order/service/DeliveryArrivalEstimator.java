package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * 도착 예정일 — <b>집화일 + N배송일</b>(일요일·공휴일 제외). 택배 연동이 도착 예정을 주지 않아(C10 설계서 U1) 직접 센다.
 *
 * <p>N 의 기본은 설정값(3)이고, 택배사별로 실제 소요일(집화 → 배송완료)이 충분히 쌓이면 그 평균으로 보정한다.
 * 따로 집계 테이블을 두지 않는다 — 하위주문의 {@code picked_up_at} · {@code delivered_at}이 곧 쌓이는 데이터다.
 * 평균은 메모리에 두고 주기적으로 다시 읽는다(목록 조회마다 집계하지 않는다).
 *
 * <p>배송일은 영업일과 다르다 — 택배는 토요일에 움직인다. 그래서 {@link BusinessCalendar}의 영업일 계산을 쓰지 않고
 * 공휴일 목록만 빌린다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryArrivalEstimator {

    private final BusinessCalendar businessCalendar;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProperties orderProperties;

    private volatile LeadDays cached;

    /** 집화 전이면 null — 약속할 날짜가 없다. */
    public LocalDate estimate(DeliveryCarrier carrier, LocalDateTime pickedUpAt) {
        if (pickedUpAt == null) {
            return null;
        }
        int leadDays = leadDays(carrier);
        LocalDate date = pickedUpAt.toLocalDate();
        for (int counted = 0; counted < leadDays; ) {
            date = date.plusDays(1);
            if (isDeliveryDay(date)) {
                counted++;
            }
        }
        return date;
    }

    /** {@code from} 다음 날부터 {@code to}까지의 배송일 수 — 실제 소요일. */
    int deliveryDaysBetween(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate date = from.plusDays(1); !date.isAfter(to); date = date.plusDays(1)) {
            if (isDeliveryDay(date)) {
                count++;
            }
        }
        return count;
    }

    private boolean isDeliveryDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SUNDAY && !businessCalendar.isHoliday(date);
    }

    private int leadDays(DeliveryCarrier carrier) {
        LeadDays current = cached;
        LocalDateTime now = LocalDateTime.now();
        if (current == null || !current.loadedAt().plusHours(orderProperties.getArrivalStatsRefreshHours()).isAfter(now)) {
            current = load(now);
            cached = current;
        }
        Integer measured = carrier == null ? null : current.byCarrier().get(carrier);
        return measured != null ? measured : orderProperties.getArrivalDefaultDays();
    }

    /**
     * 택배사별 평균 소요일 — 최근 N일의 자동 확인(추적) 배송완료만. 표본이 모자란 택배사는 넣지 않는다(기본값으로 간다).
     * 평균은 올림한다 — 예정일은 하루 일찍 틀리는 것보다 하루 늦게 맞는 쪽이 낫다.
     */
    private LeadDays load(LocalDateTime now) {
        Map<DeliveryCarrier, int[]> sums = new EnumMap<>(DeliveryCarrier.class);
        for (Object[] row : deliveryGroupRepository.findTransitSamples(now.minusDays(orderProperties.getArrivalStatsWindowDays()))) {
            int days = deliveryDaysBetween(((LocalDateTime) row[1]).toLocalDate(), ((LocalDateTime) row[2]).toLocalDate());
            if (days < 1) {
                continue; // 집화와 완료를 같은 회차에 본 송장 — 소요일을 말해 주지 않는다.
            }
            int[] sum = sums.computeIfAbsent((DeliveryCarrier) row[0], carrier -> new int[2]);
            sum[0] += days;
            sum[1]++;
        }
        Map<DeliveryCarrier, Integer> byCarrier = new EnumMap<>(DeliveryCarrier.class);
        sums.forEach((carrier, sum) -> {
            if (sum[1] >= orderProperties.getArrivalStatsMinSamples()) {
                byCarrier.put(carrier, (int) Math.ceil((double) sum[0] / sum[1]));
            }
        });
        return new LeadDays(byCarrier, now);
    }

    private record LeadDays(Map<DeliveryCarrier, Integer> byCarrier, LocalDateTime loadedAt) {
    }
}

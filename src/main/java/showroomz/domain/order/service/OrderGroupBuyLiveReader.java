package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.OrderStatus;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 진행 중 공구 실시간(1017 설계서 4-2) — <b>결제 기준 · 취소 전 포함</b>. 결제된 순간을 센다 — 뒤에 취소돼도 빠지지 않는다.
 * 확정 성과(취소 · 반품 반영 · {@link OrderGroupBuySalesReader})와 정의가 다르다 — 파트너 홈 시안이 그렇게 적었다.
 *
 * <p>오늘 · 어제 같은 시각 · 시간대 24칸은 이틀 치 결제 줄을 한 번 읽어 Java 에서 나눈다(DB 시간 함수 차이를 타지 않는다).
 * 「오늘」은 00:00 기준이다(어드민 지표의 오늘과 같다).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderGroupBuyLiveReader {

    static final int HOURS = 24;

    private final OrderProductRepository orderProductRepository;

    /**
     * @param since 누적의 시작 — 공구 시작 시각
     * @param now   기준 시각(「집계 중」 칸 = 이 시각의 시)
     */
    public LiveSales read(Long groupBuyId, LocalDateTime since, LocalDateTime now) {
        LocalDateTime todayStart = KstDates.startOfDay(KstDates.today(now));
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime yesterdaySameTime = now.minusDays(1);

        Set<Long> todayOrders = new HashSet<>();
        Set<Long> yesterdayOrders = new HashSet<>();
        List<Set<Long>> hourOrders = new ArrayList<>(HOURS);
        long[] hourAmounts = new long[HOURS];
        for (int hour = 0; hour < HOURS; hour++) {
            hourOrders.add(new HashSet<>());
        }
        long paidToday = 0;
        for (Object[] row : orderProductRepository.findPaidLines(groupBuyId, yesterdayStart, now)) {
            Long orderId = (Long) row[0];
            LocalDateTime paidAt = (LocalDateTime) row[1];
            long amount = row[2] instanceof Number n ? n.longValue() : 0L;
            if (!paidAt.isBefore(todayStart)) {
                todayOrders.add(orderId);
                paidToday += amount;
                int hour = KstDates.toKst(paidAt).getHour();
                hourOrders.get(hour).add(orderId);
                hourAmounts[hour] += amount;
            } else if (!paidAt.isAfter(yesterdaySameTime)) {
                yesterdayOrders.add(orderId);
            }
        }

        long ordersCumulative = 0;
        long paidCumulative = 0;
        long soldQuantity = 0;
        List<Object[]> totals = orderProductRepository.sumPaid(groupBuyId, since, now);
        if (!totals.isEmpty() && totals.get(0) != null) {
            Object[] row = totals.get(0);
            ordersCumulative = row[0] instanceof Number n ? n.longValue() : 0L;
            paidCumulative = row[1] instanceof Number n ? n.longValue() : 0L;
            soldQuantity = row[2] instanceof Number n ? n.longValue() : 0L;
        }

        List<HourBucket> hourly = new ArrayList<>(HOURS);
        for (int hour = 0; hour < HOURS; hour++) {
            hourly.add(new HourBucket(hour, hourOrders.get(hour).size(), hourAmounts[hour], hour == KstDates.toKst(now).getHour()));
        }
        return new LiveSales(todayOrders.size(), yesterdayOrders.size(), paidToday, ordersCumulative, paidCumulative,
                soldQuantity, hourly);
    }

    /**
     * 확정 기준(취소 · 반품 반영) 기간 실적 — 스튜디오 실시간 띠의 「오늘 +」(1017 설계서 5-2). 누적은 공구 관리 잠정 실적
     * ({@link OrderGroupBuySalesReader#readSales})과 같은 정의라, 오늘 분도 같은 제외 조건으로 센다.
     */
    public ConfirmedSales readConfirmedSince(Long groupBuyId, LocalDateTime since) {
        List<Object[]> rows = orderProductRepository.sumSalesByGroupBuySince(groupBuyId, OrderStatus.PAID_OR_LATER, since);
        if (rows.isEmpty() || rows.get(0) == null) {
            return new ConfirmedSales(0, 0);
        }
        Object[] row = rows.get(0);
        return new ConfirmedSales(row[0] instanceof Number n ? n.longValue() : 0L,
                row[1] instanceof Number n ? n.longValue() : 0L);
    }

    public record ConfirmedSales(long orders, long amount) {
    }

    /**
     * @param ordersYesterdaySameTime 어제 00:00 ~ 어제 이 시각
     * @param soldQuantity            결제 기준 판매 수량(취소 전 포함)
     */
    public record LiveSales(long ordersToday, long ordersYesterdaySameTime, long paidToday, long ordersCumulative,
                            long paidCumulative, long soldQuantity, List<HourBucket> hourly) {
    }

    /** @param collecting 지금 시각의 칸 — 「집계 중」 */
    public record HourBucket(int hour, long orders, long amount, boolean collecting) {
    }
}

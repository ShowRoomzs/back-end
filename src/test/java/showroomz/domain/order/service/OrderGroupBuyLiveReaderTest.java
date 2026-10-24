package showroomz.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 진행 중 공구 실시간(1017 설계서 4-2) — 결제 기준 · 취소 전 포함 줄을 이틀 치 읽어 오늘 · 어제 같은 시각 · 시간대 24칸으로 나눈다.
 * 한 주문의 여러 줄은 주문 1건이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderGroupBuyLiveReader — 오늘 · 어제 같은 시각 · 시간대")
class OrderGroupBuyLiveReaderTest {

    private static final Long GROUP_BUY = 9L;
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-22T14:30:00");
    private static final LocalDateTime SINCE = LocalDateTime.parse("2026-10-19T10:00:00");

    @Mock private OrderProductRepository orderProductRepository;
    @InjectMocks private OrderGroupBuyLiveReader reader;

    private static Object[] line(long orderId, String paidAt, long amount) {
        return new Object[]{orderId, LocalDateTime.parse(paidAt), amount};
    }

    @Test
    @DisplayName("오늘 주문 · 금액 — 한 주문의 두 줄은 1건 · 금액은 줄 합")
    void todayCountsDistinctOrders() {
        List<Object[]> lines = new ArrayList<>();
        lines.add(line(1, "2026-10-22T09:10:00", 27_200));
        lines.add(line(1, "2026-10-22T09:10:00", 24_000));
        lines.add(line(2, "2026-10-22T14:05:00", 27_200));
        given(orderProductRepository.findPaidLines(GROUP_BUY, LocalDateTime.parse("2026-10-21T00:00:00"), NOW))
                .willReturn(lines);
        given(orderProductRepository.sumPaid(GROUP_BUY, SINCE, NOW)).willReturn(List.<Object[]>of(new Object[]{10L, 300_000L, 14L}));

        OrderGroupBuyLiveReader.LiveSales sales = reader.read(GROUP_BUY, SINCE, NOW);

        assertThat(sales.ordersToday()).isEqualTo(2);
        assertThat(sales.paidToday()).isEqualTo(78_400);
        assertThat(sales.ordersCumulative()).isEqualTo(10);
        assertThat(sales.paidCumulative()).isEqualTo(300_000);
        assertThat(sales.soldQuantity()).isEqualTo(14);
    }

    @Test
    @DisplayName("어제 같은 시각 — 어제 00:00 ~ 어제 14:30 까지만(14:31 은 빠진다)")
    void yesterdaySameTimeBoundary() {
        List<Object[]> lines = new ArrayList<>();
        lines.add(line(11, "2026-10-21T00:00:00", 1_000));
        lines.add(line(12, "2026-10-21T14:30:00", 1_000));
        lines.add(line(13, "2026-10-21T14:31:00", 1_000));
        given(orderProductRepository.findPaidLines(GROUP_BUY, LocalDateTime.parse("2026-10-21T00:00:00"), NOW))
                .willReturn(lines);
        given(orderProductRepository.sumPaid(GROUP_BUY, SINCE, NOW)).willReturn(List.of());

        OrderGroupBuyLiveReader.LiveSales sales = reader.read(GROUP_BUY, SINCE, NOW);

        assertThat(sales.ordersYesterdaySameTime()).isEqualTo(2);
        assertThat(sales.ordersToday()).isZero();
        assertThat(sales.ordersCumulative()).isZero();
    }

    @Test
    @DisplayName("시간대 24칸 — 결제 시각의 시로 · 지금 시각(14시) 칸만 집계 중 · 어제 줄은 넣지 않는다")
    void hourlyBuckets() {
        List<Object[]> lines = new ArrayList<>();
        lines.add(line(1, "2026-10-22T00:05:00", 1_000));
        lines.add(line(2, "2026-10-22T14:00:00", 2_000));
        lines.add(line(3, "2026-10-22T14:29:59", 3_000));
        lines.add(line(4, "2026-10-21T14:00:00", 9_000));
        given(orderProductRepository.findPaidLines(GROUP_BUY, LocalDateTime.parse("2026-10-21T00:00:00"), NOW))
                .willReturn(lines);
        given(orderProductRepository.sumPaid(GROUP_BUY, SINCE, NOW)).willReturn(List.of());

        List<OrderGroupBuyLiveReader.HourBucket> hourly = reader.read(GROUP_BUY, SINCE, NOW).hourly();

        assertThat(hourly).hasSize(24);
        assertThat(hourly.get(0).orders()).isEqualTo(1);
        assertThat(hourly.get(14).orders()).isEqualTo(2);
        assertThat(hourly.get(14).amount()).isEqualTo(5_000);
        assertThat(hourly.stream().filter(OrderGroupBuyLiveReader.HourBucket::collecting).map(OrderGroupBuyLiveReader.HourBucket::hour))
                .containsExactly(14);
        assertThat(hourly.stream().mapToLong(OrderGroupBuyLiveReader.HourBucket::orders).sum()).isEqualTo(3);
    }

    @Test
    @DisplayName("확정 기준 「오늘 +」 — 결과 없으면 0 · [주문 수, 금액]")
    void confirmedSince() {
        LocalDateTime todayStart = NOW.toLocalDate().atStartOfDay();
        given(orderProductRepository.sumSalesByGroupBuySince(GROUP_BUY, OrderStatus.PAID_OR_LATER, todayStart))
                .willReturn(List.of());
        assertThat(reader.readConfirmedSince(GROUP_BUY, todayStart)).isEqualTo(new OrderGroupBuyLiveReader.ConfirmedSales(0, 0));

        given(orderProductRepository.sumSalesByGroupBuySince(GROUP_BUY, OrderStatus.PAID_OR_LATER, todayStart))
                .willReturn(List.<Object[]>of(new Object[]{3L, 81_600L}));
        assertThat(reader.readConfirmedSince(GROUP_BUY, todayStart))
                .isEqualTo(new OrderGroupBuyLiveReader.ConfirmedSales(3, 81_600));
    }
}

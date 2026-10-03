package showroomz.api.seller.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.repository.SellerOrderSearchCondition;
import showroomz.domain.order.type.OrderDateBasis;
import showroomz.domain.order.type.OrderSearchType;
import showroomz.domain.order.type.OrderSortType;
import showroomz.domain.order.type.OrderTab;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("목록 검색 조건 — 탭이 기본 기간·기본 정렬을 소유하고 1년 상한은 서버가 끝낸다(34 설계서 1-3)")
class SellerOrderSearchConditionTest {

    private static final Long MARKET_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 15, 30);

    private final SellerOrderQueryService service = new SellerOrderQueryService(
            null, null, null, null, null, null, null, new OrderProperties());

    @Test
    @DisplayName("모두 생략하면 전체 탭 · 결제일 기준 · 최근 30일(자정~23:59:59.999) · 최신순")
    void defaults() {
        SellerOrderSearchCondition condition = build(null, null, null, null);

        assertThat(condition.marketId()).isEqualTo(MARKET_ID);
        assertThat(condition.tab()).isEqualTo(OrderTab.ALL);
        assertThat(condition.dateBasis()).isEqualTo(OrderDateBasis.PAID);
        assertThat(condition.from()).isEqualTo(LocalDate.of(2026, 9, 3).atStartOfDay());
        assertThat(condition.to()).isEqualTo(LocalDate.of(2026, 10, 3).atTime(LocalTime.MAX));
        assertThat(condition.sort()).isEqualTo(OrderSortType.LATEST_FIRST);
    }

    @Test
    @DisplayName("작업 큐 3탭은 7일 · 오래된순 — 조회 탭은 30일 · 최신순")
    void tabOwnsPeriodAndSort() {
        for (OrderTab tab : new OrderTab[]{OrderTab.NEW, OrderTab.PREPARING, OrderTab.CANCEL_REQUESTED}) {
            SellerOrderSearchCondition condition = build(tab, null, null, null);
            assertThat(condition.from()).as(tab.name()).isEqualTo(LocalDate.of(2026, 9, 26).atStartOfDay());
            assertThat(condition.sort()).as(tab.name()).isEqualTo(OrderSortType.OLDEST_FIRST);
        }
        for (OrderTab tab : new OrderTab[]{OrderTab.SHIPPING, OrderTab.RETURNING, OrderTab.DELIVERED,
                OrderTab.CONFIRMED, OrderTab.CANCELLED}) {
            SellerOrderSearchCondition condition = build(tab, null, null, null);
            assertThat(condition.from()).as(tab.name()).isEqualTo(LocalDate.of(2026, 9, 3).atStartOfDay());
            assertThat(condition.sort()).as(tab.name()).isEqualTo(OrderSortType.LATEST_FIRST);
        }
    }

    @Test
    @DisplayName("파라미터가 오면 탭 기본값보다 우선한다 — from 만 주면 to 는 오늘")
    void explicitValuesWin() {
        SellerOrderSearchCondition condition = service.buildCondition(MARKET_ID, OrderTab.NEW, OrderDateBasis.SHIPPED,
                LocalDate.of(2026, 8, 1), null, OrderSearchType.TRACKING_NUMBER, "1234", OrderSortType.SHIP_DUE_ASC, NOW);

        assertThat(condition.dateBasis()).isEqualTo(OrderDateBasis.SHIPPED);
        assertThat(condition.from()).isEqualTo(LocalDate.of(2026, 8, 1).atStartOfDay());
        assertThat(condition.to()).isEqualTo(LocalDate.of(2026, 10, 3).atTime(LocalTime.MAX));
        assertThat(condition.sort()).isEqualTo(OrderSortType.SHIP_DUE_ASC);
        assertThat(condition.searchType()).isEqualTo(OrderSearchType.TRACKING_NUMBER);
        assertThat(condition.keyword()).isEqualTo("1234");
    }

    @Test
    @DisplayName("to 만 주면 from 은 to 기준 탭 기본 기간 전")
    void fromDefaultsRelativeToTo() {
        SellerOrderSearchCondition condition = build(OrderTab.NEW, null, LocalDate.of(2026, 6, 30), null);

        assertThat(condition.from()).isEqualTo(LocalDate.of(2026, 6, 23).atStartOfDay());
        assertThat(condition.to()).isEqualTo(LocalDate.of(2026, 6, 30).atTime(LocalTime.MAX));
    }

    @Test
    @DisplayName("기간 상한 365일 — 정확히 365일은 되고 366일은 ORDER_SEARCH_RANGE_EXCEEDED")
    void rangeLimit() {
        LocalDate to = LocalDate.of(2026, 10, 3);

        assertThat(build(OrderTab.ALL, to.minusDays(365), to, null).from()).isEqualTo(to.minusDays(365).atStartOfDay());
        assertThatThrownBy(() -> build(OrderTab.ALL, to.minusDays(366), to, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_SEARCH_RANGE_EXCEEDED);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 ORDER_SEARCH_RANGE_INVALID — 같은 날은 된다 · from 만 미래로 줘도 역전이다(Q-01)")
    void reversedRange() {
        LocalDate day = LocalDate.of(2026, 9, 20);

        assertThat(build(OrderTab.ALL, day, day, null).from()).isEqualTo(day.atStartOfDay());
        assertThatThrownBy(() -> build(OrderTab.ALL, day.plusDays(1), day, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_SEARCH_RANGE_INVALID);
        assertThatThrownBy(() -> build(OrderTab.ALL, LocalDate.of(2026, 10, 4), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_SEARCH_RANGE_INVALID);
    }

    @Test
    @DisplayName("상한은 설정값을 따른다")
    void rangeLimitFromProperties() {
        OrderProperties properties = new OrderProperties();
        properties.setSearchRangeMaxDays(30);
        SellerOrderQueryService narrow = new SellerOrderQueryService(null, null, null, null, null, null, null, properties);

        assertThatThrownBy(() -> narrow.buildCondition(MARKET_ID, OrderTab.ALL, null, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 3), null, null, null, NOW))
                .isInstanceOf(BusinessException.class);
    }

    private SellerOrderSearchCondition build(OrderTab tab, LocalDate from, LocalDate to, OrderSortType sort) {
        return service.buildCondition(MARKET_ID, tab, null, from, to, null, null, sort, NOW);
    }
}

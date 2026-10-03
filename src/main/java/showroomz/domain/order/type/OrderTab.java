package showroomz.domain.order.type;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 파트너센터 주문 목록 탭 9종 — 작업 큐 3 · 조회 6(34 설계서 1-3). 탭이 기본 기간·기본 정렬까지 소유한다.
 *
 * <p>취소 요청은 이행 상태가 아니라 오버레이다 — {@code CANCEL_REQUESTED} 탭은 「검토 중 요청 존재」,
 * {@code NEW}·{@code PREPARING} 탭은 「요청 없음」 조건이 상태 필터에 더해진다.
 *
 * <p>기간을 탭 성격으로 나누는 이유 — 처리할 일은 최근 것이지만 찾는 일은 과거를 뒤지는 행동이다(§34-1).
 */
@Getter
public enum OrderTab {

    ALL(EnumSet.complementOf(EnumSet.of(FulfillmentStatus.PENDING)), PendingCancelFilter.ANY, false, 30),
    NEW(EnumSet.of(FulfillmentStatus.NEW), PendingCancelFilter.NONE, true, 7),
    PREPARING(EnumSet.of(FulfillmentStatus.PREPARING), PendingCancelFilter.NONE, true, 7),
    /** 항목 하나만 요청돼도 하위주문 전체가 이 탭으로 — 남은 항목을 발송하면서 요청을 놓치지 않게(§34-8). */
    CANCEL_REQUESTED(EnumSet.of(FulfillmentStatus.NEW, FulfillmentStatus.PREPARING), PendingCancelFilter.EXISTS, true, 7),
    SHIPPING(EnumSet.of(FulfillmentStatus.SHIPPING), PendingCancelFilter.ANY, false, 30),
    RETURNING(EnumSet.of(FulfillmentStatus.RETURNING), PendingCancelFilter.ANY, false, 30),
    DELIVERED(EnumSet.of(FulfillmentStatus.DELIVERED), PendingCancelFilter.ANY, false, 30),
    CONFIRMED(EnumSet.of(FulfillmentStatus.CONFIRMED), PendingCancelFilter.ANY, false, 30),
    CANCELLED(EnumSet.of(FulfillmentStatus.CANCELLED), PendingCancelFilter.ANY, false, 30);

    public enum PendingCancelFilter { ANY, NONE, EXISTS }

    private final Set<FulfillmentStatus> statuses;
    private final PendingCancelFilter pendingCancelFilter;
    /** 작업 큐 3탭 — 기본 정렬 오래된순(시안 정정 #11). 조회 탭은 최신순. */
    private final boolean workQueue;
    private final int defaultPeriodDays;

    OrderTab(Set<FulfillmentStatus> statuses, PendingCancelFilter pendingCancelFilter, boolean workQueue,
             int defaultPeriodDays) {
        this.statuses = statuses;
        this.pendingCancelFilter = pendingCancelFilter;
        this.workQueue = workQueue;
        this.defaultPeriodDays = defaultPeriodDays;
    }

    public OrderSortType defaultSort() {
        return workQueue ? OrderSortType.OLDEST_FIRST : OrderSortType.LATEST_FIRST;
    }
}

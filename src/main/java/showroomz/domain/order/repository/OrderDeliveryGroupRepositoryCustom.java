package showroomz.domain.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;

import java.util.Map;

public interface OrderDeliveryGroupRepositoryCustom {

    Page<SellerOrderRow> searchForSeller(SellerOrderSearchCondition condition, Pageable pageable);

    /** 어드민 주문 조회(06a) — 조건에 맞는 하위주문이 있는 주문 id, 결제 최신순. */
    Page<Long> searchOrderIdsForAdmin(AdminOrderSearchCondition condition, Pageable pageable);

    /** 어드민 탭 건수 — 같은 기본 조건(브랜드 · 기간 · 검색)에서 탭별 주문 수. */
    long countOrdersForAdmin(AdminOrderSearchCondition condition);

    /**
     * 상태 셀렉트 건수(46 수정계획 4-3) — 하위주문 상태별 주문 수. 상태가 갈린 주문은 상태마다 센다 — 각 값은
     * {@code condition}에 그 상태를 넣은 {@link #countOrdersForAdmin}과 같다. 0건인 상태는 키가 없다.
     */
    Map<FulfillmentStatus, Long> countOrdersByStatusForAdmin(AdminOrderSearchCondition condition);

    /** 이상 유형 셀렉트 건수 — 배송 이상 배지별 주문 수. 각 값은 그 배지를 넣은 {@link #countOrdersForAdmin}과 같다. */
    Map<TrackingAlert, Long> countOrdersByTrackingAlertForAdmin(AdminOrderSearchCondition condition);
}

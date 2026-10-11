package showroomz.domain.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderDeliveryGroupRepositoryCustom {

    Page<SellerOrderRow> searchForSeller(SellerOrderSearchCondition condition, Pageable pageable);

    /** 어드민 주문 조회(06a) — 조건에 맞는 하위주문이 있는 주문 id, 결제 최신순. */
    Page<Long> searchOrderIdsForAdmin(AdminOrderSearchCondition condition, Pageable pageable);

    /** 어드민 탭 건수 — 같은 기본 조건(브랜드 · 기간 · 검색)에서 탭별 주문 수. */
    long countOrdersForAdmin(AdminOrderSearchCondition condition);
}

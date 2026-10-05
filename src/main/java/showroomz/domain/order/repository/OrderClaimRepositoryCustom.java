package showroomz.domain.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.order.entity.OrderClaim;

public interface OrderClaimRepositoryCustom {

    /** 파트너센터 목록 — 요청 · 하위주문 · 주문 · 주문 항목을 함께 읽는다. */
    Page<OrderClaim> searchForSeller(SellerClaimSearchCondition condition, Pageable pageable);
}

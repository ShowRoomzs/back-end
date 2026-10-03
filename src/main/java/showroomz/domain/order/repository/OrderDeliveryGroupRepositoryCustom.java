package showroomz.domain.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderDeliveryGroupRepositoryCustom {

    Page<SellerOrderRow> searchForSeller(SellerOrderSearchCondition condition, Pageable pageable);
}

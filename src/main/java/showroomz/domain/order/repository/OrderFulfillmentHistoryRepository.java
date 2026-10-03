package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderFulfillmentHistory;

import java.util.List;

/** append-only — UPDATE·DELETE 메서드를 두지 않는다(34 설계서 1-4). */
public interface OrderFulfillmentHistoryRepository extends JpaRepository<OrderFulfillmentHistory, Long> {

    @Query("SELECT h FROM OrderFulfillmentHistory h WHERE h.deliveryGroup.id = :deliveryGroupId "
            + "ORDER BY h.occurredAt DESC, h.id DESC")
    List<OrderFulfillmentHistory> findByDeliveryGroupId(@Param("deliveryGroupId") Long deliveryGroupId);
}

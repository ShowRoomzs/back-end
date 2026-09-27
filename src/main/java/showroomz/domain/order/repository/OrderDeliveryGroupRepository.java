package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderDeliveryGroup;

import java.util.List;

public interface OrderDeliveryGroupRepository extends JpaRepository<OrderDeliveryGroup, Long> {

    @Query("SELECT g FROM OrderDeliveryGroup g LEFT JOIN FETCH g.groupBuy WHERE g.order.id = :orderId ORDER BY g.id ASC")
    List<OrderDeliveryGroup> findByOrderId(@Param("orderId") Long orderId);
}

package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}

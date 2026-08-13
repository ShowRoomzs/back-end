package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.Order;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 문의에 연결된 주문 카드용 — 주문 상품까지 한 번에 조회한다 */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderProducts WHERE o.id IN :orderIds")
    List<Order> findAllByIdInWithProducts(@Param("orderIds") Collection<Long> orderIds);
}

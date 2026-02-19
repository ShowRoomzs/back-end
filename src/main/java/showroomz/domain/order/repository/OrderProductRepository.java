package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.OrderProductStatus;

import java.util.List;

public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {

    @Query("""
            SELECT op FROM OrderProduct op
            JOIN op.order o
            WHERE o.user.id = :userId
              AND op.status = :status
              AND op.review IS NULL
            ORDER BY op.orderDate DESC
            """)
    List<OrderProduct> findWritableByUserId(
            @Param("userId") Long userId,
            @Param("status") OrderProductStatus status);

    boolean existsByIdAndOrder_User_Id(Long orderProductId, Long userId);
}

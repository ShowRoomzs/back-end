package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.OrderProductStatus;

import java.util.Collection;
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

    /**
     * C15-4 탈퇴 차단 판정 — 아직 끝나지 않은 주문 상품 수.
     * 구매 확정(PURCHASE_CONFIRMED)·취소(CANCELLED)를 뺀 나머지가 "진행 중"이다.
     * TODO: 교환·환불 기간까지 진행 중으로 볼지(설계 미결정) 정해지면 조건을 넓힌다.
     */
    @Query("""
            SELECT COUNT(op) FROM OrderProduct op
            JOIN op.order o
            WHERE o.user.id = :userId
              AND op.status NOT IN :finishedStatuses
            """)
    long countOngoingByUserId(
            @Param("userId") Long userId,
            @Param("finishedStatuses") Collection<OrderProductStatus> finishedStatuses);
}

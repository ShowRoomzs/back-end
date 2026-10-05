package showroomz.domain.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderRefundTask;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 환불 집행 큐 — 주문 모듈은 INSERT 만 한다. 집행·조회는 어드민 거래 관리 몫이다(34 설계서 1-9).
 * 반품 클레임의 집행 완료 기록({@code OrderClaimService.completeRefund})만 행을 잠가 닫는다.
 */
public interface OrderRefundTaskRepository extends JpaRepository<OrderRefundTask, Long> {

    /** 소비자 앱 「환불 처리 중」 판정(C10 설계서 1-2 · 2-4 #4) — 집행 전 환불이 남은 배송 그룹. */
    @Query("SELECT DISTINCT t.deliveryGroup.id FROM OrderRefundTask t WHERE t.order.id IN :orderIds "
            + "AND t.status = showroomz.domain.order.type.RefundTaskStatus.PENDING")
    List<Long> findPendingGroupIdsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /** 환불 집행이 같은 큐 행을 두 번 닫지 않게 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM OrderRefundTask t WHERE t.id = :id")
    Optional<OrderRefundTask> findForUpdate(@Param("id") Long id);
}

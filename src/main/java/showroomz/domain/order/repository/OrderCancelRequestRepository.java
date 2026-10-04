package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderCancelRequest;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderCancelRequestRepository extends JpaRepository<OrderCancelRequest, Long> {

    /** 목록 오버레이 — 페이지의 그룹 id 로 IN 1번. 대상 항목까지 올린다(C11 경고 톤·요약 문구). */
    @Query("SELECT DISTINCT r FROM OrderCancelRequest r LEFT JOIN FETCH r.items i LEFT JOIN FETCH i.orderProduct "
            + "WHERE r.deliveryGroup.id IN :deliveryGroupIds "
            + "AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING")
    List<OrderCancelRequest> findPendingByDeliveryGroupIds(@Param("deliveryGroupIds") Collection<Long> deliveryGroupIds);

    /**
     * 소비자 앱 주문 내역·상세(C10 설계서 2-4 #3) — 검토 중(「취소 요청중」)과 반려(반려 줄)만. {@code order_id}
     * 비정규화 컬럼이 이 용도다.
     */
    @Query("SELECT DISTINCT r FROM OrderCancelRequest r LEFT JOIN FETCH r.items "
            + "WHERE r.order.id IN :orderIds "
            + "AND r.status IN (showroomz.domain.order.type.CancelRequestStatus.PENDING, "
            + "    showroomz.domain.order.type.CancelRequestStatus.REJECTED)")
    List<OrderCancelRequest> findOpenOrRejectedByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    @Query("SELECT DISTINCT r FROM OrderCancelRequest r JOIN FETCH r.deliveryGroup g JOIN FETCH g.order "
            + "LEFT JOIN FETCH r.items i LEFT JOIN FETCH i.orderProduct WHERE r.id = :id")
    Optional<OrderCancelRequest> findWithGroup(@Param("id") Long id);

    /**
     * 다건 액션 0행 사유 판정 — <b>내 마켓의</b> 하위주문에 검토 중 요청이 있는가. 마켓을 보지 않으면 타 브랜드가 남의
     * 하위주문 id 로 다건 액션을 던져 「검토 중 취소 요청이 있다」는 사실을 알아낼 수 있다(4-4 존재 비노출).
     */
    @Query("SELECT COUNT(r) > 0 FROM OrderCancelRequest r WHERE r.deliveryGroup.id = :deliveryGroupId "
            + "AND r.deliveryGroup.market.id = :marketId "
            + "AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING")
    boolean existsPendingOwned(@Param("deliveryGroupId") Long deliveryGroupId, @Param("marketId") Long marketId);

    /**
     * 승인 선점 — PENDING 이고 <b>그룹이 작업 큐(NEW·PREPARING)</b>일 때만. 0행 = 이미 처리됨 또는 그룹 상태 변경.
     * 발송 뒤 취소는 반품 경로다 — 여기서 승인되면 배송은 가는데 환불 큐가 서고 왕복 배송비 규칙을 우회한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderCancelRequest r SET r.status = showroomz.domain.order.type.CancelRequestStatus.APPROVED, "
            + "r.decidedAt = :now, r.decidedBy = :sellerId "
            + "WHERE r.id = :id AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING "
            + "AND EXISTS (SELECT g FROM OrderDeliveryGroup g WHERE g.id = r.deliveryGroup.id "
            + "    AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "        showroomz.domain.order.type.FulfillmentStatus.PREPARING))")
    int approve(@Param("id") Long id, @Param("sellerId") Long sellerId, @Param("now") LocalDateTime now);

    /** 거부 — 사유 필수(소비자에게 그대로 전달 · 약관 제18조①)는 서비스가 검증한다. 그룹 조건은 승인과 같다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderCancelRequest r SET r.status = showroomz.domain.order.type.CancelRequestStatus.REJECTED, "
            + "r.decidedAt = :now, r.decidedBy = :sellerId, r.rejectReason = :rejectReason "
            + "WHERE r.id = :id AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING "
            + "AND EXISTS (SELECT g FROM OrderDeliveryGroup g WHERE g.id = r.deliveryGroup.id "
            + "    AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "        showroomz.domain.order.type.FulfillmentStatus.PREPARING))")
    int reject(@Param("id") Long id, @Param("sellerId") Long sellerId, @Param("rejectReason") String rejectReason,
               @Param("now") LocalDateTime now);

    /** 승인·거부 0행의 사유 판정 — 요청이 아직 PENDING 이면 그룹 쪽이 바뀐 것이다. */
    @Query("SELECT r.status FROM OrderCancelRequest r WHERE r.id = :id")
    Optional<showroomz.domain.order.type.CancelRequestStatus> findStatus(@Param("id") Long id);

    /** 소비자 전액 취소 수렴(설계서 5-2) — 그 주문에 걸린 검토 중 요청을 시스템이 닫는다(결정자 없음). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderCancelRequest r SET r.status = showroomz.domain.order.type.CancelRequestStatus.VOIDED, "
            + "r.decidedAt = :now "
            + "WHERE r.order.id = :orderId AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING")
    int voidPendingByOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);
}

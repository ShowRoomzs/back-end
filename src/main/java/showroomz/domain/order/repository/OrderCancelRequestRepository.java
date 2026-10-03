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

    @Query("SELECT DISTINCT r FROM OrderCancelRequest r JOIN FETCH r.deliveryGroup g JOIN FETCH g.order "
            + "LEFT JOIN FETCH r.items i LEFT JOIN FETCH i.orderProduct WHERE r.id = :id")
    Optional<OrderCancelRequest> findWithGroup(@Param("id") Long id);

    boolean existsByDeliveryGroup_IdAndStatus(Long deliveryGroupId,
                                              showroomz.domain.order.type.CancelRequestStatus status);

    /** 승인 선점 — PENDING 에서만. 0행 = 이미 처리됨(경합의 정상 결과). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderCancelRequest r SET r.status = showroomz.domain.order.type.CancelRequestStatus.APPROVED, "
            + "r.decidedAt = :now, r.decidedBy = :sellerId "
            + "WHERE r.id = :id AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING")
    int approve(@Param("id") Long id, @Param("sellerId") Long sellerId, @Param("now") LocalDateTime now);

    /** 거부 — 사유 필수(소비자에게 그대로 전달 · 약관 제18조①)는 서비스가 검증한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderCancelRequest r SET r.status = showroomz.domain.order.type.CancelRequestStatus.REJECTED, "
            + "r.decidedAt = :now, r.decidedBy = :sellerId, r.rejectReason = :rejectReason "
            + "WHERE r.id = :id AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING")
    int reject(@Param("id") Long id, @Param("sellerId") Long sellerId, @Param("rejectReason") String rejectReason,
               @Param("now") LocalDateTime now);
}

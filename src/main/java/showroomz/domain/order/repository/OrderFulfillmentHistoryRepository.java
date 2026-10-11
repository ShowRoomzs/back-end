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

    /** 그 사건을 그 주체가 남긴 하위주문 — 어드민 환불 경로 라벨(「운영자 대행 직권 취소」)의 일괄 판정. */
    @Query("SELECT DISTINCT h.deliveryGroup.id FROM OrderFulfillmentHistory h WHERE h.deliveryGroup.id IN :groupIds "
            + "AND h.eventType = :eventType AND h.actorType = :actorType")
    List<Long> findGroupIdsWithEvent(@Param("groupIds") java.util.Collection<Long> groupIds,
                                     @Param("eventType") showroomz.domain.order.type.FulfillmentEventType eventType,
                                     @Param("actorType") showroomz.domain.order.type.FulfillmentActorType actorType);
}

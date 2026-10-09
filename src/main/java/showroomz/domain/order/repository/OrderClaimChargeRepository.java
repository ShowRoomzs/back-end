package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimCharge;

import java.util.Collection;
import java.util.List;

public interface OrderClaimChargeRepository extends JpaRepository<OrderClaimCharge, Long> {

    @Query("SELECT h FROM OrderClaimCharge h WHERE h.collection.id = :collectionId ORDER BY h.id ASC")
    List<OrderClaimCharge> findByCollectionId(@Param("collectionId") Long collectionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OrderClaimCharge h WHERE h.collection.id = :collectionId")
    int deleteByCollectionId(@Param("collectionId") Long collectionId);

    @Query("SELECT h FROM OrderClaimCharge h WHERE h.collection.id IN :collectionIds ORDER BY h.id ASC")
    List<OrderClaimCharge> findByCollectionIds(@Param("collectionIds") Collection<Long> collectionIds);

    /**
     * 공구 하위주문 클레임의 재발송비 — [건수, 합](44 어드민 설계서 2-2). 소비자가 낸 돈(결제 · 환불액 차감 · 교환 결제분 충당)만 —
     * 브랜드 부담(WAIVED) · 소멸 · 결제 취소 · 결제 대기는 뺀다. 브랜드 수취액에 가산된다(§41-4 ⑦).
     */
    @Query("SELECT COUNT(h), COALESCE(SUM(h.amount), 0) FROM OrderClaimCharge h JOIN h.collection c "
            + "WHERE c.deliveryGroup.groupBuy.id = :groupBuyId AND h.status IN :statuses")
    List<Object[]> sumByGroupBuy(@Param("groupBuyId") Long groupBuyId,
                                 @Param("statuses") Collection<showroomz.domain.order.type.ClaimChargeStatus> statuses);
}

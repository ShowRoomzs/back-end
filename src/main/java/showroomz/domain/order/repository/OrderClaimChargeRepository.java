package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaimCharge;

import java.util.Collection;
import java.util.List;

public interface OrderClaimChargeRepository extends JpaRepository<OrderClaimCharge, Long> {

    @Query("SELECT h FROM OrderClaimCharge h WHERE h.collection.id = :collectionId ORDER BY h.id ASC")
    List<OrderClaimCharge> findByCollectionId(@Param("collectionId") Long collectionId);

    @Query("SELECT h FROM OrderClaimCharge h WHERE h.collection.id IN :collectionIds ORDER BY h.id ASC")
    List<OrderClaimCharge> findByCollectionIds(@Param("collectionIds") Collection<Long> collectionIds);
}

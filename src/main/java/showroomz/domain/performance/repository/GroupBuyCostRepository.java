package showroomz.domain.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.performance.entity.GroupBuyCost;

import java.util.Optional;

public interface GroupBuyCostRepository extends JpaRepository<GroupBuyCost, Long> {

    @Query("SELECT c FROM GroupBuyCost c LEFT JOIN FETCH c.items WHERE c.groupBuyId = :groupBuyId")
    Optional<GroupBuyCost> findWithItemsByGroupBuyId(@Param("groupBuyId") Long groupBuyId);
}

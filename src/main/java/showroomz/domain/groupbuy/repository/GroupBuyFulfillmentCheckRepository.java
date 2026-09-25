package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.type.FulfillmentSide;

import java.util.List;

/** 측별 1회 · 불가역 — 수정·삭제 메서드를 두지 않는다(설계서 1-9). */
public interface GroupBuyFulfillmentCheckRepository extends JpaRepository<GroupBuyFulfillmentCheck, Long> {

    List<GroupBuyFulfillmentCheck> findByGroupBuyId(Long groupBuyId);

    boolean existsByGroupBuyIdAndCheckerSide(Long groupBuyId, FulfillmentSide checkerSide);
}

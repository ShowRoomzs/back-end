package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;

import java.util.List;

/** append-only — 수정·삭제 메서드를 두지 않는다(설계서 1-4). */
public interface GroupBuyHistoryRepository extends JpaRepository<GroupBuyHistory, Long> {

    List<GroupBuyHistory> findByGroupBuyIdOrderByOccurredAtAscIdAsc(Long groupBuyId);
}

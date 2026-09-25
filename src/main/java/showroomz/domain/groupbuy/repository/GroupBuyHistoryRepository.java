package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;
import showroomz.domain.groupbuy.type.GroupBuyEventType;

import java.util.Collection;
import java.util.List;

/** append-only — 수정·삭제 메서드를 두지 않는다(설계서 1-4). */
public interface GroupBuyHistoryRepository extends JpaRepository<GroupBuyHistory, Long> {

    List<GroupBuyHistory> findByGroupBuyIdOrderByOccurredAtAscIdAsc(Long groupBuyId);

    /**
     * 화이트리스트 조회 — 스튜디오는 {@code event_type IN (...)}을 쿼리에 박는다. 전량 조회 후 걸러내면
     * 걸러내기 전 목록(브랜드의 소명 등)이 메모리에 존재한다(31 설계 6-2).
     */
    List<GroupBuyHistory> findByGroupBuyIdAndEventTypeInOrderByOccurredAtAscIdAsc(
            Long groupBuyId, Collection<GroupBuyEventType> eventTypes);
}

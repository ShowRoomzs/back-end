package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.CreatorFollowEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface CreatorFollowEventRepository extends JpaRepository<CreatorFollowEvent, Long> {

    /** 기준 시각까지의 이력 전부 — 시각 · id 순. {@code FollowerTimeline}이 메모리에서 판정한다. */
    @Query("SELECT e FROM CreatorFollowEvent e WHERE e.creatorId = :creatorId AND e.occurredAt < :until "
            + "ORDER BY e.occurredAt ASC, e.id ASC")
    List<CreatorFollowEvent> findTimeline(@Param("creatorId") Long creatorId, @Param("until") LocalDateTime until);
}

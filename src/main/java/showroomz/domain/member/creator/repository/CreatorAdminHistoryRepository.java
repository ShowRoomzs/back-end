package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.CreatorAdminHistory;
import showroomz.domain.member.creator.type.CreatorAdminEventType;

import java.util.List;

public interface CreatorAdminHistoryRepository extends JpaRepository<CreatorAdminHistory, Long> {

    List<CreatorAdminHistory> findByCreatorIdOrderByCreatedAtDescIdDesc(Long creatorId);

    List<CreatorAdminHistory> findByCreatorIdAndEventTypeOrderByCreatedAtDesc(Long creatorId,
                                                                              CreatorAdminEventType eventType);
}

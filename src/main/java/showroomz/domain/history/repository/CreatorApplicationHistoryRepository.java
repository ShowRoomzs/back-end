package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.history.entity.CreatorApplicationHistory;

import java.util.List;

public interface CreatorApplicationHistoryRepository extends JpaRepository<CreatorApplicationHistory, Long> {

    List<CreatorApplicationHistory> findByApplication_IdOrderByCreatedAtAsc(Long applicationId);
}

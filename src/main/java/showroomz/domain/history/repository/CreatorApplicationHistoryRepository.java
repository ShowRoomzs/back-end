package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.history.entity.CreatorApplicationHistory;

public interface CreatorApplicationHistoryRepository extends JpaRepository<CreatorApplicationHistory, Long> {
}

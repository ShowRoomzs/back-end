package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.history.entity.UserConsentHistory;

public interface UserConsentHistoryRepository extends JpaRepository<UserConsentHistory, Long> {
}

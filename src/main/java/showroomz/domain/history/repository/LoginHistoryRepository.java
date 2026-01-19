package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.history.entity.LoginHistory;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
}

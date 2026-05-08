package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.history.entity.UserStatusHistory;

@Repository
public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {
}

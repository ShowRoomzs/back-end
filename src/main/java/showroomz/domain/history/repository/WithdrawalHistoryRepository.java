package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.history.entity.WithdrawalHistory;

public interface WithdrawalHistoryRepository extends JpaRepository<WithdrawalHistory, Long> {
}

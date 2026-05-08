package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.history.entity.UserStatusHistory;

import java.util.List;

@Repository
public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {

    List<UserStatusHistory> findByUser_IdOrderByCreatedAtDesc(Long userId);
}

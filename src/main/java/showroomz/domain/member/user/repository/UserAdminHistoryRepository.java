package showroomz.domain.member.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.user.entity.UserAdminHistory;

import java.util.List;

public interface UserAdminHistoryRepository extends JpaRepository<UserAdminHistory, Long> {

    List<UserAdminHistory> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}

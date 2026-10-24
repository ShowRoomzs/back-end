package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.member.user.type.UserStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {

    List<UserStatusHistory> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /** 가장 최근의 그 상태로의 전이 — 어드민 소비자 상세 탈퇴 블록의 「탈퇴 일시」(56 C3 · 처리 이력 「회원 탈퇴」 파생 행과 같은 원천). */
    Optional<UserStatusHistory> findFirstByUser_IdAndNewStatusOrderByCreatedAtDescIdDesc(Long userId, UserStatus newStatus);
}

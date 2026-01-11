package showroomz.domain.recentSerach.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.recentSearch.entitiy.RecentSearch;

import java.util.Optional;

public interface RecentSearchRepository extends JpaRepository<RecentSearch, Long> {
    // 특정 사용자의 최근 검색 기록 조회
    Page<RecentSearch> findByUser(Users user, Pageable pageable);

    // [추가] 삭제 시 본인 확인을 위해 ID와 User로 조회
    Optional<RecentSearch> findByIdAndUser(Long id, Users user);
}

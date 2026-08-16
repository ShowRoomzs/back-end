package showroomz.domain.recentSearch.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.recentSearch.entitiy.RecentSearch;
import showroomz.domain.recentSearch.type.RecentSearchType;

import java.util.Optional;

public interface RecentSearchRepository extends JpaRepository<RecentSearch, Long> {
    // 특정 사용자의 최근 검색 기록 조회 (쇼룸 행의 아바타·핸들까지 한 번에 — N+1 방지)
    @EntityGraph(attributePaths = {"creator"})
    Page<RecentSearch> findByUser(Users user, Pageable pageable);

    // [추가] 삭제 시 본인 확인을 위해 ID와 User로 조회
    Optional<RecentSearch> findByIdAndUser(Long id, Users user);

    // 검색어 기록 upsert용 — 쇼룸 행과 같은 이름이 있어도 섞이지 않도록 타입까지 좁힌다
    Optional<RecentSearch> findByUserAndTypeAndTerm(Users user, RecentSearchType type, String term);

    // 쇼룸 기록 upsert용 — 쇼룸명이 바뀌어도 같은 쇼룸이면 한 행으로 합친다
    Optional<RecentSearch> findByUserAndTypeAndCreator(Users user, RecentSearchType type, Creator creator);

    // 최근 검색 전체 삭제
    void deleteByUser(Users user);
}

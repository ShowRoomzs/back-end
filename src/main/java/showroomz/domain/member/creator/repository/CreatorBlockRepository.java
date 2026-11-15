package showroomz.domain.member.creator.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorBlock;
import showroomz.domain.member.user.entity.Users;

import java.util.List;

public interface CreatorBlockRepository extends JpaRepository<CreatorBlock, Long> {

    boolean existsByUserAndCreator(Users user, Creator creator);

    boolean existsByUser_IdAndCreator_Id(Long userId, Long creatorId);

    void deleteByUserAndCreator(Users user, Creator creator);

    /** C15-4 탈퇴 — 차단 기록 파기. */
    void deleteByUser(Users user);

    /**
     * 피드 · 목록 · 검색에서 뺄 쇼룸 — 차단은 사람이 손으로 누르는 것이라 한 사람당 몇 건이다. 목록을 한 번 읽어
     * {@code creator.id NOT IN (...)} 으로 건다(추천 피드의 팔로우 제외와 같은 방식).
     */
    @Query("SELECT b.creator.id FROM CreatorBlock b WHERE b.user.id = :userId")
    List<Long> findCreatorIdsByUserId(@Param("userId") Long userId);

    /** 차단 목록 — 최근 차단순. 쇼룸 · 계정을 함께 읽는다(카드마다 쇼룸명 · 프로필). */
    @Query(value = "SELECT b FROM CreatorBlock b JOIN FETCH b.creator c JOIN FETCH c.user "
            + "WHERE b.user.id = :userId ORDER BY b.createdAt DESC, b.id DESC",
            countQuery = "SELECT COUNT(b) FROM CreatorBlock b WHERE b.user.id = :userId")
    Page<CreatorBlock> findPageByUserId(@Param("userId") Long userId, Pageable pageable);
}

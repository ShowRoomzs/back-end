package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.user.entity.Users;

import java.util.List;

public interface CreatorFollowRepository extends JpaRepository<CreatorFollow, Long> {
    boolean existsByUserAndCreator(Users user, Creator creator);
    void deleteByUserAndCreator(Users user, Creator creator);

    // 유저가 팔로우한 쇼룸 수
    long countByUser(Users user);

    // 팔로잉 목록 조회 — 정렬(최근 게시물 순)이 서비스 단에서 끝나므로 전체를 한 번에 가져온다
    @Query("SELECT cf FROM CreatorFollow cf " +
           "JOIN FETCH cf.creator c " +
           "JOIN FETCH c.user " +
           "WHERE cf.user = :user")
    List<CreatorFollow> findAllByUserWithCreator(@Param("user") Users user);

    // 팔로잉 피드용 — 팔로우한 쇼룸 ID 목록
    @Query("SELECT cf.creator.id FROM CreatorFollow cf WHERE cf.user.id = :userId")
    List<Long> findCreatorIdsByUserId(@Param("userId") Long userId);
}

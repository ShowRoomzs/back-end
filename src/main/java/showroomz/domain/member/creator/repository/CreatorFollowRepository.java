package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;
import java.util.List;

public interface CreatorFollowRepository extends JpaRepository<CreatorFollow, Long> {
    boolean existsByUserAndCreator(Users user, Creator creator);
    void deleteByUserAndCreator(Users user, Creator creator);

    // 유저가 팔로우한 쇼룸 수
    long countByUser(Users user);

    /** C15-4 탈퇴 — 팔로잉 기록 파기 */
    void deleteByUser(Users user);

    // 팔로잉 목록 조회 — 정렬(최근 게시물 순)이 서비스 단에서 끝나므로 전체를 한 번에 가져온다
    @Query("SELECT cf FROM CreatorFollow cf " +
           "JOIN FETCH cf.creator c " +
           "JOIN FETCH c.user " +
           "WHERE cf.user = :user")
    List<CreatorFollow> findAllByUserWithCreator(@Param("user") Users user);

    // 팔로잉 피드용 — 팔로우한 쇼룸 ID 목록
    @Query("SELECT cf.creator.id FROM CreatorFollow cf WHERE cf.user.id = :userId")
    List<Long> findCreatorIdsByUserId(@Param("userId") Long userId);

    /** §22-4 총 팔로워 — 기간과 무관한 현재 시점 값. */
    long countByCreator_Id(Long creatorId);

    /** §22-4 기간 내 신규 팔로워. 직전 동일 기간에 같은 쿼리를 한 번 더 돌려 증감률을 낸다. */
    @Query("SELECT COUNT(cf) FROM CreatorFollow cf " +
           "WHERE cf.creator.id = :creatorId AND cf.createdAt >= :from AND cf.createdAt < :to")
    long countNewFollowers(@Param("creatorId") Long creatorId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /**
     * §22-4 팔로워 구성 — {성별, 생년월일} 행. 소셜 로그인 동의 항목이라 값이 비어 있을 수 있고,
     * 비어 있는 쪽은 "미확인"으로 분류한다(§22-5 수집 한계). 개인 식별자는 꺼내지 않는다.
     */
    @Query("SELECT u.gender, u.birthday FROM CreatorFollow cf JOIN cf.user u WHERE cf.creator.id = :creatorId")
    List<Object[]> findFollowerDemographics(@Param("creatorId") Long creatorId);
}

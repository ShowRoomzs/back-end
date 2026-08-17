package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostLike;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long>, PostLikeRepositoryCustom {

    Optional<PostLike> findByUserIdAndPostId(Long userId, Long postId);

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    @Query("SELECT pl.post.id FROM PostLike pl WHERE pl.user.id = :userId AND pl.post.id IN :postIds")
    List<Long> findLikedPostIdsByUserIdAndPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    /** §24-7 ① 반응 — 기간 내 좋아요. 누적 카운터가 아니라 시각으로 센다 */
    @Query("SELECT COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.id = :postId AND pl.createdAt >= :from AND pl.createdAt < :to")
    long countByPostIdInPeriod(@Param("postId") Long postId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /** 파기 배치 — 게시물 물리 삭제 전에 자식부터 지운다 */
    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);
}

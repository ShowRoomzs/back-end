package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.Post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    /** 포스트 + 등록 상품 목록(상품 엔티티 포함) 한 번에 조회 (N+1 방지) */
    @Query("SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.postProducts pp " +
           "LEFT JOIN FETCH pp.product " +
           "WHERE p.id = :postId")
    Optional<Post> findByIdWithPostProductsAndProducts(@Param("postId") Long postId);

    Page<Post> findByCreatorId(Long creatorId, Pageable pageable);

    /** 팔로잉 피드 — 팔로우한 쇼룸(크리에이터)이 올린 게시물 */
    @Query("SELECT p FROM Post p WHERE p.isDisplay = true AND p.creator.id IN :creatorIds")
    Page<Post> findDisplayedPostsByFollowingCreatorIds(@Param("creatorIds") List<Long> creatorIds, Pageable pageable);

    /**
     * §22-4 인기 콘텐츠 TOP 5 — 기간 내 <b>게시된</b> 노출 게시물을 좋아요 많은 순으로.
     * 최신순은 제외한다 — 순위표의 목적이 성과 비교라 시간순은 의미가 없다.
     * 노출·좋아요는 게시물에 누적된 값이라 기간 내 증가분이 아니라는 점에 유의한다.
     */
    @Query("SELECT p FROM Post p " +
           "WHERE p.creator.id = :creatorId AND p.isDisplay = true " +
           "AND p.createdAt >= :from AND p.createdAt < :to " +
           "ORDER BY p.wishlistCount DESC, p.viewCount DESC, p.id DESC")
    List<Post> findTopContentsByLikes(@Param("creatorId") Long creatorId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);

    /** §22-4 인기 콘텐츠 TOP 5 — 노출 많은 순 정렬. */
    @Query("SELECT p FROM Post p " +
           "WHERE p.creator.id = :creatorId AND p.isDisplay = true " +
           "AND p.createdAt >= :from AND p.createdAt < :to " +
           "ORDER BY p.viewCount DESC, p.wishlistCount DESC, p.id DESC")
    List<Post> findTopContentsByViews(@Param("creatorId") Long creatorId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);

    /** 쇼룸별 최근 게시물 등록 시각 (팔로잉 목록 기본 정렬용) */
    @Query("SELECT p.creator.id, MAX(p.createdAt) FROM Post p " +
           "WHERE p.isDisplay = true AND p.creator.id IN :creatorIds " +
           "GROUP BY p.creator.id")
    List<Object[]> findLatestPostCreatedAtByCreatorIds(@Param("creatorIds") List<Long> creatorIds);
}

package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    /** 상세 조회 — 사진까지 한 번에(N+1 방지). 순서는 {@code @OrderBy}가 보장한다 */
    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.images WHERE p.id = :postId")
    Optional<Post> findByIdWithImages(@Param("postId") Long postId);

    /** 팔로잉 피드 — 팔로우한 쇼룸이 <b>게시한</b> 게시물만. 작성중이 새면 안 된다 */
    @Query("SELECT p FROM Post p WHERE p.status = showroomz.domain.post.type.PostStatus.PUBLISHED " +
           "AND p.creator.id IN :creatorIds")
    Page<Post> findDisplayedPostsByFollowingCreatorIds(@Param("creatorIds") List<Long> creatorIds, Pageable pageable);

    /**
     * §22-4 인기 콘텐츠 TOP 5 — 기간 내 <b>게시된</b> 게시물을 좋아요 많은 순으로.
     * 최신순은 제외한다 — 순위표의 목적이 성과 비교라 시간순은 의미가 없다.
     *
     * <p>기간 기준을 {@code createdAt}에서 {@code publishedAt}으로 바꿨다. 임시저장으로 오래 묵혀 둔
     * 게시물은 "언제 만들었나"가 아니라 "언제 세상에 나왔나"로 줄을 세워야 한다.
     * 노출·좋아요는 게시물에 누적된 값이라 기간 내 증가분이 아니라는 점에 유의한다.
     */
    @Query("SELECT p FROM Post p " +
           "WHERE p.creator.id = :creatorId AND p.status = showroomz.domain.post.type.PostStatus.PUBLISHED " +
           "AND p.publishedAt >= :from AND p.publishedAt < :to " +
           "ORDER BY p.likeCount DESC, p.impressionCount DESC, p.id DESC")
    List<Post> findTopContentsByLikes(@Param("creatorId") Long creatorId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);

    /** §22-4 인기 콘텐츠 TOP 5 — 노출 많은 순 정렬. */
    @Query("SELECT p FROM Post p " +
           "WHERE p.creator.id = :creatorId AND p.status = showroomz.domain.post.type.PostStatus.PUBLISHED " +
           "AND p.publishedAt >= :from AND p.publishedAt < :to " +
           "ORDER BY p.impressionCount DESC, p.likeCount DESC, p.id DESC")
    List<Post> findTopContentsByViews(@Param("creatorId") Long creatorId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);

    /**
     * 쇼룸별 최근 게시 시각 (팔로잉 목록 기본 정렬용).
     *
     * <p>시그니처는 그대로 두고 기준만 게시 시각으로 옮겼다 — 소비자가 "최근 게시물 순"으로 기대하는 것은
     * 작성 시각이 아니라 노출된 시각이다.
     */
    @Query("SELECT p.creator.id, MAX(p.publishedAt) FROM Post p " +
           "WHERE p.status = showroomz.domain.post.type.PostStatus.PUBLISHED AND p.creator.id IN :creatorIds " +
           "GROUP BY p.creator.id")
    List<Object[]> findLatestPostCreatedAtByCreatorIds(@Param("creatorIds") List<Long> creatorIds);

    /**
     * 스튜디오 탭 카운트 — {상태, 개수} 행 (§24-1 "개수가 함께 보여야 조치가 필요한 게시물을 바로 찾는다").
     *
     * <p>탭 4개를 각각 호출하면 요청이 4배가 되고 탭 전환마다 숫자가 흔들린다. 한 트랜잭션에서 뽑는다.
     */
    @Query("SELECT p.status, COUNT(p) FROM Post p " +
           "WHERE p.creator.id = :creatorId AND p.status <> showroomz.domain.post.type.PostStatus.DELETED " +
           "GROUP BY p.status")
    List<Object[]> countByCreatorGroupedByStatus(@Param("creatorId") Long creatorId);

    /** 파기 대상 — 보관 기간이 끝난 삭제 게시물 (§24-6) */
    @Query("SELECT p FROM Post p " +
           "WHERE p.status = showroomz.domain.post.type.PostStatus.DELETED " +
           "AND p.purgeAt IS NOT NULL AND p.purgeAt < :now ORDER BY p.purgeAt ASC")
    List<Post> findPurgeTargets(@Param("now") LocalDateTime now, Pageable pageable);

    Optional<Post> findByIdAndStatus(Long postId, PostStatus status);
}

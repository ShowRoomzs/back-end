package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostImpression;

import java.time.LocalDateTime;
import java.util.List;

public interface PostImpressionRepository extends JpaRepository<PostImpression, Long> {

    /** 30분 세션 판정 — 같은 사람이 같은 게시물을 다시 봐도 노출을 새로 세지 않는다 */
    boolean existsByPost_IdAndViewerKeyAndViewedAtAfter(Long postId, String viewerKey, LocalDateTime since);

    /** §24-7 ① 노출 — 기간 내 행 수 */
    @Query("SELECT COUNT(i) FROM PostImpression i " +
           "WHERE i.post.id = :postId AND i.viewedAt >= :from AND i.viewedAt < :to")
    long countByPostIdInPeriod(@Param("postId") Long postId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * §24-7 라스트 터치 귀속 — 어떤 사람이 24시간 안에 <b>마지막으로 본</b> 이 쇼룸의 게시물.
     *
     * <p>쇼룸을 특정하는 이유 — 방문·팔로우는 쇼룸 단위 행동이라, 다른 쇼룸의 게시물에 귀속시키면
     * 그 인플루언서의 인사이트에 남의 성과가 섞인다.
     */
    @Query("SELECT i.post.id FROM PostImpression i " +
           "WHERE i.viewerKey = :viewerKey AND i.creatorId = :creatorId AND i.viewedAt >= :since " +
           "ORDER BY i.viewedAt DESC")
    List<Long> findRecentlyViewedPostIds(@Param("viewerKey") String viewerKey,
                                         @Param("creatorId") Long creatorId,
                                         @Param("since") LocalDateTime since);

    /**
     * §24-7 ③ 본 사람 — {viewerKey, 성별, 생년월일} 행.
     *
     * <p>같은 사람이 여러 번 봤을 수 있으므로 {@code viewerKey}로 중복을 제거한 뒤 분포를 낸다.
     * 비로그인 노출은 {@code user}가 없어 성별·생년월일이 {@code null}로 나오고 "미확인"이 된다.
     * 개인 식별자는 집계 밖으로 나가지 않는다(§22-4와 같은 규칙).
     */
    @Query("SELECT DISTINCT i.viewerKey, u.gender, u.birthday FROM PostImpression i " +
           "LEFT JOIN i.user u " +
           "WHERE i.post.id = :postId AND i.viewedAt >= :from AND i.viewedAt < :to")
    List<Object[]> findViewerDemographics(@Param("postId") Long postId,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /** 파기 배치 — 게시물 물리 삭제 전에 자식부터 지운다 */
    @Modifying
    @Query("DELETE FROM PostImpression i WHERE i.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    /** 보관 기간 경과분 정리 — 기간이 확정되기 전에는 호출되지 않는다 */
    @Modifying
    @Query("DELETE FROM PostImpression i WHERE i.viewedAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

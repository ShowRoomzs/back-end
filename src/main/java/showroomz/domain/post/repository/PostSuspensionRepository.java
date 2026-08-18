package showroomz.domain.post.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostSuspension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostSuspensionRepository extends JpaRepository<PostSuspension, Long> {

    /** 현재 진행 중인 조치 — {@code resolution IS NULL}인 가장 최근 행이다 */
    Optional<PostSuspension> findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(Long postId);

    /**
     * 목록 화면용 — 여러 게시물의 진행 중인 조치를 한 번에 읽는다.
     *
     * <p>조치 시각 <b>오름차순</b>이라 게시물별로 마지막 행이 가장 최근 조치다 —
     * {@link #findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(Long)}가 고르는 것과 같은 건이다.
     */
    @Query("SELECT s FROM PostSuspension s " +
           "WHERE s.post.id IN :postIds AND s.resolution IS NULL " +
           "ORDER BY s.suspendedAt ASC")
    List<PostSuspension> findOpenByPostIds(@Param("postIds") List<Long> postIds);

    /** 조치 이력 — 재게시 후 재조치가 가능하므로 여러 건이 쌓인다 */
    List<PostSuspension> findByPost_IdOrderBySuspendedAtDesc(Long postId);

    /**
     * 기한 만료 배치 대상 — 진행 중인 조치 중 기한이 지났고 <b>이의 신청이 없는</b> 건.
     *
     * <p>신청이 들어온 건은 기한이 지나도 자동 삭제하지 않는다. 심사 결과가 나기 전에 게시물이
     * 사라지면 결과가 붕 뜬다(§24-5의 심사 중 삭제 금지와 같은 이유다).
     */
    @Query("SELECT s FROM PostSuspension s " +
           "WHERE s.resolution IS NULL AND s.appealDeadline < :now " +
           "AND NOT EXISTS (SELECT a FROM PostAppeal a WHERE a.suspension = s) " +
           "ORDER BY s.appealDeadline ASC")
    List<PostSuspension> findExpiredWithoutAppeal(@Param("now") LocalDateTime now, Pageable pageable);

    /** 파기 배치 — 게시물 물리 삭제 전에 자식부터 지운다 */
    @Modifying
    @Query("DELETE FROM PostSuspension s WHERE s.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);
}

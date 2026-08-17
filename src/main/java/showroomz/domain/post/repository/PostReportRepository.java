package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.type.PostReportStatus;

import java.util.List;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    /** 사람당 게시물당 1회 — 유니크가 최종 방어선이고 이쪽은 400 대신 409를 내주기 위한 선검사다 */
    boolean existsByPost_IdAndReporter_Id(Long postId, Long userId);

    /** 노출 중지 시 한꺼번에 닫을 대상 */
    List<PostReport> findByPost_IdAndStatus(Long postId, PostReportStatus status);

    /**
     * 운영자 대기열 — 오래 기다린 순이다.
     *
     * <p>최신순이 아닌 이유는 이의 신청 목록(§24-5)과 같다. 신고는 방치되면 위반 게시물이 계속
     * 노출된다는 뜻이라, 순서가 곧 형평이다.
     */
    Page<PostReport> findByStatusOrderByReportedAtAsc(PostReportStatus status, Pageable pageable);

    Page<PostReport> findAllByOrderByReportedAtDesc(Pageable pageable);

    Page<PostReport> findByPost_IdOrderByReportedAtDesc(Long postId, Pageable pageable);

    Page<PostReport> findByPost_IdAndStatusOrderByReportedAtDesc(Long postId, PostReportStatus status,
                                                                Pageable pageable);

    /** 어드민 목록의 "신고 N건" — 게시물별 대기 건수를 한 번에 읽는다 */
    @Query("SELECT r.post.id, COUNT(r) FROM PostReport r " +
           "WHERE r.post.id IN :postIds AND r.status = :status GROUP BY r.post.id")
    List<Object[]> countByPostIdsAndStatus(@Param("postIds") List<Long> postIds,
                                           @Param("status") PostReportStatus status);

    long countByPost_IdAndStatus(Long postId, PostReportStatus status);

    /** 파기 배치 — 게시물 물리 삭제 전에 자식부터 지운다 */
    @Modifying
    @Query("DELETE FROM PostReport r WHERE r.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);
}
